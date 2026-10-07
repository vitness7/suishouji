package com.hza.ledger.autodetect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v1.3.2 电商场景回归测试。
 *
 * 背景：用户是电商从业者，手机里装着千牛工作台、拼多多商家版等App，
 * 这些 App 的支付通知也会被自动识别。关键约束：
 *
 * 1. 经营支出（进货、保证金、推广费）要记，但**归到独立的「经营支出」分类**，
 *    不可污染餐饮购物这些生活类统计。
 * 2. 店铺收到的客户付款是**营收，不自动记**——否则生活账本被经营流水淹没。
 * 3. 用户在拼多多的**个人购物仍要记**，不能因为App 相同就一律排除。
 *
 * 难点：同一个千牛 App 里既有采购付款也有日常浏览，不能靠包名一刀切。
 */
class EcommerceScenarioTest {

    // ---------- 经营支出：应识别且归到经营类 ----------

    @Test
    fun `千牛进货付款归为经营支出`() {
        val result = parseTransactionText("千牛 采购订单付款成功 ¥1280.00")

        assertNotNull("进货付款应能识别", result)
        assertEquals(128000L, result!!.amountMinor)
        assertEquals(ParseDirection.EXPENSE, result.direction)
        assertEquals(
            "进货必须归到经营支出，不能污染生活分类",
            "expense-business", result.suggestedCategoryKey,
        )
    }

    @Test
    fun `店铺保证金归为经营支出`() {
        val result = parseTransactionText("拼多多 店铺保证金 扣款 ¥3000.00")

        assertNotNull(result)
        assertEquals(300000L, result!!.amountMinor)
        assertEquals("expense-business", result.suggestedCategoryKey)
    }

    @Test
    fun `推广费归为经营支出`() {
        val result = parseTransactionText("千牛 直通车推广费 ¥568.80")

        assertNotNull(result)
        assertEquals(56880L, result!!.amountMinor)
        assertEquals("expense-business", result.suggestedCategoryKey)
    }

    @Test
    fun `物流快递费归为经营支出`() {
        val result = parseTransactionText("1688 快递代发 ¥45.00")

        assertNotNull(result)
        assertEquals(4500L, result!!.amountMinor)
        assertEquals("expense-business", result.suggestedCategoryKey)
    }

    @Test
    fun `货源代发采购款归为经营支出`() {
        val result = parseTransactionText("支付宝 1688 采购货款 ¥8600.00")

        assertNotNull(result)
        assertEquals(860000L, result!!.amountMinor)
        assertEquals("expense-business", result.suggestedCategoryKey)
    }

    @Test
    fun `店铺软件服务费归为经营支出`() {
        val result = parseTransactionText("千牛 生意参谋服务费 ¥398.00")

        assertNotNull(result)
        assertEquals(39800L, result!!.amountMinor)
        assertEquals("expense-business", result.suggestedCategoryKey)
    }

    @Test
    fun `仓储物流归为经营支出`() {
        val result = parseTransactionText("千牛 仓储物流 ¥120.00")

        assertNotNull(result)
        assertEquals(12000L, result!!.amountMinor)
        assertEquals("expense-business", result.suggestedCategoryKey)
    }

    // ---------- 店铺营收：不自动记 ----------

    @Test
    fun `客户订单付款营收不被自动识别`() {
        // 这是店铺收入，不是个人收支。自动记入会淹没生活账本。
        assertNull(
            "店铺营收不应自动入账",
            parseTransactionText("千牛 买家已付款 订单 ¥380.00"),
        )
    }

    @Test
    fun `收款到账提示不被自动识别`() {
        assertNull(
            parseTransactionText("拼多多 商家收款 ¥1280.00 已到账"),
        )
    }

    @Test
    fun `销售额结算提示不被自动识别`() {
        assertNull(
            parseTransactionText("千牛 货款结算到账 ¥5600.00"),
        )
    }

    @Test
    fun `微信商家收款不应自动入账`() {
        assertNull(
            parseTransactionText("微信支付 商家收款 ¥560.00"),
        )
    }

    @Test
    fun `对公转账收入不应自动入账`() {
        assertNull(
            parseTransactionText("千牛 货款转账 ¥12000.00"),
        )
    }

    // ---------- 个人购物：仍要照常记 ----------

    @Test
    fun `拼多多个人购物照常识别并归生活支出`() {
        val result = parseTransactionText("微信支付 拼多多 付款 ¥89.90")

        assertNotNull("个人购物必须继续识别", result)
        assertEquals(8990L, result!!.amountMinor)
        assertEquals(ParseDirection.EXPENSE, result.direction)
        assertEquals(
            "个人购物归生活支出，不能被当成经营",
            "expense-shopping", result.suggestedCategoryKey,
        )
    }

    @Test
    fun `淘宝买日用品照常识别`() {
        val result = parseTransactionText("支付宝 淘宝 付款成功 ¥45.80")

        assertNotNull(result)
        assertEquals(4580L, result!!.amountMinor)
        assertEquals("expense-shopping", result.suggestedCategoryKey)
    }

    @Test
    fun `生活类支出不受电商App影响`() {
        val result = parseTransactionText("微信支付 付款 ¥35.80 星巴克")

        assertNotNull(result)
        assertEquals(3580L, result!!.amountMinor)
        assertEquals("expense-food", result.suggestedCategoryKey)
    }

    @Test
    fun `工资等真实收入不受影响`() {
        val result = parseTransactionText("【招商银行】工资入账人民币8500.00元")

        assertNotNull(result)
        assertEquals(ParseDirection.INCOME, result!!.direction)
        assertEquals("工资应归工资分类", "income-salary", result.suggestedCategoryKey)
    }

    // ---------- 不能误伤普通消费 ----------

    @Test
    fun `普通消费不会因电商规则被误杀`() {
        val samples = listOf(
            "微信支付 付款 ¥12.50 使用优惠券",
            "微信支付 付款 ¥88.00 超市",
            "支付宝 付款 ¥25.00 便利店",
        )
        samples.forEach { sample ->
            assertNotNull("真实消费不应被误杀：$sample", parseTransactionText(sample))
        }
    }

    @Test
    fun `房租水电等居住支出不受影响`() {
        val result = parseTransactionText("微信支付 付款 ¥2800.00 房租")

        assertNotNull(result)
        assertEquals(280000L, result!!.amountMinor)
        assertEquals("expense-housing", result.suggestedCategoryKey)
    }

    @Test
    fun `经营规则词不能出现在普通消费里`() {
        // "寄快递"属生活消费，不应误判为经营支出
        val result = parseTransactionText("微信支付 付款 ¥23.00 寄快递")

        assertNotNull(result)
        org.junit.Assert.assertNotEquals(
            "寄快递属生活消费，不应归为经营支出",
            "expense-business", result!!.suggestedCategoryKey,
        )
    }
}