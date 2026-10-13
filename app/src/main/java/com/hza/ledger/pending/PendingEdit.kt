package com.hza.ledger.pending

import com.hza.ledger.domain.model.TransactionType
import java.math.BigDecimal

/**
 * 待确认流水的手工修正逻辑。
 *
 * ## 为什么需要这个
 *
 * 自动识别不可能 100% 准确，尤其是「这笔钱是进账还是出账」——
 * 通知文案里常常没有明确的收付方向词，只能靠渠道和上下文推断。
 * 识别错了却不给用户改的机会，等于逼用户放弃这笔记录。
 *
 * 所以确认环节必须可编辑，且要在写入前做完整校验 ——
 * **写入脏数据比不写更糟**：
 * 一笔「收入 + 餐饮分类」的记录会让统计报表出现无法解释的科目。
 *
 * 本文件只有纯逻辑，不依赖 Android，可完整单测。
 */

/** 单笔金额上限（分）：99,999,999.99 元。防止手滑多打几个零。 */
private const val MAX_AMOUNT_MINOR = 9_999_999_999L

/** 用户对识别结果的手工修正结果。 */
data class PendingEdit(
    val pendingId: String,
    val type: String,
    val amountMinor: Long,
    val categoryId: String?,
    val accountId: String,
    val toAccountId: String?,
    val merchant: String = "",
)

/** 编辑内容的校验结果。 */
sealed interface PendingEditError {
    /** 金额为空或不是有效数字。 */
    data object AmountInvalid : PendingEditError

    /** 金额必须大于 0。 */
    data object AmountNotPositive : PendingEditError

    /** 小数位超过两位。 */
    data object AmountTooManyDecimals : PendingEditError

    /** 金额超出合理上限。 */
    data object AmountTooLarge : PendingEditError

    /** 未选择账户。 */
    data object AccountMissing : PendingEditError

    /** 转账未选择转入账户。 */
    data object TransferTargetMissing : PendingEditError

    /** 转出与转入是同一个账户。 */
    data object TransferSameAccount : PendingEditError

    /** 非转账未选择分类。 */
    data object CategoryMissing : PendingEditError
}

/**
 * 把用户输入的金额解析成「分」。
 *
 * ⚠️ 必须用 BigDecimal。用 Double 会出现
 * `8.24 * 100 = 823.9999999999999` 这类误差，
 * 而金额差一分就是错账。
 *
 * 接受的输入形式：
 * - `8.24`、`8`、`0.5`、`  8.24  `（自动去空格）
 * - `1,234.56`（自动去千分位逗号，用户常从别处粘贴）
 *
 * 拒绝的输入：
 * - 空串、非数字、负数、零
 * - 小数位超过两位（**不四舍五入** —— 静默改变用户输入更让人困惑，
 *   直接拒绝并提示更清楚）
 * - 超出 [MAX_AMOUNT_MINOR]
 *
 * @return 分值；非法输入返回 null
 */
fun parseAmountInputToMinor(input: String): Long? {
    val cleaned = input.trim()
        .replace(",", "")
        .replace("，", "")   // 中文逗号也去掉，中文输入法下很容易打出来
        .trim()

    if (cleaned.isEmpty()) return null

    // 只允许数字与最多一个小数点 —— 提前拦截 "1.2.3"、"8元" 这类输入，
    // 比让 BigDecimal 抛异常再捕获更清晰
    if (!cleaned.matches(Regex("""\d+(\.\d*)?"""))) {
        // 允许 ".5" 这种省略整数位的写法
        if (!cleaned.matches(Regex("""\.\d+"""))) return null
    }

    val value = runCatching { BigDecimal(cleaned) }.getOrNull() ?: return null

    // 小数位检查要在转分之前 —— 否则 8.245 会先被截断再判断不出来
    if (value.scale() > 2) return null

    if (value.signum() <= 0) return null

    val minor = runCatching { value.movePointRight(2).longValueExact() }.getOrNull() ?: return null

    if (minor > MAX_AMOUNT_MINOR) return null

    return minor
}

/**
 * 把解析失败的原因说清楚，供界面提示。
 *
 * 分开写是因为「金额无效」和「金额超限」需要不同的提示文案 ——
 * 只返回 null 的话用户不知道到底哪里不对。
 */
fun describeAmountProblem(input: String): PendingEditError? {
    val cleaned = input.trim().replace(",", "").replace("，", "").trim()
    if (cleaned.isEmpty()) return PendingEditError.AmountInvalid

    val value = runCatching { BigDecimal(cleaned) }.getOrNull()
        ?: return PendingEditError.AmountInvalid

    if (value.scale() > 2) return PendingEditError.AmountTooManyDecimals
    if (value.signum() <= 0) return PendingEditError.AmountNotPositive

    val minor = runCatching { value.movePointRight(2).longValueExact() }.getOrNull()
        ?: return PendingEditError.AmountInvalid

    if (minor > MAX_AMOUNT_MINOR) return PendingEditError.AmountTooLarge
    return null
}

/**
 * 校验编辑后的内容是否可入账。
 *
 * @param type 交易类型，取值见 [com.hza.ledger.domain.model.TransactionType]
 * @param amountMinor 金额（分）
 * @param accountId 账户；转账时是**转出**账户
 * @param toAccountId 转入账户；仅转账时需要
 * @param categoryId 分类；转账不需要
 * @return 错误原因；通过时返回 null
 */
fun validatePendingEdit(
    type: String,
    amountMinor: Long,
    accountId: String?,
    toAccountId: String?,
    categoryId: String?,
): PendingEditError? {
    if (amountMinor <= 0L) return PendingEditError.AmountNotPositive
    if (amountMinor > MAX_AMOUNT_MINOR) return PendingEditError.AmountTooLarge
    if (accountId.isNullOrBlank()) return PendingEditError.AccountMissing

    return if (type == TransactionType.TRANSFER) {
        when {
            toAccountId.isNullOrBlank() -> PendingEditError.TransferTargetMissing
            // ⚠️ 转给自己等于什么都没发生，且会导致余额计算重复扣加
            toAccountId == accountId -> PendingEditError.TransferSameAccount
            else -> null
        }
    } else {
        // ⚠️ 非转账必须有分类，且分类类型要与收支方向一致 ——
        // 这条在 Repository 层还会再校验一次（防绕过），
        // 这里先挡一道，让用户立刻看到问题而不是提交后才失败
        if (categoryId.isNullOrBlank()) PendingEditError.CategoryMissing else null
    }
}

/** 错误原因对应的用户提示。 */
fun PendingEditError.message(): String = when (this) {
    PendingEditError.AmountInvalid -> "金额格式不正确"
    PendingEditError.AmountNotPositive -> "金额必须大于 0"
    PendingEditError.AmountTooManyDecimals -> "金额最多两位小数"
    PendingEditError.AmountTooLarge -> "金额过大，请检查是否多输了数字"
    PendingEditError.AccountMissing -> "请选择账户"
    PendingEditError.TransferTargetMissing -> "转账需要选择转入账户"
    PendingEditError.TransferSameAccount -> "转出与转入不能是同一个账户"
    PendingEditError.CategoryMissing -> "请选择分类"
}

/** 把分值格式化成金额输入框的初始文本，如 824 → "8.24"。 */
fun formatMinorForInput(amountMinor: Long): String =
    BigDecimal.valueOf(amountMinor, 2).toPlainString()
