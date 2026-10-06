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
