package com.hza.ledger.data.repository

import com.hza.ledger.data.local.AccountEntity
import com.hza.ledger.data.local.BudgetEntity
import com.hza.ledger.data.local.CategoryEntity
import com.hza.ledger.data.local.LedgerBookEntity
import com.hza.ledger.data.local.LedgerDao
import com.hza.ledger.data.local.SavingsPlanEntity
import com.hza.ledger.data.local.TOTAL_BUDGET_SCOPE
import com.hza.ledger.data.local.TransactionEntity
import com.hza.ledger.domain.model.AccountType
import com.hza.ledger.domain.model.NewTransaction
import com.hza.ledger.domain.model.SavingsMethod
import com.hza.ledger.domain.model.TransactionType
import com.hza.ledger.util.MonthWindow
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineLedgerRepository(
    private val dao: LedgerDao,
) : LedgerRepository {
    private val mutableActiveBookId = MutableStateFlow(DEFAULT_BOOK_ID)
    override val activeBookId = mutableActiveBookId.asStateFlow()

    override val accounts = activeBookId.flatMapLatest { dao.observeAccounts(it) }
    override val accountBalances = activeBookId.flatMapLatest { dao.observeAccountBalances(it) }
    override val transactions = activeBookId.flatMapLatest { dao.observeTransactions(it) }
    override val budgets = activeBookId.flatMapLatest { dao.observeBudgets(it) }
    override val savingsPlans = activeBookId.flatMapLatest { dao.observeSavingsPlans(it) }

    override fun categories(type: String) =
        activeBookId.flatMapLatest { bookId -> dao.observeCategories(bookId, type) }

    override suspend fun ensureSeedData() {
        val now = System.currentTimeMillis()
        val existingBookId = dao.getDefaultBookId() ?: dao.getFirstBookId()
        if (existingBookId != null) {
            dao.insertCategoriesIfAbsent(
                defaultExpenseCategories(existingBookId, now) +
                    defaultIncomeCategories(existingBookId, now),
            )
            mutableActiveBookId.value = existingBookId
            return
        }

        val month = MonthWindow.current(now)
        val yearLater = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.YEAR, 1)
        }.timeInMillis

        val book = LedgerBookEntity(
            id = DEFAULT_BOOK_ID,
            name = "日常账本",
            currencyCode = "CNY",
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )

        val accounts = listOf(
            AccountEntity(
                id = "account-cash",
                bookId = DEFAULT_BOOK_ID,
                name = "现金",
                subtitle = "现金钱包",
                type = AccountType.CASH,
                colorHex = 0xFFFFC247,
                openingBalanceMinor = 0,
                includeInNetWorth = true,
                sortOrder = 0,
                createdAt = now,
                updatedAt = now,
            ),
            AccountEntity(
                id = "account-daily",
                bookId = DEFAULT_BOOK_ID,
                name = "日常卡",
                subtitle = "储蓄账户",
                type = AccountType.BANK,
                colorHex = 0xFF4D7CFE,
                openingBalanceMinor = 0,
                includeInNetWorth = true,
                sortOrder = 1,
                createdAt = now,
                updatedAt = now,
            ),
        )

        dao.seed(
            book = book,
            accounts = accounts,
            categories = defaultExpenseCategories(DEFAULT_BOOK_ID, now) +
                defaultIncomeCategories(DEFAULT_BOOK_ID, now),
            budget = BudgetEntity(
                id = "budget-${month.start}",
                bookId = DEFAULT_BOOK_ID,
                periodStart = month.start,
                periodEnd = month.endExclusive,
                limitMinor = 0,
                createdAt = now,
                updatedAt = now,
            ),
            savingsPlan = SavingsPlanEntity(
                id = "savings-first-fund",
                bookId = DEFAULT_BOOK_ID,
                name = "第一桶金",
                method = SavingsMethod.FLEXIBLE,
                targetMinor = 1_000_000,
                savedMinor = 0,
                colorHex = 0xFF57D3A2,
                startAt = now,
                endAt = yearLater,
                isArchived = false,
                createdAt = now,
                updatedAt = now,
            ),
        )
        mutableActiveBookId.value = DEFAULT_BOOK_ID
    }

    override suspend fun selectBook(bookId: String) {
        require(dao.bookExists(bookId)) { "账本不存在" }
        mutableActiveBookId.value = bookId
    }

    override suspend fun addTransaction(transaction: NewTransaction): String {
        transaction.requireValid()
        val bookId = activeBookId.value
        validateReferences(bookId, transaction)
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.insertTransaction(
            TransactionEntity(
                id = id,
                bookId = bookId,
                type = transaction.type,
                amountMinor = transaction.amountMinor,
                categoryId = transaction.categoryId,
                accountId = transaction.accountId,
                toAccountId = transaction.toAccountId,
                note = transaction.note.trim(),
                occurredAt = transaction.occurredAt,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id
    }

    override suspend fun updateTransaction(id: String, transaction: NewTransaction) {
        val bookId = activeBookId.value
        val existing = checkNotNull(dao.getTransaction(bookId, id)) { "找不到要更新的账单" }
        validateReferences(bookId, transaction)
        dao.updateTransaction(existing.withUpdate(transaction, System.currentTimeMillis()))
    }

    override suspend fun deleteTransaction(id: String) {
        val bookId = activeBookId.value
        checkNotNull(dao.getTransaction(bookId, id)) { "找不到要删除的账单" }
        dao.softDeleteTransaction(bookId, id, System.currentTimeMillis())
    }

    override suspend fun restoreTransaction(id: String) {
        val bookId = activeBookId.value
        checkNotNull(dao.getTransaction(bookId, id)) { "找不到要恢复的账单" }
        dao.restoreTransaction(bookId, id, System.currentTimeMillis())
    }

    override suspend fun undoTransaction(id: String) {
        deleteTransaction(id)
    }

    override suspend fun saveBudget(
        periodStart: Long,
        periodEnd: Long,
        limitMinor: Long,
        categoryId: String?,
    ) {
        require(periodEnd > periodStart) { "预算周期必须有效" }
        require(limitMinor >= 0) { "预算金额不能为负数" }
        val bookId = activeBookId.value
        if (categoryId != null) {
            require(dao.categoryExists(bookId, categoryId, TransactionType.EXPENSE)) { "预算分类不属于当前账本" }
        }
        val scopeKey = categoryId ?: TOTAL_BUDGET_SCOPE
        val existing = dao.getBudget(bookId, scopeKey, periodStart, periodEnd)
        val now = System.currentTimeMillis()
        dao.upsertBudget(
            BudgetEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                bookId = bookId,
                categoryId = categoryId,
                scopeKey = scopeKey,
                periodStart = periodStart,
                periodEnd = periodEnd,
                limitMinor = limitMinor,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun saveAccount(
        id: String?,
        name: String,
        subtitle: String,
        type: String,
        openingBalanceMinor: Long,
        includeInNetWorth: Boolean,
    ): String {
        val bookId = activeBookId.value
        val now = System.currentTimeMillis()
        val accountId = id ?: "account-${UUID.randomUUID()}"

        // 排序号取同类已有最大值 +1，保证新建的账户排在列表末尾
        val nextSortOrder = dao.getAccounts(bookId)
            .filter { it.type == type && (id == null || it.id != id) }
            .maxOfOrNull { it.sortOrder + 1 }
            ?: 0

        dao.upsertAccounts(
            listOf(
                AccountEntity(
                    id = accountId,
                    bookId = bookId,
                    name = name,
                    subtitle = subtitle,
                    type = type,
                    colorHex = accountColor(type),
                    openingBalanceMinor = openingBalanceMinor,
                    includeInNetWorth = includeInNetWorth,
                    sortOrder = nextSortOrder,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                ),
            ),
        )
        return accountId
    }

    override suspend fun deleteAccount(id: String): Result<Unit> = runCatching {
        val bookId = activeBookId.value
        // 仍有流水引用该账户时不允许删除，否则历史账目会指向不存在的账户
        val usage = dao.countTransactionsUsingAccount(bookId, id)
        require(usage == 0L) { "该账户下还有 $usage 笔记录，请先处理相关账单" }
        dao.softDeleteAccount(bookId, id, System.currentTimeMillis())
    }

    override suspend fun accountUsageCount(id: String): Long =
        dao.countTransactionsUsingAccount(activeBookId.value, id)

    override suspend fun saveSavingsPlan(
        id: String?,
        name: String,
        method: String,
        targetMinor: Long,
        colorHex: Long,
        startAt: Long,
        endAt: Long?,
    ): String {
        val bookId = activeBookId.value
        val now = System.currentTimeMillis()
        val planId = id ?: "saving-${UUID.randomUUID()}"

        val existing = dao.getSavingsPlans(bookId).firstOrNull { it.id == planId }
        dao.upsertSavingsPlan(
            SavingsPlanEntity(
                id = planId,
                bookId = bookId,
                name = name,
                method = method,
                targetMinor = targetMinor,
                savedMinor = existing?.savedMinor ?: 0L,
                colorHex = colorHex,
                startAt = startAt,
                endAt = endAt,
                isArchived = false,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        return planId
    }

    /**
     * 存入一笔。
     *
     * 关键：更新进度与写入支出流水必须同时成功或同时失败，
     * 否则会出现「进度涨了但账上没这笔钱」或反之的错账。
     */
    override suspend fun depositToSavingsPlan(
        planId: String,
        amountMinor: Long,
        accountId: String,
        occurredAt: Long,
        note: String,
    ) {
        val bookId = activeBookId.value
        val now = System.currentTimeMillis()

        val plan = dao.getSavingsPlans(bookId).firstOrNull { it.id == planId }
            ?: error("存钱目标不存在")

        // 存入意味着这笔钱从某个账户离开，按支出记账才能让账户余额与实际一致
        val categoryId = dao.getCategories(bookId, TransactionType.EXPENSE)
            .firstOrNull { it.id == "expense-savings" }
            ?.id
            ?: dao.getCategories(bookId, TransactionType.EXPENSE).firstOrNull()?.id

        dao.runInTransaction {
            dao.upsertSavingsPlan(
                plan.copy(
                    savedMinor = plan.savedMinor + amountMinor,
                    updatedAt = now,
                ),
            )
            dao.insertTransaction(
                TransactionEntity(
                    id = "txn-${UUID.randomUUID()}",
                    bookId = bookId,
                    type = TransactionType.EXPENSE,
                    amountMinor = amountMinor,
                    categoryId = categoryId,
                    accountId = accountId,
                    toAccountId = null,
                    merchant = plan.name,
                    note = note.ifBlank { "存入「${plan.name}」" },
                    occurredAt = occurredAt,
                    relatedTransactionId = planId,
                    reimbursementState = "NONE",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    override suspend fun deleteSavingsPlan(id: String) {
        dao.deleteSavingsPlan(activeBookId.value, id)
    }

    /** 各账户类型的默认配色，与分类体系保持一致的视觉语言。 */
    private fun accountColor(type: String): Long = when (type) {
        AccountType.CASH -> 0xFFFFC247L
        AccountType.BANK -> 0xFF4D7CFEL
        AccountType.CREDIT -> 0xFFFF6B5FL
        AccountType.DIGITAL -> 0xFF57D3A2L
        AccountType.INVESTMENT -> 0xFF9B7BFFL
        else -> 0xFF8992A3L
    }

    private suspend fun validateReferences(bookId: String, transaction: NewTransaction) {
        require(dao.accountExists(bookId, transaction.accountId)) { "账户不属于当前账本" }
        if (transaction.type == TransactionType.TRANSFER) {
            val toAccountId = checkNotNull(transaction.toAccountId)
            require(dao.accountExists(bookId, toAccountId)) { "转入账户不属于当前账本" }
        } else {
            val categoryId = checkNotNull(transaction.categoryId)
            require(dao.categoryExists(bookId, categoryId, transaction.type)) { "分类不属于当前账本" }
        }
    }

    companion object {
        const val DEFAULT_BOOK_ID = "book-default"
    }
}
