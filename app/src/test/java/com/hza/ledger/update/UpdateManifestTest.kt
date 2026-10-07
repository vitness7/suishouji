package com.hza.ledger.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {

    private val validJson = """
        {
          "versionCode": 3,
          "versionName": "1.2.0",
          "versionTitle": "修复自动识别失效",
          "changelog": "1. 修复微信支付通知无法识别\n2. 新增自检诊断页",
          "apkUrl": "https://example.com/app.apk",
          "apkSize": 20971520,
          "publishedAt": "2026-10-07T10:00:00Z",
          "mandatory": false
        }
    """.trimIndent()

    @Test
    fun `能解析完整版本信息`() {
        val manifest = UpdateManifest.parse(validJson)

        assertNotNull(manifest)
        assertEquals(3, manifest!!.versionCode)
        assertEquals("1.2.0", manifest.versionName)
        assertEquals("修复自动识别失效", manifest.versionTitle)
        assertEquals("https://example.com/app.apk", manifest.apkUrl)
        assertEquals(20971520L, manifest.apkSize)
    }

    @Test
    fun `变更日志中的换行符被正确还原`() {
        val manifest = UpdateManifest.parse(validJson)

        assertTrue(
            "换行应被还原为真实换行",
            manifest!!.changelog.contains("\n"),
        )
        assertTrue(manifest.changelog.contains("修复微信支付通知无法识别"))
    }

    @Test
    fun `缺少apkUrl 时判定为无效`() {
        val json = """{"versionCode": 3, "versionName": "1.2.0"}"""

        assertNull("缺少下载地址的版本信息不可用", UpdateManifest.parse(json))
    }

    @Test
    fun `缺少 versionCode 时判定为无效`() {
        val json = """{"versionName": "1.2.0", "apkUrl": "https://example.com/app.apk"}"""

        assertNull(UpdateManifest.parse(json))
    }

    @Test
    fun `空字符串与畸形JSON 安全返回空`() {
        assertNull(UpdateManifest.parse(""))
        assertNull(UpdateManifest.parse("   "))
        assertNull(UpdateManifest.parse("not json at all"))
        assertNull(UpdateManifest.parse("{broken"))
    }

    @Test
    fun `可选字段缺失时使用默认值`() {
        val json = """{"versionCode": 5, "apkUrl": "https://example.com/a.apk"}"""

        val manifest = UpdateManifest.parse(json)

        assertNotNull(manifest)
        assertEquals("", manifest!!.versionName)
        assertEquals(0L, manifest.apkSize)
        assertFalse(manifest.mandatory)
    }

    @Test
    fun `mandatory 字段能被正确解析`() {
        val json = """
            {"versionCode": 9, "apkUrl": "https://e.com/a.apk", "mandatory": true}
        """.trimIndent()

        assertTrue(UpdateManifest.parse(json)!!.mandatory)
    }

    // ---------- 版本比较 ----------

    @Test
    fun `更高版本号被判定为更新`() {
        val manifest = UpdateManifest.parse(validJson)!!

        assertTrue(isNewerVersion(currentVersionCode = 2, manifest = manifest))
    }

    @Test
    fun `相同版本号不是更新`() {
        val manifest = UpdateManifest.parse(validJson)!!

        assertFalse(isNewerVersion(currentVersionCode = 3, manifest = manifest))
    }

    @Test
    fun `更低版本号不是更新（防降级）`() {
        val manifest = UpdateManifest.parse(validJson)!!

        assertFalse("服务器误配低版本号时不应提示更新", isNewerVersion(currentVersionCode = 5, manifest = manifest))
    }

    @Test
    fun `低版本号的大版本不会被误判`() {
        // 回归测试：确认比较用的是 versionCode 而非字符串版本号
        // "1.10.0" 字符串比较会小于 "1.9.0"，但版本号 10 > 9
        val json = """
            {"versionCode": 10, "versionName": "1.10.0", "apkUrl": "https://e.com/a.apk"}
        """.trimIndent()

        assertTrue(isNewerVersion(9, UpdateManifest.parse(json)!!))
    }

    // ---------- 大小格式化 ----------

    @Test
    fun `文件大小格式化正确`() {
        assertEquals("未知大小", formatApkSize(0L))
        // 512KB 应显示 512，不能四舍五入成 500（用户要看到真实体积）
        assertEquals("512 KB", formatApkSize(512L * 1024L))
        assertEquals("1 KB", formatApkSize(1024L))
        assertEquals("1.5 MB", formatApkSize(1536L * 1024L))
        assertEquals("20.0 MB", formatApkSize(20971520L))
    }
}
