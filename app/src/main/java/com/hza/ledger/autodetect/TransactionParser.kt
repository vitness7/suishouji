package com.hza.ledger.autodetect

/**
 * 自动记账解析引擎。纯 Kotlin，无 Android 依赖，可完整单元测试。
 *
 * 设计原则：宁可解析不出，也不猜错。解析失败返回 null，由上层决定跳过。
 */

/** 解析出的交易方向。 */
enum class ParseDirection { EXPENSE, INCOME, UNKNOWN }

/** 支付渠道。 */
enum class ParseChannel {
    WECHAT,
    ALIPAY,
    BANK_CARD,
    CREDIT_CARD,
    UNKNOWN,
}

/** 解析结果。金额单位统一为「分」，与数据库 amountMinor 对齐。 */
data class ParsedTransaction(
    val amountMinor: Long,
    val direction: ParseDirection,
    val channel: ParseChannel,
    val merchant: String?,
    val suggestedCategoryKey: String?,
    val confidence: Float,
    val rawText: String,
)

/**
 * 关键词规则表。按顺序匹配，命中即返回。
 *
 * 每条规则包含：方向、渠道、推荐分类、关键词。
 * 关键词用小写匹配，中文不受影响，英文需小写。
 */
private data class Rule(
    val direction: ParseDirection,
    val channel: ParseChannel,
    val categoryKey: String?,
    val keywords: List<String>,
)

private fun rule(
    direction: ParseDirection,
    channel: ParseChannel,
    categoryKey: String?,
    vararg keywords: String,
) = Rule(direction, channel, categoryKey, keywords.toList())

private val RULES: List<Rule> = listOf(
    // 明确的收入
    rule(ParseDirection.INCOME, ParseChannel.ALIPAY, "income-other", "收款", "收到款", "已收款", "转入"),
    rule(ParseDirection.INCOME, ParseChannel.WECHAT, "income-other", "微信支付收款", "收款到"),
    rule(ParseDirection.INCOME, ParseChannel.BANK_CARD, "income-salary", "工资", "入账", "存款利息"),
    rule(ParseDirection.INCOME, ParseChannel.CREDIT_CARD, "income-other", "额度返还", "退款到"),
    rule(ParseDirection.INCOME, ParseChannel.BANK_CARD, "income-other", "退款", "退回"),

    // 明确的支出（渠道判定）
    // 分类一律留null：这些通用词无法代表真实用途。
    //
    // ⚠️ 顺序陷阱：「支付成功/付款成功」是通用词，微信和支付宝都会用。
    // 必须把具体渠道名（微信支付/支付宝）放在通用词之前，
    // 否则支付宝通知会被微信规则抢先命中，导致渠道误判。
    rule(
        ParseDirection.EXPENSE,
        ParseChannel.WECHAT,
        null,
        "微信支付", "微信收款支付", "微信支付凭证", "扫码支付", "商户消费",
    ),
    rule(
        ParseDirection.EXPENSE,
        ParseChannel.ALIPAY,
        null,
        "支付宝", "花呗", "余额宝", "蚂蚁森林", "消费红包",
    ),
    rule(ParseDirection.EXPENSE, ParseChannel.CREDIT_CARD, null, "信用卡", "消费信用卡", "账单分期"),
    // 通用交易动词放在最后：只在没有更具体的渠道线索时才生效
    rule(
        ParseDirection.EXPENSE,
        ParseChannel.BANK_CARD,
        null,
        "支付成功", "付款成功", "已付款", "完成支付", "快捷支付", "网银",
        "银行扣款", "消费支出", "扣款", "消费", "支付", "转账", "取现", "扣费",
        "代扣", "预授权", "支付方式",
    ),

    // 分类建议：先匹配"用途明确"的规则（如加油站、房租），
    // 这些比通用的"消费/支付"更能代表真实分类，因此排在前面。
    // key 均为 DefaultCategoryCatalog 中的真实分类 ID。
    rule(ParseDirection.UNKNOWN, ParseChannel.UNKNOWN, "expense-housing", "房租", "物业费", "水费", "电费", "燃气费", "宽带费"),
    rule(ParseDirection.UNKNOWN, ParseChannel.UNKNOWN, "expense-transport", "加油", "加油站", "停车费", "高速通行", "代扣车费", "打车", "滴滴", "地铁", "公交", "高铁", "机票"),

    // 按商户关键词猜分类（方向未定，仅供分类建议）
    rule(ParseDirection.UNKNOWN, ParseChannel.UNKNOWN, "expense-food", "餐", "food", "外卖", "餐厅", "饭店", "咖啡", "奶茶", "便利店", "火锅", "烧烤", "快餐", "小吃", "面馆", "食堂", "烘焙", "面包", "蛋糕", "茶饮", "酒楼"),
    rule(ParseDirection.UNKNOWN, ParseChannel.UNKNOWN, "expense-shopping", "购物", "商城", "淘宝", "京东", "拼多多", "服饰", "数码"),
    rule(ParseDirection.UNKNOWN, ParseChannel.UNKNOWN, "expense-housing", "房租", "物业", "水电", "燃气", "宽带"),
    rule(ParseDirection.UNKNOWN, ParseChannel.UNKNOWN, "expense-health", "医院", "药房", "药店", "门诊", "体检"),
    rule(ParseDirection.UNKNOWN, ParseChannel.UNKNOWN, "expense-fun", "电影", "影院", "游戏", "会员", "健身", "ktv"),
    rule(ParseDirection.UNKNOWN, ParseChannel.UNKNOWN, "expense-study", "学费", "培训", "书店", "图书"),
    rule(ParseDirection.UNKNOWN, ParseChannel.UNKNOWN, "expense-daily", "超市", "生鲜", "菜场", "水果", "代扣"),
)

