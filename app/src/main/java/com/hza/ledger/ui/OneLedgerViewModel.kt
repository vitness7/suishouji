package com.hza.ledger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hza.ledger.data.local.AccountEntity
import com.hza.ledger.data.local.AccountBalanceItem
import com.hza.ledger.data.local.BudgetEntity
import com.hza.ledger.data.local.CategoryEntity
import com.hza.ledger.data.local.PendingTransactionEntity
import com.hza.ledger.data.local.SavingsPlanEntity
import com.hza.ledger.data.local.TransactionListItem
import com.hza.ledger.data.repository.AutoDetectRepository
import com.hza.ledger.data.repository.LedgerRepository
import com.hza.ledger.domain.model.NewTransaction
import com.hza.ledger.domain.model.TransactionType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OneLedgerUiState(
    val activeBookId: String = "",
    val accounts: List<AccountEntity> = emptyList(),
    val accountBalances: List<AccountBalanceItem> = emptyList(),
    val transactions: List<TransactionListItem> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val savingsPlans: List<SavingsPlanEntity> = emptyList(),
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val incomeCategories: List<CategoryEntity> = emptyList(),
    val pendingTransactions: List<PendingTransactionEntity> = emptyList(),
)

private data class LedgerData(
    val accounts: List<AccountEntity>,
    val accountBalances: List<AccountBalanceItem>,
    val transactions: List<TransactionListItem>,
    val budgets: List<BudgetEntity>,
)

private data class PlanningData(
    val plans: List<SavingsPlanEntity>,
    val expenseCategories: List<CategoryEntity>,
    val incomeCategories: List<CategoryEntity>,
)

