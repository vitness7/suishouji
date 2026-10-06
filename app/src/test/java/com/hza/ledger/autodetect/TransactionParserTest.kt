package com.hza.ledger.autodetect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionParserTest {

    // ---------- 微信支付 ----------

    @Test
    fun `微信支付通知能解析出金额与方向`() {
        val result = parseTransactionText("微信支付  付款给星巴克 ¥35.80  零钱")

        assertNotNull(result)
        assertEquals(3580L, result!!.amountMinor)
        assertEquals(ParseDirection.EXPENSE, result.direction)
        assertEquals(ParseChannel.WECHAT, result.channel)
    }

    @Test
    fun `微信支付带元字后缀`() {
        val result = parseTransactionText("您有一笔微信支付支出 128.50元")

        assertNotNull(result)
        assertEquals(12850L, result!!.amountMinor)
    }

    // ---------- 支付宝 ----------

    @Test
    fun `支付宝消费能解析`() {
        val result = parseTransactionText("支付宝  付款成功  永辉超市  ￥156.30")

        assertNotNull(result)
        assertEquals(15630L, result!!.amountMinor)
        assertEquals(ParseChannel.ALIPAY, result.channel)
        assertEquals(ParseDirection.EXPENSE, result.direction)
    }

    @Test
    fun `花呗消费被识别为支出`() {
        val result = parseTransactionText("花呗 消费支出 89.90元")

        assertNotNull(result)
        assertEquals(ParseDirection.EXPENSE, result!!.direction)
    }

    // ---------- 银行与信用卡 ----------

    @Test
    fun `银行扣款短信能解析`() {
        val result = parseTransactionText("【招商银行】您尾号1234的储蓄卡8月6日快捷支付支出人民币68.50元")

        assertNotNull(result)
        assertEquals(6850L, result!!.amountMinor)
        assertEquals(ParseDirection.EXPENSE, result.direction)
    }

    @Test
    fun `信用卡消费能解析`() {
        val result = parseTransactionText("您的信用卡 12月1日消费 1280.00 元")

        assertNotNull(result)
        assertEquals(128000L, result!!.amountMinor)
        assertEquals(ParseChannel.CREDIT_CARD, result.channel)
    }

    // ---------- 收入 ----------

    @Test
    fun `工资入账被识别为收入`() {
        val result = parseTransactionText("招商银行 工资入账 12500.00元")

        assertNotNull(result)
        assertEquals(1250000L, result!!.amountMinor)
        assertEquals(ParseDirection.INCOME, result.direction)
    }

    @Test
    fun `退款被识别为收入`() {
        val result = parseTransactionText("您的订单已退款 299.00元 已原路退回")

        assertNotNull(result)
        assertEquals(29900L, result!!.amountMinor)
        assertEquals(ParseDirection.INCOME, result!!.direction)
    }

    // ---------- 商户与分类 ----------

    @Test
    fun `能提取商户名`() {
        val result = parseTransactionText("微信支付 向美团外卖付款 ¥42.00")

        assertNotNull(result)
        assertEquals("美团外卖", result!!.merchant)
    }

    @Test
    fun `餐饮商户能建议餐饮分类`() {
        val result = parseTransactionText("支付宝 付款成功 海底捞火锅 ￥268.00")

        assertNotNull(result)
        assertEquals("expense-food", result!!.suggestedCategoryKey)
    }

    @Test
    fun `加油能建议交通分类`() {
        val result = parseTransactionText("中国石化 加油站消费 300.00元")

        assertNotNull(result)
        assertEquals("expense-transport", result!!.suggestedCategoryKey)
    }

    @Test
    fun `房租能建议居住分类`() {
        val result = parseTransactionText("招商银行 房租代扣 2800.00元")

        assertNotNull(result)
        assertEquals("expense-housing", result!!.suggestedCategoryKey)
    }

    // ---------- 精度陷阱（重点） ----------

    @Test
    fun `金额转分无浮点误差`() {
        // Double 计算 12.34 * 100 会得到 1233.9999，BigDecimal 必须精确
        val result = parseTransactionText("微信支付 ¥12.34")

        assertNotNull(result)
        assertEquals(1234L, result!!.amountMinor)
    }

    @Test
    fun `一位小数的金额正确补零`() {
        val result = parseTransactionText("微信支付 ¥12.3")

        assertNotNull(result)
        assertEquals(1230L, result!!.amountMinor)
    }

    @Test
    fun `三位小数的金额被正确处理`() {
        // 超过两位小数时应向下取整到分，不应抛异常
        val result = parseTransactionText("微信支付 ¥12.345")

        assertNotNull(result)
        assertEquals(1234L, result!!.amountMinor)
    }

    @Test
    fun `千分位分隔符能正确处理`() {
        val result = parseTransactionText("招商银行 消费 1,234.56元")

        assertNotNull(result)
        assertEquals(123456L, result!!.amountMinor)
    }

    @Test
    fun `大额金额不溢出`() {
        val result = parseTransactionText("招商银行 转账 200000.00元")

        assertNotNull(result)
        assertEquals(20000000L, result!!.amountMinor)
    }

    // ---------- 必须拒绝的情况 ----------

    @Test
    fun `营销短信被拒绝`() {
        assertNull(parseTransactionText("【优惠】双十一满减活动，领取50元红包券"))
    }

    @Test
    fun `含验证码的短信被拒绝`() {
        assertNull(parseTransactionText("您的验证码是123456，请勿告诉他人"))
    }

    @Test
    fun `无金额的文本被拒绝`() {
        assertNull(parseTransactionText("微信支付成功"))
    }

    @Test
    fun `纯数字订单号被拒绝`() {
        assertNull(parseTransactionText("订单号 202608061234567890 已发货"))
    }

    @Test
    fun `空文本被拒绝`() {
        assertNull(parseTransactionText(""))
        assertNull(parseTransactionText("   "))
    }

    @Test
    fun `完全无关的文本被拒绝`() {
        assertNull(parseTransactionText("今天天气不错，适合散步"))
    }

    @Test
    fun `零金额被拒绝`() {
        assertNull(parseTransactionText("支付 0.00元"))
    }

    // ---------- 置信度 ----------

    @Test
    fun `渠道与方向明确的文本置信度较高`() {
        val confident = parseTransactionText("微信支付 付款给星巴克 ¥35.80")!!
        assertTrue(
            "渠道与方向明确时置信度应显著高于阈值，实际=${confident.confidence}",
            confident.confidence >= MIN_CONFIDENCE,
        )
    }

    @Test
    fun `置信度始终在零到一之间`() {
        val samples = listOf(
            "微信支付 ¥35.80",
            "招商银行 工资入账 12500.00元",
            "今天天气不错",
        )
        samples.mapNotNull { parseTransactionText(it) }.forEach {
            assertTrue(it.confidence in 0f..1f)
        }
    }

    // ---------- 真实场景回归 ----------

    @Test
    fun `长文本噪声会降低置信度但不影响解析`() {
        val short = parseTransactionText("微信支付 ¥35.80")!!
        val noisy = parseTransactionText("微信支付 ¥35.80 " + "详情请查看账单".repeat(30))

        assertEquals(3580L, noisy!!.amountMinor)
        assertTrue("长文本应降低置信度", noisy.confidence < short.confidence)
    }

    @Test
    fun `Android十三脱敏通知无法解析时返回空`() {
        // 系统把内容脱敏成"某应用发来通知"，此时不应硬猜
        assertNull(parseTransactionText("某应用发来通知"))
    }
}
