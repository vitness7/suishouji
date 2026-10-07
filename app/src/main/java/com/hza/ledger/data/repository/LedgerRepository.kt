package com.hza.ledger.data.repository

import com.hza.ledger.data.local.AccountEntity
import com.hza.ledger.data.local.AccountBalanceItem
import com.hza.ledger.data.local.BudgetEntity
import com.hza.ledger.data.local.CategoryEntity
import com.hza.ledger.data.local.SavingsPlanEntity
import com.hza.ledger.data.local.TransactionListItem
import com.hza.ledger.domain.model.NewTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LedgerRepository {
    val activeBookId: StateFlow<String>
    val accounts: Flow<List<AccountEntity>>
    val accountBalances: Flow<List<AccountBalanceItem>>
    val transactions: Flow<List<TransactionListItem>>
    val budgets: Flow<List<BudgetEntity>>
    val savingsPlans: Flow<List<SavingsPlanEntity>>

    fun categories(type: String): Flow<List<CategoryEntity>>

    suspend fun ensureSeedData()
    suspend fun selectBook(bookId: String)
    suspend fun addTransaction(transaction: NewTransaction): String
    suspend fun updateTransaction(id: String, transaction: NewTransaction)
    suspend fun deleteTransaction(id: String)
    suspend fun restoreTransaction(id: String)
    suspend fun undoTransaction(id: String)
    suspend fun saveBudget(
        periodStart: Long,
        periodEnd: Long,
        limitMinor: Long,
        categoryId: String? = null,
    )

    // ---------- 账户管理 ----------

    /**
     * 新增或更新账户。
     *
     * @param id 传 null 表示新建
     */
    suspend fun saveAccount(
        id: String?,
        name: String,
        subtitle: String,
        type: String,
        openingBalanceMinor: Long,
        includeInNetWorth: Boolean,
    ): String

    /**
     * 删除账户。
     *
     * @return 若账户仍被流水引用则拒绝删除并返回失败，避免历史账目出现悬空引用
     */
    suspend fun deleteAccount(id: String): Result<Unit>

    suspend fun accountUsageCount(id: String): Long

    // ---------- 存钱计划 ----------

    suspend fun saveSavingsPlan(
        id: String?,
        name: String,
        method: String,
        targetMinor: Long,
        colorHex: Long,
        startAt: Long,
        endAt: Long?,
    ): String

    /** 存入一笔：更新已存金额并写入对应流水，保证两边一致。 */
    suspend fun depositToSavingsPlan(
        planId: String,
        amountMinor: Long,
        accountId: String,
        occurredAt: Long,
        note: String,
    )

    suspend fun deleteSavingsPlan(id: String)
}