class OneLedgerViewModel(
    private val repository: LedgerRepository,
    private val autoDetectRepository: AutoDetectRepository,
) : ViewModel() {
    private val ledgerData = combine(
        repository.accounts,
        repository.accountBalances,
        repository.transactions,
        repository.budgets,
    ) { accounts, accountBalances, transactions, budgets ->
        LedgerData(accounts, accountBalances, transactions, budgets)
    }

    private val planningData = combine(
        repository.savingsPlans,
        repository.categories(TransactionType.EXPENSE),
        repository.categories(TransactionType.INCOME),
    ) { plans, expenseCategories, incomeCategories ->
        PlanningData(plans, expenseCategories, incomeCategories)
    }

    val uiState = combine(
        ledgerData,
        planningData,
        repository.activeBookId,
        autoDetectRepository.pendingTransactions,
    ) { ledger, planning, activeBookId, pending ->
        OneLedgerUiState(
            activeBookId = activeBookId,
            accounts = ledger.accounts,
            accountBalances = ledger.accountBalances,
            transactions = ledger.transactions,
            budgets = ledger.budgets,
            savingsPlans = planning.plans,
            expenseCategories = planning.expenseCategories,
            incomeCategories = planning.incomeCategories,
            pendingTransactions = pending,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OneLedgerUiState(),
    )

    // ---------- 自动识别候选操作 ----------

    /**
     * 确认一笔候选并写入正式账本。
     *
     * 账户未指定时，自动选择与识别渠道匹配度最高的账户；
     * 分类未指定时使用解析器建议的分类。
     */
    fun confirmPending(
        pending: PendingTransactionEntity,
        categoryId: String? = null,
        accountId: String? = null,
    ) {
        viewModelScope.launch {
            val bookId = repository.activeBookId.value
            if (bookId.isBlank()) return@launch

            val resolvedCategory = categoryId
                ?: pending.suggestedCategoryKey?.takeIf { key ->
                    // 建议分类必须真实存在于当前账本，否则退回未分类
                    (if (pending.type == TransactionType.INCOME) {
                        uiState.value.incomeCategories
                    } else {
                        uiState.value.expenseCategories
                    }).any { it.id == key }
                }

            val resolvedAccount = accountId
                ?: pickBestAccount(pending.channel, pending.type)
                ?: return@launch

            autoDetectRepository.confirm(
                bookId = bookId,
                pendingId = pending.id,
                categoryId = resolvedCategory,
                accountId = resolvedAccount,
                type = pending.type,
                amountMinor = pending.amountMinor,
                merchant = pending.merchant,
                note = "",
                occurredAt = pending.occurredAt,
            )
        }
    }

    /** 按渠道匹配度选账户：数字渠道→支付宝/微信类，银行→储蓄/信用卡。 */
    private fun pickBestAccount(channel: String, type: String): String? {
        val accounts = uiState.value.accounts
        if (accounts.isEmpty()) return null
        val preferredType = when (channel) {
            "WECHAT", "ALIPAY" -> "DIGITAL"
            "BANK_CARD" -> "BANK"
            "CREDIT_CARD" -> "CREDIT"
            else -> null
        }
        return accounts.firstOrNull { it.type == preferredType }?.id
            ?: accounts.firstOrNull { it.type != "CREDIT" }?.id
            ?: accounts.first().id
    }

    fun discardPending(pending: PendingTransactionEntity) {
        viewModelScope.launch {
            val bookId = repository.activeBookId.value
            if (bookId.isBlank()) return@launch
            autoDetectRepository.discard(bookId, pending.id)
        }
    }

    fun discardAllPending() {
        viewModelScope.launch {
            val bookId = repository.activeBookId.value
            if (bookId.isBlank()) return@launch
            autoDetectRepository.discardAll(bookId, uiState.value.pendingTransactions.map { it.id })
        }
    }

    /** 供设置页演示与自测用。 */
    fun ingestSampleForTesting(text: String) {
        viewModelScope.launch {
            val bookId = repository.activeBookId.value
            if (bookId.isBlank()) return@launch
            autoDetectRepository.ingest(
                bookId = bookId,
                text = text,
                sourceKey = "manual:${System.nanoTime()}",
            )
        }
    }

    init {
        viewModelScope.launch { repository.ensureSeedData() }
    }

    fun addTransaction(transaction: NewTransaction, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            onSaved(repository.addTransaction(transaction))
        }
    }

    fun selectBook(bookId: String, onComplete: (Result<Unit>) -> Unit = {}) = runMutation(onComplete) {
        repository.selectBook(bookId)
    }

    fun undoTransaction(id: String) {
        viewModelScope.launch { repository.undoTransaction(id) }
    }

    fun updateTransaction(
        id: String,
        transaction: NewTransaction,
        onComplete: (Result<Unit>) -> Unit,
    ) = runMutation(onComplete) {
        repository.updateTransaction(id, transaction)
    }

    fun deleteTransaction(id: String, onComplete: (Result<Unit>) -> Unit) = runMutation(onComplete) {
        repository.deleteTransaction(id)
    }

    fun restoreTransaction(id: String, onComplete: (Result<Unit>) -> Unit = {}) = runMutation(onComplete) {
        repository.restoreTransaction(id)
    }

    fun saveBudget(
        periodStart: Long,
        periodEnd: Long,
        limitMinor: Long,
        categoryId: String? = null,
        onSaved: () -> Unit = {},
    ) {
        viewModelScope.launch {
            repository.saveBudget(
                periodStart = periodStart,
                periodEnd = periodEnd,
                limitMinor = limitMinor,
                categoryId = categoryId,
            )
            onSaved()
        }
    }

    // ---------- 账户管理 ----------

    fun saveAccount(
        id: String?,
        name: String,
        subtitle: String,
        type: String,
        openingBalanceMinor: Long,
        includeInNetWorth: Boolean,
        onDone: (String?) -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                repository.saveAccount(
                    id = id,
                    name = name,
                    subtitle = subtitle,
                    type = type,
                    openingBalanceMinor = openingBalanceMinor,
                    includeInNetWorth = includeInNetWorth,
                )
            }.onSuccess { onDone(it) }
                .onFailure { onDone(null) }
        }
    }

    fun deleteAccount(id: String, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            onResult(repository.deleteAccount(id))
        }
    }

    fun accountUsageCount(id: String, onResult: (Long) -> Unit = {}) {
        viewModelScope.launch {
            onResult(repository.accountUsageCount(id))
        }
    }

    // ---------- 存钱计划 ----------

    fun saveSavingsPlan(
        id: String?,
        name: String,
        method: String,
        targetMinor: Long,
        colorHex: Long,
        startAt: Long,
        endAt: Long?,
        onDone: (String?) -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                repository.saveSavingsPlan(
                    id = id,
                    name = name,
                    method = method,
                    targetMinor = targetMinor,
                    colorHex = colorHex,
                    startAt = startAt,
                    endAt = endAt,
                )
            }.onSuccess { onDone(it) }
                .onFailure { onDone(null) }
        }
    }

    fun depositToSavingsPlan(
        planId: String,
        amountMinor: Long,
        accountId: String,
        note: String,
        onDone: (Result<Unit>) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = runCatching {
                repository.depositToSavingsPlan(
                    planId = planId,
                    amountMinor = amountMinor,
                    accountId = accountId,
                    occurredAt = System.currentTimeMillis(),
                    note = note,
                )
            }
            onDone(result)
        }
    }

    fun deleteSavingsPlan(id: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteSavingsPlan(id)
            onDone()
        }
    }

    private fun runMutation(
        onComplete: (Result<Unit>) -> Unit,
        mutation: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                mutation()
                onComplete(Result.success(Unit))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                onComplete(Result.failure(error))
            }
        }
    }
}

class OneLedgerViewModelFactory(
    private val repository: LedgerRepository,
    private val autoDetectRepository: AutoDetectRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(OneLedgerViewModel::class.java))
        return OneLedgerViewModel(repository, autoDetectRepository) as T
    }
}
