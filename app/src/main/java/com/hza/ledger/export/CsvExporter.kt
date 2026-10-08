package com.hza.ledger.export

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSV 导出的纯逻辑部分（无 Android 依赖，可完整单测）。
 *
 * ## 为什么不直接写 DAO 查询
 *
 * 把「格式化」与「查数据」分开，好处是：
 * - 精度与转义这些最容易出错的部分能用普通单测覆盖
 * - 不需要 Robolectric 或真机就能验证
 */
object CsvExporter {

    /**
     * 把「分」转成「元」字符串。
     *
     * ⚠️ 必须用 BigDecimal，用 Double 会出现
     * `12.34 → 0.12340000000000001` 这类误差。
     * 导出的金额和 App 里显示不一致是不可接受的错误。
     *
     * 用 `movePointLeft` 而非除法，避免精度损失；
     * 用 `toPlainString` 而非 `toString`，避免大额变成科学计数法
     * （`1E+8`被 Excel 打开会变成数字或报错）。
     */
    fun formatAmountMinor(amountMinor: Long): String =
        BigDecimal.valueOf(amountMinor, 2).toPlainString()

    /**
     * CSV 字段转义。
     *
     * 规则（RFC 4180）：
     * - 含逗号、引号、换行时必须用双引号包裹
     * - 字段内的双引号要写成两个双引号
     * - null 视作空字符串，不要输出字面量 "null"
     */
    fun escapeCsv(value: String?): String {
        val text = value ?: ""
        val needsQuote = text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuote) return text
        return "\"" + text.replace("\"", "\"\"") + "\""
    }

/**
 * UTF-8 BOM 的字节序列：EF BB BF。
 *
 * 为什么不直接写 `'\uFEFF'` 字符：
 * 源码文件里出现 BOM 字符会被 Lint 判为 ByteOrderMark 错误，
 * 且某些编辑器会把它当正常内容吞掉。
 * **必须以字节形式输出**，这样源码保持干净的 UTF-8。
 */
private val UTF8_BOM_BYTES = byteArrayOf(
    0xEF.toByte(),
    0xBB.toByte(),
    0xBF.toByte(),
)

/** BOM 字符，供测试断言用（由字节还原，等价于 U+FEFF）。 */
val UTF8_BOM_CHAR: Char = String(UTF8_BOM_BYTES, Charsets.UTF_8)[0]

/**
 * 组装完整 CSV 字节（含 BOM 与表头）。
 *
 * 即使没有数据也要输出表头 —— 否则用户会以为导出失败了。
 *
 * @return 带 BOM 前缀的 UTF-8 字节，可直接写文件
 */
fun buildCsvBytes(rows: List<ExportRow>): ByteArray {
    val builder = StringBuilder()
    builder.append(HEADER.joinToString(",") { escapeCsv(it) })
    builder.append("\r\n")
    rows.forEach { row ->
        builder.append(
            listOf(
                row.dateTime, row.type, row.amount, row.category,
                row.account, row.toAccount, row.merchant, row.note,
            ).joinToString(",") { escapeCsv(it) },
        )
        builder.append("\r\n")
    }
    return UTF8_BOM_BYTES + builder.toString().toByteArray(Charsets.UTF_8)
}

/**
 * 组装完整 CSV 文本（含 BOM 字符）。
 *
 * 仅供测试使用；实际写文件请用 [buildCsvBytes]，避免编码环节出岔子。
 */
fun buildCsv(rows: List<ExportRow>): String {
    val builder = StringBuilder()
    builder.append(UTF8_BOM_CHAR)
    builder.append(HEADER.joinToString(",") { escapeCsv(it) })
    builder.append("\r\n")
    rows.forEach { row ->
        builder.append(
            listOf(
                row.dateTime, row.type, row.amount, row.category,
                row.account, row.toAccount, row.merchant, row.note,
            ).joinToString(",") { escapeCsv(it) },
        )
        builder.append("\r\n")
    }
    return builder.toString()
}

    private val HEADER = listOf(
        "日期时间", "类型", "金额(元)", "分类", "账户", "转入账户", "商户", "备注",
    )
}

/** 导出的单行数据，全部是「已格式化」的文本。 */
data class ExportRow(
    val dateTime: String,
    val type: String,
    val amount: String,
    val category: String,
    val account: String,
    val toAccount: String,
    val merchant: String,
    val note: String,
) {
    companion object {
        private val DATE_TIME = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        private val DATE = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

        /** 由数据库实体构造导出行。 */
        fun from(
            type: String,
            amountMinor: Long,
            categoryName: String,
            accountName: String,
            toAccountName: String,
            merchant: String,
            note: String,
            occurredAt: Long,
        ): ExportRow = ExportRow(
            dateTime = DATE_TIME.format(Date(occurredAt)),
            type = type,
            amount = CsvExporter.formatAmountMinor(amountMinor),
            category = categoryName,
            account = accountName,
            toAccount = toAccountName,
            merchant = merchant,
            note = note,
        )
    }
}

/** 测试便捷别名。 */
internal fun formatAmountMinor(v: Long) = CsvExporter.formatAmountMinor(v)
internal fun escapeCsv(v: String?) = CsvExporter.escapeCsv(v)
internal fun buildCsv(rows: List<ExportRow>) = CsvExporter.buildCsv(rows)