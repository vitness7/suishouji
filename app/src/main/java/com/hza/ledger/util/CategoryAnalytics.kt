package com.hza.ledger.util

/**
 * 分类占比与环比计算。纯 Kotlin，无 Android 依赖，便于单元测试。
 */

/**
 * 单个分类的统计结果。
 *
 * @param name 分类名称
 * @param amountMinor 金额（单位：分）
 * @param share 占比，取值 0f..1f
 * @param count 该分类下的流水笔数
 */
data class CategoryShare(
    val name: String,
    val amountMinor: Long,
    val share: Float,
    val count: Int,
)

/**
 * 环比对比结果。
 *
 * @param current 本期金额（分）
 * @param previous 上期金额（分）
 * @param delta 变化额 = current - previous（分）
 * @param rate 变化率 = delta / previous；上期为 0 时返回 null 表示不可比
 * @param isIncrease 是否为增长（支出场景下增长通常不是好事）
 */
data class PeriodComparison(
    val current: Long,
    val previous: Long,
) {
    val delta: Long get() = current - previous
    val isIncrease: Boolean get() = delta > 0
    val rate: Float? get() = if (previous == 0L) null else delta.toFloat() / previous

    /** 格式化变化率为百分比文本，例如 "+12.5%" / "-8.3%" / "—" 。 */
    fun formatRate(): String {
        val r = rate ?: return "—"
        val percent = r * 100f
        val sign = if (percent > 0f) "+" else ""
        return "$sign${trimZeros(percent)}%"
    }

    /** 描述性文案，例如 "较上月多花 ¥120.50" / "较上月少花 ¥30.00" / "与上月持平" 。 */
    fun describe(unit: String, formatter: (Long) -> String): String = when {
        delta == 0L -> "与上期持平"
        isIncrease -> "较上期多$unit${formatter(delta)}"
        else -> "较上期少$unit${formatter(-delta)}"
    }
}

private fun trimZeros(value: Float): String {
    val rounded = Math.round(value * 10f) / 10f
    return if (rounded == rounded.toLong().toFloat()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}

/**
 * 计算各分类的金额与占比，按金额降序。
 *
 * 占比以 [expenseTotal] 为基数；为 0 时全部记为 0f，避免除零。
 * 金额相同者按名称排序，保证结果稳定可测。
 */
fun calculateCategoryShares(
    expenses: List<Pair<String, Long>>,
): List<CategoryShare> {
    val expenseTotal = expenses.sumOf { it.second }
    return expenses
        .groupBy({ it.first }, { it.second })
        .map { (name, amounts) ->
            val amount = amounts.sum()
            CategoryShare(
                name = name,
                amountMinor = amount,
                share = if (expenseTotal == 0L) 0f else amount.toFloat() / expenseTotal,
                count = amounts.size,
            )
        }
        .sortedWith(compareByDescending<CategoryShare> { it.amountMinor }.thenBy { it.name })
}

/**
 * 把占比格式化为百分比文本，保留一位小数。
 * 例如 0.1234f -> "12.3%" ，0.5f -> "50%" 。
 */
fun formatShare(share: Float): String {
    val percent = share * 100f
    val rounded = Math.round(percent * 10f) / 10f
    return if (rounded == rounded.toLong().toFloat()) {
        "${rounded.toLong().toInt()}%"
    } else {
        "$rounded%"
    }
}