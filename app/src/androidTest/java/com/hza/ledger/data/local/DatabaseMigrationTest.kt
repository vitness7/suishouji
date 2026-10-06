package com.hza.ledger.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OneLedgerDatabase::class.java,
    )

    @Test
    fun migrate1To2PreservesDataAndAddsConstraints() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO ledger_books
                    (id, name, currencyCode, isDefault, createdAt, updatedAt)
                VALUES ('book-test', '迁移账本', 'CNY', 1, 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO accounts
                    (id, bookId, name, subtitle, type, colorHex, openingBalanceMinor,
                     includeInNetWorth, sortOrder, createdAt, updatedAt, deletedAt)
                VALUES ('account-test', 'book-test', '现金', '', 'CASH', 1, 10000, 1, 0, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO categories
                    (id, bookId, name, transactionType, iconKey, colorHex, parentId,
                     sortOrder, isSystem, createdAt, updatedAt, deletedAt)
                VALUES ('category-test', 'book-test', '餐饮', 'EXPENSE', 'food', 1, NULL, 0, 1, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO transactions
                    (id, bookId, type, amountMinor, categoryId, accountId, toAccountId,
                     merchant, note, occurredAt, relatedTransactionId, reimbursementState,
                     createdAt, updatedAt, deletedAt)
                VALUES
                    ('transaction-test', 'book-test', 'EXPENSE', 1250, 'category-test',
                     'account-test', NULL, '', '午餐', 1, NULL, 'NONE', 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO budgets
                    (id, bookId, categoryId, periodStart, periodEnd, limitMinor, createdAt, updatedAt)
                VALUES ('budget-test', 'book-test', NULL, 1, 100, 50000, 1, 1)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DATABASE, 2, true, MIGRATION_1_2).apply {
            query("SELECT amountMinor, categoryId FROM transactions WHERE id = 'transaction-test'").use { cursor ->
                assertFalse(cursor.isClosed)
                cursor.moveToFirst()
                assertEquals(1250L, cursor.getLong(0))
                assertEquals("category-test", cursor.getString(1))
            }
            query("SELECT scopeKey, limitMinor FROM budgets WHERE id = 'budget-test'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(TOTAL_BUDGET_SCOPE, cursor.getString(0))
                assertEquals(50000L, cursor.getLong(1))
            }
            query("PRAGMA foreign_key_check").use { cursor ->
                assertEquals(0, cursor.count)
            }
            close()
        }
    }

    @Test
    fun migrate2To3ClearsOnlyUntouchedDemoBudget() {
        helper.createDatabase(TEST_DATABASE, 2).apply {
            execSQL(
                """
                INSERT INTO ledger_books
                    (id, name, currencyCode, isDefault, createdAt, updatedAt)
                VALUES ('book-default', 'Default', 'CNY', 1, 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO budgets
                    (id, bookId, categoryId, scopeKey, periodStart, periodEnd,
                     limitMinor, createdAt, updatedAt)
                VALUES
                    ('budget-untouched', 'book-default', NULL, '$TOTAL_BUDGET_SCOPE',
                     1, 100, 500000, 10, 10),
                    ('budget-user-edited', 'book-default', NULL, '$TOTAL_BUDGET_SCOPE',
                     101, 200, 500000, 10, 20)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DATABASE, 3, true, MIGRATION_2_3).apply {
            query("SELECT id, limitMinor FROM budgets ORDER BY id").use { cursor ->
                cursor.moveToFirst()
                assertEquals("budget-untouched", cursor.getString(0))
                assertEquals(0L, cursor.getLong(1))
                cursor.moveToNext()
                assertEquals("budget-user-edited", cursor.getString(0))
                assertEquals(500000L, cursor.getLong(1))
            }
            close()
        }
    }

    /**
     * 3 -> 4：新增自动识别候选表。
     *
     * 这是纯新增迁移，必须验证两点：
     * 1. 既有账本、流水、预算数据完全不受影响
     * 2. 新表可正常写入与读出
     */
    @Test
    fun migrate3To4AddsPendingTableAndKeepsExistingData() {
        helper.createDatabase(TEST_DATABASE, 3).apply {
            execSQL(
                """
                INSERT INTO ledger_books
                    (id, name, currencyCode, isDefault, createdAt, updatedAt)
                VALUES ('book-keep', '保留账本', 'CNY', 1, 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO accounts
                    (id, bookId, name, subtitle, type, colorHex, openingBalanceMinor,
                     includeInNetWorth, sortOrder, createdAt, updatedAt, deletedAt)
                VALUES ('account-keep', 'book-keep', '现金', '', 'CASH', 1, 10000, 1, 0, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO transactions
                    (id, bookId, type, amountMinor, categoryId, accountId, toAccountId, merchant,
                     note, occurredAt, relatedTransactionId, reimbursementState,
                     createdAt, updatedAt, deletedAt)
                VALUES ('txn-keep', 'book-keep', 'EXPENSE', 3580, NULL, 'account-keep', NULL,
                        '星巴克', '', 100, NULL, 'NONE', 100, 100, NULL)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DATABASE, 4, true, MIGRATION_3_4).apply {
            // 既有流水必须完好
            query("SELECT id, amountMinor, merchant FROM transactions").use { cursor ->
                cursor.moveToFirst()
                assertEquals("txn-keep", cursor.getString(0))
                assertEquals(3580L, cursor.getLong(1))
                assertEquals("星巴克", cursor.getString(2))
                assertFalse("不应产生额外流水", cursor.moveToNext())
            }

            // 既有账户必须完好
            query("SELECT COUNT(*) FROM accounts").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }

            // 新表可正常读写
            execSQL(
                """
                INSERT INTO pending_transactions
                    (id, bookId, type, amountMinor, merchant, note, channel,
                     suggestedCategoryKey, confidence, sourceKey, rawText, occurredAt, createdAt)
                VALUES ('pending-1', 'book-keep', 'EXPENSE', 26800, '海底捞', '', 'ALIPAY',
                        'expense-food', 0.85, 'notif:test:1', '支付宝 付款成功 海底捞 ￥268.00', 200, 200)
                """.trimIndent(),
            )
            query("SELECT id, amountMinor, confidence, sourceKey FROM pending_transactions").use { cursor ->
                cursor.moveToFirst()
                assertEquals("pending-1", cursor.getString(0))
                assertEquals(26800L, cursor.getLong(1))
                assertEquals(0.85f, cursor.getFloat(2), 0.0001f)
                assertEquals("notif:test:1", cursor.getString(3))
            }
            close()
        }
    }

    /** sourceKey 唯一约束必须生效，防止同一通知重复堆积候选。 */
    @Test
    fun pendingSourceKeyIsUnique() {
        helper.createDatabase(TEST_DATABASE, 4).apply {
            execSQL(
                """
                INSERT INTO ledger_books
                    (id, name, currencyCode, isDefault, createdAt, updatedAt)
                VALUES ('book-uq', '唯一约束', 'CNY', 1, 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO pending_transactions
                    (id, bookId, type, amountMinor, merchant, note, channel,
                     suggestedCategoryKey, confidence, sourceKey, rawText, occurredAt, createdAt)
                VALUES ('pending-uq-1', 'book-uq', 'EXPENSE', 100, '', '', 'WECHAT',
                        NULL, 0.6, 'same-key', '', 1, 1)
                """.trimIndent(),
            )
            // 相同 sourceKey 应被唯一索引拒绝
            val inserted = try {
                execSQL(
                    """
                    INSERT INTO pending_transactions
                        (id, bookId, type, amountMinor, merchant, note, channel,
                         suggestedCategoryKey, confidence, sourceKey, rawText, occurredAt, createdAt)
                    VALUES ('pending-uq-2', 'book-uq', 'EXPENSE', 200, '', '', 'WECHAT',
                            NULL, 0.6, 'same-key', '', 2, 2)
                    """.trimIndent(),
                )
                1
            } catch (_: Exception) {
                0
            }
            assertEquals("相同来源键不应重复插入", 0, inserted)
            close()
        }
    }

    private companion object {
        const val TEST_DATABASE = "migration-test"
    }
}
