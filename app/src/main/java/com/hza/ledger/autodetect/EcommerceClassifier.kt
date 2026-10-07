package com.hza.ledger.autodetect

/**
 * 电商场景识别。
 *
 * 用户是电商从业者，手机里装着千牛工作台、拼多多商家版等App。
 * 这些 App 也会发支付通知，若不区分，会把经营流水混进生活账本。
 *
 * ## 为什么不能只靠包名过滤
 *
 * 千牛工作台（商家）里的付款可能是**进货成本**，也可能是**给客户发货的顺丰费**，
 * 两者都该记为经营支出；而**店铺收到的货款**是营收，不该记。
 * 同时用户在拼多多买衣服日用品属于个人生活消费，必须照常记。
 *
 * 同一个 App 里三种性质并存，所以必须**按文案性质判断，而非按 App 判断**。
 *
 * ## 双通道设计
 *
 * 1. [isBusinessPackage]：来源 App 是商家工具（千牛/商家版等）时提高经营判定倾向
 * 2. [BUSINESS_COST_KEYWORDS] / [REVENUE_KEYWORDS]：按文案语义精确分类
 *
 * 两条通道都命中经营语义才归入经营支出，避免「千牛里寄快递」这类生活支出被误判。
 */
object EcommerceClassifier {

    /**
     * 商家工作台类App 的包名。
     *
     * 这些 App 里的支付多为经营用途，但**不代表全部都是**——
     * 仍需文案判定，因为用户也可能在里面办生活缴费。
     */
    private val BUSINESS_PACKAGES = setOf(
        "com.taobao.qianniu",          // 千牛工作台（淘宝卖家）
        "com.pinduoduo.merchant",      // 拼多多商家版
        "com.xunmeng.pinduoduo",       // 拼多多
        "com.alibaba.android.rimet",   // 钉钉
        "com.1688.mobilebuyer",        // 1688 采购
        "com.youzan.merchant",         // 有赞商家版
        "com.alibaba.overseas",        // 国际站
    )

    /**
     * 采购/经营支出关键词。
     *
     * 只收**在电商语境下专属于经营**的词。
     * 不收「快递」这类词——它在个人生活里也很常见（寄快递），
     * 收了会把生活支出误判成经营。
     */
    val BUSINESS_COST_KEYWORDS = listOf(
        "进货", "采购", "货款", "拿货", "备货", "货源款",
        "保证金", "押金", "店铺保证金",
        "推广费", "直通车", "万相台", "关键词推广", "广告费", "推广服务费",
        "生意参谋", "生意经", "店小二", "技术服务费", "平台服务费",
        "仓储费", "仓储物流", "快递代发", "代发运费", "包装费",
        "店铺租金", "店铺费用", "平台扣点", "扣点手续费", "运营服务费",
        "样品费", "赠品费", "网商贷", "生意贷", "周转资金",
    )

    /**
     * 营收关键词 —— 收到钱、不是花钱。
     *
     * ⚠️ 收录标准：**必须含电商经营语境**。
     * 早期版本收了「收款到账」「入账通知」这类通用词，
     * 结果把「工资入账」等正常收入也误杀了。
     * 所以只用「买家/客户/商家/货款/销售款」这类主体明确的组合。
     */
    val REVENUE_KEYWORDS = listOf(
        "买家已付款", "买家付款", "客户付款", "客户已付款",
        "订单收款", "订单货款", "收到货款", "货款到账", "货款结算", "货款转账",
        "销售款", "结算到账", "商家收款", "店铺收款", "收款到账通知",
        "对公转账", "打款到账通知",
    )

    /** 来源是否为商家工具 App。 */
    fun isBusinessPackage(packageName: String): Boolean = packageName in BUSINESS_PACKAGES

    /**
     * 电商平台标识。
     *
     * 仅在来源为商家 App 时作为辅助判据 —— 个人 App 里出现这些词
     * 通常是购物而非经营，不能据此判定为经营支出。
     */
    private val PLATFORM_HINTS = listOf(
        "千牛", "商家", "店铺", "订单", "买家", "卖家", "货源", "旺旺",
    )

    /**
     * 是否为营收（收到钱）。
     *
     * 命中即**不应自动入账**：这是店铺营业收入，
     * 混进个人账本会彻底毁掉消费统计。用户明确选择了「不自动记」。
     */
    fun isRevenue(text: String): Boolean =
        REVENUE_KEYWORDS.any { text.contains(it) }

    /**
     * 判断该通知**是否应该被自动记账**。
     *
     * @param text 通知全文
     * @return false 表示不应入账（营收等），应直接丢弃
     */
    fun shouldAutoRecord(text: String): Boolean = !isRevenue(text)

    /**
     * 判断是否为经营支出。
     *
     * ⚠️ 调用前必须先确认 [shouldAutoRecord] 为 true。
     *
     * @param text 通知全文
     * @param packageName 通知来源包名，用于对商家 App 略微放宽判定
     */
    fun isBusinessCost(text: String, packageName: String?): Boolean {
        if (isRevenue(text)) return false
        if (containsBusinessSemantics(text)) return true

        // 商家 App 里的支出，若文案带了电商平台标识，也视为经营。
        // 注意「寄快递」这类生活词不在 BUSINESS_COST_KEYWORDS 里，不会误判。
        return packageName != null &&
            isBusinessPackage(packageName) &&
            containsPlatformHint(text)
    }

    private fun containsPlatformHint(text: String): Boolean =
        PLATFORM_HINTS.any { text.contains(it) }

    private fun containsBusinessSemantics(text: String): Boolean =
        BUSINESS_COST_KEYWORDS.any { text.contains(it) }
}