package com.hza.ledger.pending

import com.hza.ledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 待确认编辑的金额解析与校验测试。
 *
 * ## 背景
 *
 * 用户反馈「确认时不能改类型和金额，进账被判定为支出也改不了」。
 * 改造后允许手工修改，但**金额输入是记账 App 最容易出错的地方** ——
 * 差一分就是错账，比不能改更糟。
 *
 * 所以这里把边界一条条钉死。
 */
class PendingEditTest {

    // ---------- 金额解析：正常输入 ----------

    @Test
    fun `常见金额格式正确解析`() {
        assertEquals(824L, parseAmountInputToMinor("8.24"))
        assertEquals(800L, parseAmountInputToMinor("8"))
        assertEquals(50L, parseAmountInputToMinor("0.5"))
        assertEquals(1L, parseAmountInputToMinor("0.01"))
        assertEquals(10000L, parseAmountInputToMinor("100.00"))
    }

    @Test
    fun `带前后空格应能解析`() {
        // 用户从别处复制时很容易带上空格
        assertEquals(824L, parseAmountInputToMinor("  8.24  "))
    }

    @Test
    fun `千分位逗号应被忽略`() {
        // 从银行短信或对账单粘贴过来通常带千分位
        assertEquals(123456L, parseAmountInputToMinor("1,234.56"))
        assertEquals(100000000L, parseAmountInputToMinor("1,000,000"))
    }

    @Test
    fun `中文逗号也应被忽略`() {
        // 中文输入法下打出的逗号是「，」，不处理会让用户莫名失败
        assertEquals(123456L, parseAmountInputToMinor("1，234.56"))
    }

    @Test
    fun `省略整数位的小数应能解析`() {
        assertEquals(50L, parseAmountInputToMinor(".5"))
    }

    @Test
    fun `尾部小数点视为整数`() {
        assertEquals(800L, parseAmountInputToMinor("8."))
    }

    @Test
    fun `前导零不影响结果`() {
        assertEquals(850L, parseAmountInputToMinor("08.50"))
        assertEquals(850L, parseAmountInputToMinor("008.50"))
    }

    // ---------- 金额解析：精度（最关键） ----------

    @Test
    fun `金额精度不能受浮点数影响`() {
        // ⚠️ 核心断言：用 Double 时 8.24 * 100 = 823.9999999999999，
        // 转 Long 会变成 823，差一分钱
        assertEquals(824L, parseAmountInputToMinor("8.24"))
        assertEquals(29L, parseAmountInputToMinor("0.29"))
        assertEquals(158L, parseAmountInputToMinor("1.58"))
        assertEquals(111L, parseAmountInputToMinor("1.11"))
        assertEquals(1233L, parseAmountInputToMinor("12.33"))
    }

    @Test
    fun `大量金额都能精确转分`() {
        // 穷举两位小数，确保没有边界值出错。
        // ⚠️ 跳过 0.00 —— 金额必须大于 0，零是非法输入（另有测试覆盖）
        for (yuan in 0..200) {
            for (cent in 0..99) {
                if (yuan == 0 && cent == 0) continue
                val text = "$yuan.${cent.toString().padStart(2, '0')}"
                val expected = yuan * 100L + cent
                assertEquals("解析 $text 出错", expected, parseAmountInputToMinor(text))
            }
        }
    }

    // ---------- 金额解析：非法输入 ----------

    @Test
    fun `空输入应返回空`() {
        assertNull(parseAmountInputToMinor(""))
        assertNull(parseAmountInputToMinor("   "))
    }

    @Test
    fun `非数字应返回空`() {
        assertNull(parseAmountInputToMinor("abc"))
        assertNull(parseAmountInputToMinor("8元"))
        assertNull(parseAmountInputToMinor("8.24元"))
        assertNull(parseAmountInputToMinor("￥8.24"))
    }

    @Test
    fun `零与负数应被拒绝`() {
        assertNull("金额必须大于 0", parseAmountInputToMinor("0"))
        assertNull(parseAmountInputToMinor("0.00"))
        assertNull(parseAmountInputToMinor("-5"))
    }

    @Test
    fun `超过两位小数应被拒绝而不是静默四舍五入`() {
        // ⚠️ 静默改变用户输入会让人困惑：
        // 输入 8.245 却记成 8.25，用户会怀疑账本算错了
        assertNull(parseAmountInputToMinor("8.245"))
        assertNull(parseAmountInputToMinor("8.249"))
        assertNull(parseAmountInputToMinor("0.001"))
    }

    @Test
    fun `多个小数点应被拒绝`() {
        assertNull(parseAmountInputToMinor("1.2.3"))
        assertNull(parseAmountInputToMinor("8..24"))
    }

    @Test
    fun `只有小数点应被拒绝`() {
        assertNull(parseAmountInputToMinor("."))
    }

    @Test
    fun `超大金额应被拒绝`() {
        // 手滑多打几个零的保护
        assertNull(parseAmountInputToMinor("99999999999"))
        assertNull(parseAmountInputToMinor("100000000.00"))
    }

    @Test
    fun `上限边界值应被接受`() {
        // 99,999,999.99 是允许的最大值
        assertEquals(9_999_999_999L, parseAmountInputToMinor("99999999.99"))
    }

    // ---------- 错误提示 ----------

