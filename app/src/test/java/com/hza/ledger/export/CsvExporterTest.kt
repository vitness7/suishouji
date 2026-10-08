package com.hza.ledger.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.7.0 CSV 导出测试。
 *
 * ## 为什么导出比看起来难
 *
 * 内部金额存「分」（Long），导出要转「元」。
 * 若用 Double 会出问题：
 * ```
 * 12.34.toDouble() / 100 = 0.12340000000000001
 * 8.24.toDouble()  / 100 = 0.082400000000000004
 * ```
 * 导出的账目金额与 App 内显示不一致 —— 这是记账App 最不可接受的错误。
 * 必须用 BigDecimal。
 *
 * ## 另一个坑：Excel 打开中文乱码
 *
 * CSV 默认是 UTF-8 无BOM，Excel 会按本地编码解析，
 * 中文全变乱码。必须写 **UTF-8 with BOM**。
 */
class CsvExporterTest {

    // ---------- 金额精度（最关键） ----------

    @Test
    fun `金额从分转元必须精确`() {
        assertEquals("12.34", formatAmountMinor(1234))
        assertEquals("8.24", formatAmountMinor(824))
        assertEquals("0.01", formatAmountMinor(1))
        assertEquals("0.05", formatAmountMinor(5))
        assertEquals("100.00", formatAmountMinor(10000))
    }

    @Test
    fun `Double 会出的问题不能出现`() {
        // 这几个值在 Double 下会出错，必须用 BigDecimal 才能正确
        val cases = listOf(1234L, 824L, 5L, 1L, 587L, 1290L, 99999L, 1000000L)
        cases.forEach { minor ->
            val expected = java.math.BigDecimal.valueOf(minor, 2).toPlainString()
            assertEquals("minor=$minor", expected, formatAmountMinor(minor))
        }
    }

    @Test
    fun `金额必须始终保留两位小数`() {
        // Excel 靠小数位对齐，100 应该显示 100.00 而不是 100
        assertEquals("100.00", formatAmountMinor(10000))
        assertEquals("0.00", formatAmountMinor(0))
        assertEquals("1.50", formatAmountMinor(150))
    }

    @Test
    fun `大额金额不能出现科学计数法`() {
        // toString() 会给出 "1E+8"，Excel 打开会变成数字或报错
        assertEquals("100000.00", formatAmountMinor(10_000_000L))
        assertTrue(
            "不得包含科学计数法",
            !formatAmountMinor(1_000_000_000L).contains("E"),
        )
    }

    @Test
    fun `负数金额正确处理`() {
        assertEquals("-12.34", formatAmountMinor(-1234))
        assertEquals("0.00", formatAmountMinor(0))
    }

    // ---------- CSV 转义 ----------

    @Test
    fun `字段含逗号时必须加引号`() {
        assertEquals("\"星巴克, 北京店\"", escapeCsv("星巴克, 北京店"))
    }

    @Test
    fun `字段含换行时必须加引号`() {
        assertEquals("\"第一行\n第二行\"", escapeCsv("第一行\n第二行"))
    }

    @Test
    fun `字段含双引号时必须转义为两个双引号`() {
        // ⚠️ CSV 规范：双引号要写成两个双引号，否则会破坏结构
        assertEquals("\"他说\"\"你好\"\"\"", escapeCsv("他说\"你好\""))
    }

    @Test
    fun `空字段应为空字符串而非 null 字面量`() {
        assertEquals("", escapeCsv(""))
        assertEquals("", escapeCsv(null))
    }

    @Test
    fun `普通字段不加多余引号`() {
        assertEquals("星巴克", escapeCsv("星巴克"))
        assertEquals("餐饮", escapeCsv("餐饮"))
    }

    @Test
    fun `字段前后空格保留`() {
        // 用户备注里可能有意义的空格，不该被裁掉
        assertEquals(" 备注 ", escapeCsv(" 备注 "))
    }

    // ---------- BOM（Excel 中文乱码的解法） ----------

    @Test
    fun `文件必须以 UTF8 BOM 开头`() {
        // ⚠️ 对字节断言，不是对字符 ——
        // Lint 不允许源码里出现 BOM 字符（ByteOrderMark），所以实现用字节输出。
        val bytes = CsvExporter.buildCsvBytes(
            listOf(ExportRow("2026-10-08", "支出", formatAmountMinor(824), "餐饮", "招行", "", "", ""))
        )
        assertEquals(
            "缺少 BOM 会导致 Excel 打开中文乱码",
            listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            listOf(bytes[0], bytes[1], bytes[2]),
        )
    }

    @Test
    fun `BOM 后紧跟表头第一个字符`() {
        val bytes = CsvExporter.buildCsvBytes(emptyList())
        // BOM 是 3 字节，第 4 个字节应是表头首字的中文首字节
        assertEquals(
            "BOM 之后应是表头内容",
            '日'.toString().toByteArray(Charsets.UTF_8)[0],
            bytes[3],
        )
    }

    @Test
    fun `导出字节应为合法 UTF8`() {
        val text = "餐饮,星巴克"
        val bytes = CsvExporter.buildCsvBytes(
            listOf(ExportRow("2026-10-08", "支出", "8.24", text, "招行", "", "", ""))
        )
        val decoded = String(bytes, Charsets.UTF_8)
        assertTrue("内容应可解码为 UTF-8", decoded.contains(text))
    }

    @Test
    fun `表头包含关键列且为中文`() {
        val csv = buildCsv(emptyList())
        assertTrue("应含日期", csv.contains("日期"))
        assertTrue("应含金额", csv.contains("金额"))
        assertTrue("应含分类", csv.contains("分类"))
        assertTrue("应含账户", csv.contains("账户"))
    }

    // ---------- 行构造 ----------

    @Test
    fun `导出行字段顺序固定`() {
        val row = ExportRow(
            dateTime = "2026-10-08 12:30",
            type = "支出",
            amount = "8.24",
            category = "餐饮",
            account = "招行储蓄卡",
            toAccount = "",
            merchant = "星巴克",
            note = "",
        )
        val lines = buildCsv(listOf(row)).lines().filter { it.isNotBlank() }
        // 第二行是数据行
        assertEquals(2, lines.size)
        assertTrue(lines[1].startsWith("2026-10-08 12:30"))
        assertTrue(lines[1].contains("8.24"))
    }

    @Test
    fun `空数据也要输出表头`() {
        // 没有账目时也要生成只有表头的文件，否则用户以为导出失败
        val csv = buildCsv(emptyList())
        val lines = csv.lines().filter { it.isNotBlank() }
        assertEquals("应只有表头一行", 1, lines.size)
    }
}