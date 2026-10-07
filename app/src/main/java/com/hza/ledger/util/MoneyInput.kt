package com.hza.ledger.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 把用户输入的金额文本解析为「分」。
 *
 * 铁律：**必须用 BigDecimal，绝不能用 Double**。
 * Double 计算 `12.34 * 100` 会得到 `1233.9999`，四舍五入成 1234 看似正确，
 * 但 `1.005 * 100` 会得到 `100.49999999999999` → 100，比实际少 5 分。
 * 记账 App 差一分钱就是错账，绝不能容忍。
 *
 * @return 解析成功返回分；输入非法或为空返回 null
 */
fun formatMinorInput(input: String): Long? {
    // 先 trim 再剥符号：用户可能输入「 ¥ 35.80 」这种带空格的格式，
    // 直接用 removePrefix 会因开头有空格而匹配失败。
    val cleaned = input.trim()
        .removePrefix("¥")
        .removePrefix("￥")
        .trim()
    if (cleaned.isEmpty()) return null

    return try {
        val value = BigDecimal(cleaned)
        // 负数不是合法金额输入（支出方向由类型决定，不靠符号）
        if (value.signum() < 0) return null
        value.setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .longValueExact()
    } catch (_: ArithmeticException) {
        null
    } catch (_: NumberFormatException) {
        null
    }
}

/**
 * 把「分」格式化为可编辑的元字符串。
 *
 * 用于回填编辑框：100 → "1"，1234 → "12.34"。
 */
fun minorToEditableText(minor: Long): String {
    val value = BigDecimal(minor).movePointLeft(2).setScale(2, RoundingMode.HALF_UP)
    return value.toPlainString().removeSuffix(".00")
}

/**
 * 累加金额，超出 Long 范围时饱和到上限而非溢出。
 *
 * 极端情况下（误输入超大金额）宁可封顶也不要让数值变成负数。
 */
fun safeAddMinor(a: Long, b: Long): Long {
    return try {
        Math.addExact(a, b)
    } catch (_: ArithmeticException) {
        Long.MAX_VALUE
    }
}

/**
 * 限制金额在可显示范围内，避免超大数字撑破布局。
 */
fun clampDisplayMinor(minor: Long): Long = minor.coerceIn(0L, MAX_DISPLAY_MINOR)

/** 单笔金额上限：1 亿元。超过则视为误输入。 */
const val MAX_DISPLAY_MINOR = 100_000_000_00L