/**
 * 明确的营销/无关文本特征。命中任一则直接放弃解析。
 *
 * ⚠️ 收录标准：**只在没有支付语义时才会误判的词**才能进这里。
 * 「券」「活动」「红包」这类词在真实消费通知里极常见
 * （如"支付成功 ¥12.50 使用优惠券"），若拉黑会导致大量真实支出被误杀。
 * 因此只保留那些几乎不可能出现在付款通知里的强营销词。
 */
private val REJECT_PATTERNS = listOf(
    "营销", "广告", "点击查看", "点开", "验证码", "登录", "注册",
    "安全提醒", "风险提醒", "抽奖", "秒杀", "拼团", "砍价", "签到",
    "推荐", "热门", "限时", "邀请好友",
)

/**
 * 金额候选。
 *
 * 真实短信里干扰项极多：卡号尾号1234、日期8月6日、订单号、余额数字。
 * 因此要求金额必须带货币符号或"元"字，纯数字一律不接受——
 * 宁可解析不出，也不能把卡号当金额。
 */
private val AMOUNT_WITH_SYMBOL = Regex("""[¥￥]\s*(\d+(?:\.\d{1,2})?)""")

/** 带"元"字的金额，允许前置货币符号与千分位。 */
private val AMOUNT_WITH_YUAN = Regex("""(?:[¥￥]|人民币)?\s*(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+\.\d{1,2}|\d+)\s*元""")

/** 纯数字（无符号无"元"）——仅在没有更好候选时使用。 */
private val AMOUNT_BARE = Regex("""(\d+(?:\.\d{1,2})?)""")

/** 卡号/订单号等超长数字，绝不能当金额。 */
private val LONG_DIGITS = Regex("""\d{7,}""")

/** 日期中的日/月，用于排除 "8月6日" 这类干扰。 */
private val DATE_NOISE = Regex("""\d{1,2}\s*月\s*\d{1,2}\s*日""")

/** 常见的"尾号1234"表述，紧跟其后的数字需排除。 */
private val CARD_TAIL_NOISE = Regex("""(?:尾号|卡号|账号|订单号?|流水号)\s*[:：]?\s*\d+""")

/**
 * 纯数字兜底：要求文本中已出现货币符号，否则卡号等数字极易被误当金额。
 *
 * 例："微信支付 付款 35.80" 这类无符号写法必须同时出现 ¥ / ￥ 才采信。
 */
private val SYMBOL_PRESENT = Regex("""[¥￥]""")

/**
 * 解析通知或短信文本。
 *
 * @param text 原始文本（通知标题+内容或短信正文）
 * @return 解析成功返回结果，无法可靠解析返回 null
 */
