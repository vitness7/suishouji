package com.hza.ledger.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryAnalyticsTest {

    @Test
    fun `占比按金额降序且总和为一百`() {
        val shares = calculateCategoryShares(
            listOf(
                "餐饮" to 3000L,
                "交通" to 1000L,
                "购物" to 6000L,
            ),
        )

        assertEquals(listOf("购物", "餐饮", "交通"), shares.map { it.name })
        val totalShare = shares.sumOf { it.share.toDouble() }.toFloat()
        assertEquals(1f, totalShare, 0.0001f)
    }

    @Test
    fun `占比为零的总额时不会除零`() {
        val shares = calculateCategoryShares(listOf("餐饮" to 0L, "交通" to 0L))

        assertEquals(2, shares.size)
        assertTrue(shares.all { it.share == 0f })
    }

    @Test
    fun `同名分类被合并且笔数累计`() {
        val shares = calculateCategoryShares(
            listOf("餐饮" to 1000L, "餐饮" to 2000L, "交通" to 500L),
        )

        val food = shares.first { it.name == "餐饮" }
        assertEquals(3000L, food.amountMinor)
        assertEquals(2, food.count)
    }

    @Test
    fun `金额相同时按名称排序保证结果稳定`() {
        val first = calculateCategoryShares(listOf("乙类" to 1000L, "甲类" to 1000L))
        val second = calculateCategoryShares(listOf("甲类" to 1000L, "乙类" to 1000L))

        assertEquals(first.map { it.name }, second.map { it.name })
        assertEquals(listOf("乙类", "甲类"), first.map { it.name })
    }

    @Test
    fun `空列表返回空结果`() {
        assertTrue(calculateCategoryShares(emptyList()).isEmpty())
    }

    @Test
    fun `百分比文本保留一位小数且去掉多余零`() {
        assertEquals("12.3%", formatShare(0.1234f))
        assertEquals("50%", formatShare(0.5f))
        assertEquals("100%", formatShare(1f))
        assertEquals("0%", formatShare(0f))
    }

    @Test
    fun `环比增长时变化额与变化率正确`() {
        val comparison = PeriodComparison(current = 1200L, previous = 1000L)

        assertEquals(200L, comparison.delta)
        assertTrue(comparison.isIncrease)
        assertEquals(0.2f, comparison.rate!!, 0.0001f)
        assertEquals("+20%", comparison.formatRate())
    }

    @Test
    fun `环比下降时变化额为负`() {
        val comparison = PeriodComparison(current = 800L, previous = 1000L)

        assertEquals(-200L, comparison.delta)
        assertTrue(!comparison.isIncrease)
        assertEquals("-20%", comparison.formatRate())
    }

    @Test
    fun `上期为零时变化率不可比但delta仍正确`() {
        val comparison = PeriodComparison(current = 500L, previous = 0L)

        assertNull(comparison.rate)
        assertEquals("—", comparison.formatRate())
        assertEquals(500L, comparison.delta)
    }

    @Test
    fun `持平文案正确`() {
        val comparison = PeriodComparison(current = 1000L, previous = 1000L)

        assertEquals("与上期持平", comparison.describe("花") { "" })
    }

    @Test
    fun `环比描述文案区分多花与少花`() {
        val formatter: (Long) -> String = { "¥$it" }

        assertEquals(
            "较上期多花¥200",
            PeriodComparison(1200L, 1000L).describe("花", formatter),
        )
        assertEquals(
            "较上期少花¥200",
            PeriodComparison(800L, 1000L).describe("花", formatter),
        )
    }

    @Test
    fun `环比变化率保留一位小数`() {
        assertEquals("+12.5%", PeriodComparison(1125L, 1000L).formatRate())
        assertEquals("-8.3%", PeriodComparison(917L, 1000L).formatRate())
    }
}