package com.hza.ledger.autodetect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.5.2 回归测试：修复「付款后不弹通知」。
 *
 * 用户反馈：自动识别本来有，但付款后不弹确认提醒。
 *
 * 排查发现是三个问题叠加：
 * 1. `onPendingCreated` 回调从未被赋值 —— 通知永远不弹（主因）
 * 2. `AutoDetectRepository.ingest` 漏传 packageName，电商判断失效
 * 3. 「押金」被当作经营支出关键词，日常付款被错标
 */
class PaymentNotificationRegressionTest {

    // ---------- 关键词误判回归 ----------

    @Test
    fun `押金不应被当作经营支出`() {
        // 租房押金是极常见的日常支出，早期版本收了「押金」导致误判
        val text = "微信支付 付款 ¥200.00 押金"
        assertFalse(
            "押金属生活支出，不应判为经营",
            EcommerceClassifier.isBusinessCost(text.lowercase(), null),
        )
    }

    @Test
    fun `房租押金不应被当作经营支出`() {
        val text = "微信支付 付款 ¥3000.00 房租押金"
        assertFalse(
            "房租押金属生活支出",
            EcommerceClassifier.isBusinessCost(text.lowercase(), null),
        )
    }

    @Test
    fun `店铺保证金仍应识别为经营支出`() {
        // 收窄关键词后不能把真的经营项也漏掉
        val text = "拼多多 店铺保证金 扣款 ¥3000.00"
        assertTrue(
            "店铺保证金仍需识别为经营支出",
            EcommerceClassifier.isBusinessCost(text.lowercase(), null),
        )
    }

    @Test
    fun `平台保证金仍应识别为经营支出`() {
        val text = "千牛 平台保证金扣款 ¥5000.00"
        assertTrue(
            "平台保证金仍需识别为经营支出",
            EcommerceClassifier.isBusinessCost(text.lowercase(), null),
        )
    }

    // ---------- 常见日常付款不应受影响 ----------

    @Test
    fun `常见日常消费不应被判为经营或营收`() {
        val samples = listOf(
            "微信支付 付款 ¥45.80 星巴克",
            "微信支付 付款 ¥88.00 超市",
            "微信支付 付款 ¥12.50 使用优惠券",
            "支付宝 付款 ¥25.00 便利店",
            "微信支付 付款 ¥15.00 蜜雪冰城",
            "微信支付 付款 ¥35.00 滴滴出行",
            "微信支付 付款 ¥68.00京东",
        )

        samples.forEach { text ->
            val lower = text.lowercase()
            assertFalse("不应判为营收：$text", EcommerceClassifier.isRevenue(text))
            assertFalse(
                "不应判为经营支出：$text",
                EcommerceClassifier.isBusinessCost(lower, null),
            )
            assertNotNull(
                "应能正常识别为交易：$text",
                parseTransactionText(text),
            )
        }
    }

    // ---------- 营收拦截仍需生效 ----------

    @Test
    fun `店铺营收仍应被拦截`() {
        // 收窄关键词不能把营收放进来，否则生活账本会被经营流水淹没
        val samples = listOf(
            "千牛 买家已付款 订单 ¥380.00",
            "拼多多 商家收款 ¥1280.00 已到账",
            "千牛 货款结算到账 ¥5600.00",
        )

        samples.forEach { text ->
            assertTrue("应识别为营收：$text", EcommerceClassifier.isRevenue(text))
        }
    }

    // ---------- 包名参数生效性 ----------

    @Test
    fun `传入包名后商家App的支出可判为经营`() {
        // ingest 之前漏传 packageName，导致这里拿不到来源信息
        val text = "千牛 直通车推广费 ¥568.80"
        val withPkg = EcommerceClassifier.isBusinessCost(text.lowercase(), "com.taobao.qianniu")
        assertTrue("商家App内的推广费应判为经营支出", withPkg)
    }

    @Test
    fun `不传包名时仍能靠文案识别经营支出`() {
        val text = "千牛 直通车推广费 ¥568.80"
        assertTrue(
            "有明确经营关键词时不依赖包名",
            EcommerceClassifier.isBusinessCost(text.lowercase(), null),
        )
    }
}