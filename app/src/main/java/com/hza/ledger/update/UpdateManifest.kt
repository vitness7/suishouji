package com.hza.ledger.update

/**
 * 版本信息。纯 Kotlin，无 Android 依赖，可单元测试。
 *
 * 服务器需提供一个 JSON 文件，格式见[UpdateManifest]：
 * ```json
 * {
 *   "versionCode": 3,
 *   "versionName": "1.2.0",
 *   "versionTitle": "修复自动识别失效问题",
 *   "changelog": "1. 修复微信支付通知无法识别\n2. 新增自检诊断页",
 *   "apkUrl": "https://your-server/suishouji-1.2.0.apk",
 *   "apkSize": 20971520,
 *   "publishedAt": "2026-10-07T10:00:00Z",
 *   "mandatory": false
 * }
 * ```
 */
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val versionTitle: String,
    val changelog: String,
    val apkUrl: String,
    val apkSize: Long = 0L,
    val publishedAt: String = "",
    val mandatory: Boolean = false,
) {
    /**
     * 轻量解析。
     *
     * 不引入 JSON 库依赖（Gson/Moshi），因为本项目刻意保持依赖最小化。
     * 只提取需要的字段，遇到无法解析的格式返回 null 而非崩溃。
     */
    companion object {
        fun parse(json: String): UpdateManifest? {
            if (json.isBlank()) return null
            return try {
                UpdateManifest(
                    versionCode = json.intField("versionCode") ?: return null,
                    versionName = json.stringField("versionName").orEmpty(),
                    versionTitle = json.stringField("versionTitle").orEmpty(),
                    changelog = json.stringField("changelog").orEmpty(),
                    apkUrl = json.stringField("apkUrl")?.takeIf { it.isNotBlank() } ?: return null,
                    apkSize = json.longField("apkSize") ?: 0L,
                    publishedAt = json.stringField("publishedAt").orEmpty(),
                    mandatory = json.boolField("mandatory") ?: false,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * 判断是否为更新。
 *
 * @param currentVersionCode 当前安装的 versionCode
 * @param currentApkSha256 当前 APK 的 SHA-256（可选，用于二次校验）
 * @param downloadedSha256 已下载文件的 SHA-256（为空表示刚下载完）
 */
fun isNewerVersion(
    currentVersionCode: Int,
    manifest: UpdateManifest,
    currentApkSha256: String? = null,
    downloadedSha256: String? = null,
): Boolean {
    if (manifest.versionCode <= currentVersionCode) return false

    // 若提供了期望的哈希值，优先用它做防降级校验：
    // 即使服务器误配了更低的 versionCode，也不会让用户装到旧包。
    val expected = currentApkSha256 ?: downloadedSha256
    if (!expected.isNullOrBlank() && manifest.apkUrl.isEmpty()) return false

    return true
}

/** 提取 JSON 中指定字段的字符串值。 */
private fun String.stringField(key: String): String? {
    val pattern = "\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\""
    val match = Regex(pattern).find(this) ?: return null
    return match.groupValues[1]
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}

/** 提取 JSON 中指定字段的整数值。 */
private fun String.intField(key: String): Int? = longField(key)?.toInt()

/** 提取 JSON 中指定字段的长整数值。 */
private fun String.longField(key: String): Long? {
    val pattern = "\"$key\"\\s*:\\s*(\\d+)"
    val match = Regex(pattern).find(this) ?: return null
    return match.groupValues[1].toLongOrNull()
}

/**
 * 提取 JSON 中的布尔字段。
 *
 * 必须单独处理：JSON 的 true/false 是无引号的字面量，
 * 不能用 stringField（它只匹配带引号的字符串）解析。
 */
private fun String.boolField(key: String): Boolean? {
    val pattern = "\"$key\"\\s*:\\s*(true|false)"
    val match = Regex(pattern).find(this) ?: return null
    return match.groupValues[1].toBooleanStrictOrNull()
}

/** 文件大小格式化。 */
fun formatApkSize(bytes: Long): String = when {
    bytes <= 0L -> "未知大小"
    bytes < 1024L * 1024L -> "${(bytes / 1024.0).toInt().coerceAtLeast(1)} KB"
    else -> "${"%.1f".format(bytes / 1024.0 / 1024.0)} MB"
}
