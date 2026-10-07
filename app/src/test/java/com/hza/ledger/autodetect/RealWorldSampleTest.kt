package com.hza.ledger.autodetect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.1.1 新增：真实通知样本回归测试。
 *
 * 背景：v1.1.0 因包名白名单过窄导致识别完全失效。本组测试全部使用
 * 真实世界的通知/短信文案，确保解析器能覆盖常见格式。
 */
class RealWorldSampleTest {

    // ---------- 微信支付真实通知 ----------

    @Test
    fun `微信支付凭证型通知能识别`() {
        val result = parseTransactionText("微信支付凭证 付款给海底捞 ¥268.00")

        assertNotNull("微信支付凭证格式应能识别", result)
        assertEquals(26800L, result!!.amountMinor)
        assertEquals(ParseChannel.WECHAT, result.channel)
    }

    @Test
    fun `微信支付完成通知能识别`() {
        val result = parseTransactionText("微信支付 支付成功  ¥35.80  零钱")

        assertNotNull(result)
        assertEquals(3580L, result!!.amountMinor)
        assertEquals(ParseDirection.EXPENSE, result.direction)
    }

    @Test
    fun `微信服务通知格式能识别`() {
        // 微信「服务通知」常见文案：不含"微信支付"四字
        val result = parseTransactionText("你有一笔支出  ¥56.00  在美团外卖")

        assertNotNull(result)
        assertEquals(5600L, result!!.amountMinor)
    }

    @Test
    fun `微信收款能识别为收入`() {
        val result = parseTransactionText("微信支付 收款 ¥200.00 来自张三")

        assertNotNull(result)
        assertEquals(20000L, result!!.amountMinor)
        assertEquals(ParseDirection.INCOME, result!!.direction)
    }

    // ---------- 支付宝真实通知 ----------

    @Test
    fun `支付宝付款通知能识别`() {
        val result = parseTransactionText("支付宝 付款成功 永辉超市 ￥156.30")

        assertNotNull(result)
        assertEquals(15630L, result!!.amountMinor)
        assertEquals(ParseChannel.ALIPAY, result.channel)
    }

    @Test
    fun `花呗账单通知能识别`() {
        val result = parseTransactionText("花呗 消费支出 ¥89.90  商家：永辉超市")

        assertNotNull(result)
        assertEquals(8990L, result!!.amountMinor)
        assertEquals(ParseDirection.EXPENSE, result!!.direction)
    }

    // ---------- 银行真实短信 ----------

    @Test
    fun `招商银行快捷支付短信能识别`() {
        val result = parseTransactionText(
            "【招商银行】您尾号1234的储蓄卡8月6日快捷支付支出人民币68.50元",
        )

        assertNotNull(result)
        assertEquals(6850L, result!!.amountMinor)
        assertEquals(ParseChannel.BANK_CARD, result!!.channel)
    }

    @Test
    fun `工商银行消费短信能识别`() {
        val result = parseTransactionText("【工商银行】您账户1234于8月6日消费支出128.00元")

        assertNotNull(result)
        assertEquals(12800L, result!!.amountMinor)
    }

    @Test
    fun `建设银行代扣短信能识别`() {
        val result = parseTransactionText("【中国建设银行】代扣水费人民币45.00元")

        assertNotNull(result)
        assertEquals(4500L, result!!.amountMinor)
    }

    @Test
    fun `信用卡还款短信能识别`() {
        val result = parseTransactionText("【广发银行】您的信用卡消费 1280.00元")

        assertNotNull(result)
        assertEquals(128000L, result!!.amountMinor)
    }

    @Test
    fun `工资入账短信能识别为收入`() {
        val result = parseTransactionText("【招商银行】工资入账人民币12500.00元")

        assertNotNull(result)
        assertEquals(1250000L, result!!.amountMinor)
        assertEquals(ParseDirection.INCOME, result!!.direction)
    }

    // ---------- 纯数字不采信（防卡号误判） ----------

    @Test
    fun `无货币符号的裸数字不应被误当金额`() {
        // "尾号1234" 在剥离后不应再被当成金额 12.34
        val result = parseTransactionText("【招商银行】您尾号1234的储蓄卡8月6日快捷支付支出人民币68.50元")

        assertNotNull(result)
        assertEquals("应取68.50元而非卡号或日期数字", 6850L, result!!.amountMinor)
    }

    @Test
    fun `只有卡号没有金额时应拒绝`() {
        assertNull(parseTransactionText("【招商银行】您尾号5678的信用卡"))
    }

    @Test
    fun `订单通知不应被误判为交易`() {
        // 订单金额存在但无支付语义词，不应入账
        val result = parseTransactionText("您的订单 1234567890 已发货")
        assertNull(result)
    }

    // ---------- 拒绝规则不能过严 ----------

    @Test
    fun `含“活动”二字的真实支付通知仍应识别`() {
        // 过度拒绝是上一版识别失败的原因之一，不能把正常交易误杀
        val result = parseTransactionText("微信支付 付款成功 ¥88.00 活动商品")

        assertNotNull("含'活动'的交易通知不应被误杀", result)
        assertEquals(8800L, result!!.amountMinor)
    }

    @Test
    fun `含“券”字的消费通知仍应识别`() {
        val result = parseTransactionText("微信支付 ¥12.50 使用优惠券")

        assertNotNull("用券消费是真实支出，不应被误杀", result)
        assertEquals(1250L, result!!.amountMinor)
    }

    @Test
    fun `真正的营销短信仍应被拒绝`() {
        // 注意：这里用的是"秒杀"这类强营销词。
        // "活动/券/红包" 已从拒绝列表移除，因为真实消费通知里太常见。
        assertNull(parseTransactionText("【秒杀】天天神价，限时抢购，点击查看详情"))
        assertNull(parseTransactionText("邀请好友得红包，签到领积分"))
    }

    @Test
    fun `验证码短信仍应被拒绝`() {
        assertNull(parseTransactionText("您的验证码是888888，5分钟内有效"))
    }

    // ---------- 置信度门槛 ----------

    @Test
    fun `典型真实通知的置信度应达到入库门槛`() {
        val samples = listOf(
            "微信支付 付款给星巴克 ¥35.80",
            "支付宝 付款成功 永辉超市 ￥156.30",
            "【招商银行】您尾号1234的储蓄卡快捷支付支出人民币68.50元",
            "微信支付 收款 ¥200.00 来自张三",
        )

        samples.forEach { sample ->
            val parsed = parseTransactionText(sample)
            assertNotNull("应能解析：$sample", parsed)
            assertTrue(
                "置信度应达到 $MIN_CONFIDENCE，实际 ${parsed!!.confidence}：$sample",
                parsed.confidence >= MIN_CONFIDENCE,
            )
        }
    }

    @Test
    fun `系统脱敏通知不产生候选`() {
        assertNull(parseTransactionText("某应用发来通知"))
        assertNull(parseTransactionText("你有一条新消息"))
    }
}
