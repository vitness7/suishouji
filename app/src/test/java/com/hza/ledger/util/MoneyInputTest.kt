package com.hza.ledger.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyInputTest {

    @Test
    fun `基本解析正确`() {
        assertEquals(1234L, formatMinorInput("12.34"))
        assertEquals(1200L, formatMinorInput("12"))
        assertEquals(1200L, formatMinorInput("12.0"))
        assertEquals(1200L, formatMinorInput("12.00"))
        assertEquals(5L, formatMinorInput("0.05"))
    }

    /**
     * 精度回归：这些是 Double 计算会出错的经典值。
     * `1.005 * 100 = 100.49999999999999` → Double 会得到 100，少 5 分。
     */
    @Test
    fun `三位小数的金额正确进位而非丢失`() {
        assertEquals("1.005 应进位到 101分", 101L, formatMinorInput("1.005"))
        assertEquals("8.615 应进位到 862 分", 862L, formatMinorInput("8.615"))
        assertEquals("1.004 应截到 100分", 100L, formatMinorInput("1.004"))
    }

    @Test
    fun `大量金额无精度损失`() {
        // Double 在这个值上会失准
        assertEquals(123456789L, formatMinorInput("1234567.89"))
        assertEquals(99999999L, formatMinorInput("999999.99"))
    }

    @Test
    fun `带货币符号能解析`() {
        assertEquals(3580L, formatMinorInput("¥35.80"))
        assertEquals(3580L, formatMinorInput("￥35.80"))
        assertEquals(3580L, formatMinorInput(" ¥ 35.80 "))
    }

    @Test
    fun `空与非法输入返回空`() {
        assertNull(formatMinorInput(""))
        assertNull(formatMinorInput("   "))
        assertNull(formatMinorInput("abc"))
        assertNull(formatMinorInput("12.3.4"))
        assertNull(formatMinorInput("--12"))
    }

    @Test
    fun `负数被拒绝（方向由类型决定不靠符号）`() {
        assertNull(formatMinorInput("-12.34"))
        assertNull(formatMinorInput("-0.01"))
    }

    @Test
    fun `超大金额仍能解析但不超上限`() {
        assertEquals(MAX_DISPLAY_MINOR, formatMinorInput("100000000"))
    }

    @Test
    fun `回填编辑框格式正确`() {
        assertEquals("1", minorToEditableText(100L))
        assertEquals("12.34", minorToEditableText(1234L))
        assertEquals("0.05", minorToEditableText(5L))
        assertEquals("0", minorToEditableText(0L))
    }

    @Test
    fun `解析与回填可往返一致`() {
        listOf(0L, 5L, 100L, 1234L, 999999L, 123456789L).forEach { minor ->
            assertEquals("往返应一致：$minor", minor, formatMinorInput(minorToEditableText(minor)))
        }
    }

    @Test
    fun `累加溢出时饱和而非变负`() {
        assertEquals(300L, safeAddMinor(100L, 200L))
        assertEquals(Long.MAX_VALUE, safeAddMinor(Long.MAX_VALUE, 100L))
    }
}
