package com.hza.ledger.update

import android.content.Context
import androidx.core.content.edit
import java.io.File

/**
 * 更新配置的本地存储。
 *
 * 存SharedPreferences，不进数据库——这样即使应用数据被清除，
 * 也只是回到"未配置"状态，不会与账本数据耦合。
 */
class UpdateSettingsStore(private val context: Context) {

    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 版本信息地址。
     *
     * 优先级：用户手动填写 > BuildConfig 内置地址。
     * 让用户可在应用内改，不必每次都重新打包。
     */
    fun getManifestUrl(): String =
        prefs.getString(KEY_MANIFEST_URL, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfigProvider.defaultManifestUrl

    fun setManifestUrl(url: String) {
        prefs.edit { putString(KEY_MANIFEST_URL, url.trim()) }
    }

    fun clearManifestUrl() {
        prefs.edit { remove(KEY_MANIFEST_URL) }
    }

    /** 是否已配置过自定义地址（用于界面提示）。 */
    fun hasCustomUrl(): Boolean = !prefs.getString(KEY_MANIFEST_URL, null).isNullOrBlank()

    /** 上次检查更新的时间戳，0表示从未检查过。 */
    var lastCheckAt: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_CHECK, value) }

    /** 上次检查结果摘要，用于启动时静默提示。 */
    var lastResult: String
        get() = prefs.getString(KEY_LAST_RESULT, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_LAST_RESULT, value) }

    /** 校验并规范化用户输入的 URL，非法则返回 null。 */
    fun normalizeUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "https://$trimmed"
        }
        return withScheme.takeIf {
            runCatching { java.net.URL(it) }.isSuccess
        }
    }

    companion object {
        private const val PREFS = "update_settings"
        private const val KEY_MANIFEST_URL = "manifest_url"
        private const val KEY_LAST_CHECK = "last_check_at"
        private const val KEY_LAST_RESULT = "last_result"
    }
}

/** 提供 BuildConfig 中的默认地址，避免在多处直接引用 BuildConfig。 */
object BuildConfigProvider {
    val defaultManifestUrl: String
        get() = com.hza.ledger.BuildConfig.UPDATE_MANIFEST_URL
}

/**
 * 读取当前安装的 versionCode。
 *
 * `getLongVersionCode` 需要 API 28，低版本需回退到已废弃的 `versionCode`。
 */
fun currentVersionCode(context: Context): Int = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        info.longVersionCode.toInt()
    } else {
        @Suppress("DEPRECATION")
        info.versionCode
    }
}.getOrDefault(0)

/** 读取当前安装的 versionName。 */
fun currentVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
}.getOrDefault("")

/** 清理已下载的更新包，避免缓存堆积。 */
fun clearDownloadedApk(context: Context) {
    runCatching {
        File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() }
    }
}