fun parseTransactionText(text: String): ParsedTransaction? {
    val normalized = text.trim()
    if (normalized.isEmpty()) return null

    val lower = normalized.lowercase()

    // 1. 营销文本直接拒绝
    if (REJECT_PATTERNS.any { lower.contains(it) }) return null

    // 2. 提取金额
    val amountMinor = extractAmountMinor(normalized)

    // 3. 规则匹配。
    //    关键：channel 按「规则越靠前优先级越高」判定，而不是先到先得。
    //    这样具体渠道名（微信支付）永远不会被后面的通用词（支付成功）覆盖。
    var direction = ParseDirection.UNKNOWN
    var channel = ParseChannel.UNKNOWN
    var channelPriority = Int.MAX_VALUE
    var categoryKey: String? = null
    var categoryPriority = Int.MAX_VALUE
    var matched = false

    RULES.forEachIndexed { priority, rule ->
        if (!rule.keywords.any { lower.contains(it.lowercase()) }) return@forEachIndexed
        matched = true

        if (rule.direction != ParseDirection.UNKNOWN && direction == ParseDirection.UNKNOWN) {
            direction = rule.direction
        }
        if (rule.channel != ParseChannel.UNKNOWN && priority < channelPriority) {
            channel = rule.channel
            channelPriority = priority
        }
        if (rule.categoryKey != null && priority < categoryPriority) {
            categoryKey = rule.categoryKey
            categoryPriority = priority
        }
    }

    // 4. 完全没有交易语义特征 -> 放弃
    if (!matched) return null

    // 5. 无金额同样放弃
    val amount = amountMinor ?: return null

    // 6. 置信度评估
    val confidence = scoreConfidence(amount, direction, channel, categoryKey, normalized)

    return ParsedTransaction(
        amountMinor = amount,
        direction = direction,
        channel = channel,
        merchant = extractMerchant(normalized),
        suggestedCategoryKey = categoryKey,
        confidence = confidence,
        rawText = normalized,
    )
}

/**
 * 从文本中提取金额并转换为「分」。
 *
 * 策略：先剥离卡号/日期等干扰，再按可靠性依次尝试
 * 货币符号 > "元"字 > 纯数字。
 *
 * 用 BigDecimal 而非 Double，避免 12.34 * 100 = 1233.9999 这类精度问题。
 */
private fun extractAmountMinor(text: String): Long? {
    // 剥离干扰：卡号、订单号、日期
    val cleaned = CARD_TAIL_NOISE.replace(text, " ")
        .replace(DATE_NOISE, " ")
        .replace(LONG_DIGITS, " ")

    // 1. 带货币符号最可信
    AMOUNT_WITH_SYMBOL.find(cleaned)?.let { return toMinor(it.groupValues[1]) }

    // 2. 带"元"字次之
    AMOUNT_WITH_YUAN.find(cleaned)?.let { return toMinor(it.groupValues[1]) }

    // 3. 纯数字：仅在文本已出现货币符号时才可信，
    //    否则「尾号 1234」这类数字会被误当金额。
    if (SYMBOL_PRESENT.containsMatchIn(cleaned)) {
        AMOUNT_BARE.find(cleaned)?.let { return toMinor(it.groupValues[1]) }
    }

    return null
}

private fun toMinor(raw: String): Long? {
    val cleaned = raw.replace(",", "").trim()
    if (cleaned.isEmpty()) return null
    return try {
        val value = java.math.BigDecimal(cleaned)
        if (value.signum() <= 0) return null
        value.movePointRight(2).longValueExact()
    } catch (_: ArithmeticException) {
        null
    } catch (_: NumberFormatException) {
        null
    }
}

/**
 * 置信度评分。低于 [MIN_CONFIDENCE] 的结果建议不入待确认列表。
 *
 * 只根据「有多少可信信号」打分，不做主观加权。
 */
private fun scoreConfidence(
    amountMinor: Long,
    direction: ParseDirection,
    channel: ParseChannel,
    categoryKey: String?,
    text: String,
): Float {
    var score = 0f

    // 方向明确是最强信号
    if (direction != ParseDirection.UNKNOWN) score += 0.45f
    // 渠道明确
    if (channel != ParseChannel.UNKNOWN) score += 0.2f
    // 分类推断成功
    if (categoryKey != null) score += 0.1f
    // 带货币符号或"元"字，说明金额表达规范
    if (text.contains("¥") || text.contains("￥") || text.contains("元")) score += 0.1f
    // 金额合理区间，避免解析到明显异常的大数
    if (amountMinor in 1..100_000_000L) score += 0.1f
    // 文本过长可能混入噪声
    if (text.length > 200) score -= 0.15f

    return score.coerceIn(0f, 1f)
}

/** 建议入库的最低置信度。低于此值不展示给用户，避免干扰。 */
const val MIN_CONFIDENCE = 0.45f

/**
 * 尝试提取商户名。
 *
 * 常见格式："商户名"、"向XXX付款"、"在XXX消费"。
 */
private val MERCHANT_PATTERNS = listOf(
    Regex("""向(.+?)(?:付款|支付|转账)"""),
    Regex("""在(.+?)(?:消费|支付|付款)"""),
    Regex("""到(.+?)(?:消费|支付)"""),
    Regex("""商户[：:]\s*(.+)"""),
)

private fun extractMerchant(text: String): String? {
    for (pattern in MERCHANT_PATTERNS) {
        val match = pattern.find(text) ?: continue
        val candidate = match.groupValues[1].trim()
        if (candidate.isNotEmpty() && candidate.length <= 30) return candidate
    }
    return null
}
