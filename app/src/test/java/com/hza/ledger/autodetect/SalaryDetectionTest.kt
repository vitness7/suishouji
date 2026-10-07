package com.hza.ledger.autodetect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.5.0 工资识别回归测试。
 *
 * 需求：设置 15 号为发工资日，工资到账后计入资产。
 * 用户明确说明「金额不固定，工资发了会发短信」，
 * 因此必须靠**短信文本**识别，不能依赖固定金额配置。
 *
 * 关键设计：工资到账后要能自动归到「工资卡」账户，
 * 这样资产页能看到这笔钱，而不是只记一笔悬空收入。
 */
class SalaryDetectionTest {

    // ---------- 各家银行的工资短信格式 ----------

    @Test
    fun `招商银行代发工资能识别为工资`() {
        val result = parseTransactionText("【招商银行】代发工资：您的账户于10月15日入账人民币8500.00元")

        assertNotNull("工资短信必须能识别", result)
        assertEquals(850000L, result!!.amountMinor)
        assertEquals(ParseDirection.INCOME, result.direction)
        assertEquals("工资应归工资分类", "income-salary", result.suggestedCategoryKey)
    }

    @Test
    fun `工商银行工资到账能识别`() {
        val result = parseTransactionText("【中国工商银行】您的账户10月15日工资到账人民币12,500.00元")

        assertNotNull(result)
        assertEquals(1250000L, result!!.amountMinor)
        assertEquals("income-salary", result.suggestedCategoryKey)
    }

    @Test
    fun `建设银行代发薪酬能识别`() {
        val result = parseTransactionText("【中国建设银行】代发薪酬人民币8800.00元已入账")

        assertNotNull(result)
        assertEquals(880000L, result!!.amountMinor)
        assertEquals("income-salary", result.suggestedCategoryKey)
    }

    @Test
    fun `农业银行工资发放能识别`() {
        val result = parseTransactionText("【中国农业银行】工资发放8500.00元")

        assertNotNull(result)
        assertEquals(850000L, result!!.amountMinor)
        assertEquals("income-salary", result.suggestedCategoryKey)
    }

    @Test
    fun `中信银行代扣代发工资能识别`() {
        val result = parseTransactionText("【中信银行】代扣代发工资人民币15800.00元")

        assertNotNull(result)
        assertEquals(1580000L, result!!.amountMinor)
        assertEquals("income-salary", result.suggestedCategoryKey)
    }

    @Test
    fun `邮政储蓄银行代发工资能识别`() {
        val result = parseTransactionText("【中国邮政储蓄银行】代发工资6500.00元")

        assertNotNull(result)
        assertEquals(650000L, result!!.amountMinor)
        assertEquals("income-salary", result.suggestedCategoryKey)
    }

    @Test
    fun `薪资也能识别（部分银行用这个词）`() {
        val result = parseTransactionText("您本月薪资人民币12000.00元已到账")

        assertNotNull(result)
        assertEquals(1200000L, result!!.amountMinor)
        assertEquals("income-salary", result.suggestedCategoryKey)
    }

    @Test
    fun `薪酬也能识别`() {
        val result = parseTransactionText("本期薪酬人民币9800.00元")

        assertNotNull(result)
        assertEquals(980000L, result!!.amountMinor)
        assertEquals("income-salary", result.suggestedCategoryKey)
    }

    // ---------- 大额工资不被误判 ----------

    @Test
    fun `高额工资不会触发营收拦截`() {
        // 工资是收入但**不是营业收入**，绝不能被电商营收规则误杀
        val result = parseTransactionText("【招商银行】代发工资人民币58888.00元")

        assertNotNull("高额工资不应被营收规则拦截", result)
        assertEquals(5888800L, result!!.amountMinor)
        assertEquals(ParseDirection.INCOME, result.direction)
    }

    @Test
    fun `工资不含店铺收款字样时正常识别`() {
        val result = parseTransactionText("【工商银行】工资到账 8500.00")

        assertNotNull(result)
        assertEquals(ParseDirection.INCOME, result!!.direction)
    }

    // ---------- 不能把非工资误判为工资 ----------

    @Test
    fun `店铺营收仍不应被识别为工资`() {
        org.junit.Assert.assertNull(
            "店铺货款到账不是工资",
            parseTransactionText("【招商银行】货款到账人民币5600.00元"),
        )
    }

    @Test
    fun `退款不会被当成工资`() {
        val result = parseTransactionText("【招商银行】退款人民币120.00元")

        assertNotNull(result)
        assertEquals(
            "退款不应归工资分类",
            false,
            result!!.suggestedCategoryKey == "income-salary",
        )
    }

    @Test
    fun `经营性收款不归工资`() {
        val result = parseTransactionText("【招商银行】货款结算人民币12000.00元")

        org.junit.Assert.assertNull("经营结算不应被识别", result)
    }

    // ---------- 置信度需达到入库门槛 ----------

    @Test
    fun `工资识别置信度应达标`() {
        val samples = listOf(
            "【招商银行】代发工资：您的账户于10月15日入账人民币8500.00元",
            "【中国工商银行】您的账户10月15日工资到账人民币12,500.00元",
            "您本月薪资人民币12000.00元已到账",
        )

        samples.forEach { sample ->
            val parsed = parseTransactionText(sample)
            assertNotNull("应能解析：$sample", parsed)
            assertTrue(
                "置信度需达 $MIN_CONFIDENCE，实际 ${parsed!!.confidence}：$sample",
                parsed.confidence >= MIN_CONFIDENCE,
            )
        }
    }

    /**
     * 金额剥离顺序的回归测试。
     *
     * ⚠️ 背景：卡号规则是「≥7 位连续数字」，而金额 123456.78 有 6 位整数部分。
     * 若先剥卡号会把小数点截断，金额放大百倍 —— 这是会毁灭账本的严重错账。
     */
    @Test
    fun `六位整数金额不会被卡号规则破坏`() {
        val result = parseTransactionText("微信支付 付款 ¥123456.78超市")

        assertNotNull(result)
        assertEquals("123456.78 应等于 12345678分", 12345678L, result!!.amountMinor)
    }

    @Test
    fun `卡号尾号带小数不应被当成金额`() {
        // 「尾号 1234.56」不是金额，只是卡号片段
        val result = parseTransactionText("【招商银行】您尾号1234.56的储蓄卡已开通")

        assertNull("卡号片段不应被识别为交易", result)
    }

    @Test
    fun `订单号带小数不应被当成金额`() {
        assertNull(
            parseTransactionText("您的订单号 123456.78 已发货"),
        )
    }
}