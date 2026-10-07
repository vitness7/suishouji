package com.hza.ledger.domain.model

object TransactionType {
    const val EXPENSE = "EXPENSE"
    const val INCOME = "INCOME"
    const val TRANSFER = "TRANSFER"
}

object AccountType {
    const val CASH = "CASH"
    const val BANK = "BANK"
    const val CREDIT = "CREDIT"
    const val DIGITAL = "DIGITAL"
    const val INVESTMENT = "INVESTMENT"

    /**
     * 工资卡账户。
     *
     * 单独一类的意义：工资到账后要能自动归入这个账户，
     * 资产页因此能单独显示「工资卡余额」，而不是混在普通银行卡里看不清。
     * 多数银行把工资和日常消费放同一张卡，单独记能让收支更清晰。
     */
    const val SALARY = "SALARY"
}

object SavingsMethod {
    const val FIXED = "FIXED"
    const val FLEXIBLE = "FLEXIBLE"
    const val WEEK_52 = "WEEK_52"
    const val DAY_365 = "DAY_365"
}

data class NewTransaction(
    val type: String,
    val amountMinor: Long,
    val categoryId: String?,
    val accountId: String,
    val toAccountId: String? = null,
    val note: String = "",
    val occurredAt: Long = System.currentTimeMillis(),
)
