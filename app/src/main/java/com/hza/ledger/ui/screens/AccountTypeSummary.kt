package com.hza.ledger.ui.screens

import com.hza.ledger.data.local.AccountEntity
import com.hza.ledger.data.local.AccountBalanceItem
import androidx.compose.ui.graphics.Color
import com.hza.ledger.domain.model.AccountType
import com.hza.ledger.ui.theme.BrandBlue
import com.hza.ledger.ui.theme.ExpenseCoral
import com.hza.ledger.ui.theme.IncomeMint
import com.hza.ledger.ui.theme.SavingsAmber
import com.hza.ledger.ui.theme.Violet

/**
 * 按账户类型分组的汇总结果。
 *
 * @param type 账户类型（AccountType 的常量）
 * @param totalMinor 该类型下所有账户余额之和（分）
 * @param count 该类型下的账户数量
 */
data class AccountTypeSummary(
    val type: String,
    val totalMinor: Long,
    val count: Int,
)

/**
 * 按账户类型分别统计总金额。
 *
 * 需求：「各个账户类型设置成单独的，然后计算总金额」——
 * 用户希望看到现金、银行卡、数字账户等各自的小计，而不是只有一个总数。
 *
 * 设计要点：
 * - 只统计标记为「计入净资产」的账户，与净资产口径保持一致
 * - 不排除负余额账户（如信用卡欠款），负数本身是有效信息
 * - 返回结果按总额降序，便于界面上从大到小展示
 * - 余额缺失时回退到期初余额，与 [calculateNetWorth] 口径一致
 */
internal fun summarizeByAccountType(
    accounts: List<AccountEntity>,
    balanceItems: List<AccountBalanceItem>,
): List<AccountTypeSummary> {
    val balanceByAccountId = balanceItems.associate { it.accountId to it.currentBalanceMinor }

    return accounts
        .filter { it.includeInNetWorth }
        .groupBy { it.type }
        .map { (type, group) ->
            AccountTypeSummary(
                type = type,
                totalMinor = group.sumOf { account ->
                    balanceByAccountId[account.id] ?: account.openingBalanceMinor
                },
                count = group.size,
            )
        }
        .sortedByDescending { it.totalMinor }
}

/** 账户类型的界面显示名。 */
internal fun accountTypeName(type: String): String = when (type) {
    AccountType.CASH -> "现金"
    AccountType.BANK -> "银行卡"
    AccountType.DIGITAL -> "数字账户"
    AccountType.CREDIT -> "信用账户"
    AccountType.INVESTMENT -> "投资账户"
    AccountType.SALARY -> "工资卡"
    else -> "其他"
}

/** 账户类型的配色，汇总卡片与账户图标共用，保证视觉一致。 */
internal fun accountTypeColor(type: String) = when (type) {
    AccountType.CASH -> SavingsAmber
    AccountType.BANK -> BrandBlue
    AccountType.DIGITAL -> IncomeMint
    AccountType.CREDIT -> ExpenseCoral
    AccountType.INVESTMENT -> Violet
    // 工资卡用绿色系，与「收入」的语义呼应，一眼看出这是进钱的地方
    AccountType.SALARY -> Color(0xFF2E9E6B)
    else -> Color(0xFF8992A3)
}