    @Test
    fun `各种非法输入都能给出具体原因`() {
        assertEquals(PendingEditError.AmountInvalid, describeAmountProblem(""))
        assertEquals(PendingEditError.AmountInvalid, describeAmountProblem("abc"))

        // 小数位问题与"格式错误"要区分开，提示才有指导意义
        assertEquals(
            PendingEditError.AmountTooManyDecimals,
            describeAmountProblem("8.245"),
        )
        assertEquals(PendingEditError.AmountNotPositive, describeAmountProblem("0"))
        assertEquals(PendingEditError.AmountNotPositive, describeAmountProblem("-5"))
        assertEquals(PendingEditError.AmountTooLarge, describeAmountProblem("99999999999"))
    }

    @Test
    fun `合法输入不报错`() {
        assertNull(describeAmountProblem("8.24"))
    }

    @Test
    fun `每条错误都有中文提示`() {
        val errors = listOf(
            PendingEditError.AmountInvalid,
            PendingEditError.AmountNotPositive,
            PendingEditError.AmountTooManyDecimals,
            PendingEditError.AmountTooLarge,
            PendingEditError.AccountMissing,
            PendingEditError.TransferTargetMissing,
            PendingEditError.TransferSameAccount,
            PendingEditError.CategoryMissing,
        )
        errors.forEach {
            assertNotNull("$it 缺少提示", it.message())
        }
    }

    // ---------- 编辑校验：支出与收入 ----------

    @Test
    fun `合法的支出可以通过`() {
        assertNull(
            validatePendingEdit(
                type = "EXPENSE",
                amountMinor = 824,
                accountId = "acc-1",
                toAccountId = null,
                categoryId = "expense-food",
            ),
        )
    }

    @Test
    fun `合法的收入可以通过`() {
        assertNull(
            validatePendingEdit(
                type = "INCOME",
                amountMinor = 850000,
                accountId = "acc-1",
                toAccountId = null,
                categoryId = "income-salary",
            ),
        )
    }

    @Test
    fun `非转账缺少分类应被拒绝`() {
        // ⚠️ 这条防止出现「收入 + 空分类」的记录
        assertEquals(
            PendingEditError.CategoryMissing,
            validatePendingEdit("EXPENSE", 824, "acc-1", null, null),
        )
        assertEquals(
            PendingEditError.CategoryMissing,
            validatePendingEdit("INCOME", 824, "acc-1", null, "  "),
        )
    }

    @Test
    fun `未选账户应被拒绝`() {
        assertEquals(
            PendingEditError.AccountMissing,
            validatePendingEdit("EXPENSE", 824, null, null, "expense-food"),
        )
        assertEquals(
            PendingEditError.AccountMissing,
            validatePendingEdit("EXPENSE", 824, "  ", null, "expense-food"),
        )
    }

    @Test
    fun `金额非正应被拒绝`() {
        assertEquals(
            PendingEditError.AmountNotPositive,
            validatePendingEdit("EXPENSE", 0, "acc-1", null, "expense-food"),
        )
        assertEquals(
            PendingEditError.AmountNotPositive,
            validatePendingEdit("EXPENSE", -100, "acc-1", null, "expense-food"),
        )
    }

    // ---------- 编辑校验：转账 ----------

    @Test
    fun `合法转账可以通过`() {
        assertNull(
            validatePendingEdit(
                type = TransactionType.TRANSFER,
                amountMinor = 10000,
                accountId = "acc-1",
                toAccountId = "acc-2",
                categoryId = null,
            ),
        )
    }

    @Test
    fun `转账不选转入账户应被拒绝`() {
        // ⚠️ 这是最关键的一条：只有转出没有转入，
        // 会导致钱从一个账户扣掉、另一个账户没加，余额永久对不上
        assertEquals(
            PendingEditError.TransferTargetMissing,
            validatePendingEdit(TransactionType.TRANSFER, 10000, "acc-1", null, null),
        )
        assertEquals(
            PendingEditError.TransferTargetMissing,
            validatePendingEdit(TransactionType.TRANSFER, 10000, "acc-1", "  ", null),
        )
    }

    @Test
    fun `转出转入同账户应被拒绝`() {
        assertEquals(
            PendingEditError.TransferSameAccount,
            validatePendingEdit(TransactionType.TRANSFER, 10000, "acc-1", "acc-1", null),
        )
    }

    @Test
    fun `转账不需要分类`() {
        // 转账是账户间移动，不归属任何收支科目
        assertNull(
            validatePendingEdit(TransactionType.TRANSFER, 10000, "acc-1", "acc-2", categoryId = null),
        )
    }

    // ---------- 回填输入框 ----------

    @Test
    fun `分值格式化回输入框`() {
        assertEquals("8.24", formatMinorForInput(824))
        assertEquals("0.01", formatMinorForInput(1))
        assertEquals("100.00", formatMinorForInput(10000))
        assertEquals("10000.00", formatMinorForInput(1_000_000))
    }

    @Test
    fun `格式化结果可直接被解析回原值`() {
        // 往返一致性：打开编辑框时要能原样回填
        listOf(1L, 50L, 824L, 10000L, 9_999_999_999L).forEach { minor ->
            assertEquals(
                "往返失败: $minor",
                minor,
                parseAmountInputToMinor(formatMinorForInput(minor)),
            )
        }
    }
}