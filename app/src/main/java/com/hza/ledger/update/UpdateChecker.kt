package com.hza.ledger.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.hza.ledger.autodetect.AppContextHolder
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 下载进度回调。[downloaded] 已下载字节，[total] 总字节（未知时为 -1）。 */
typealias ProgressCallback = (downloaded: Long, total: Long) -> Unit

/** 更新流程的结果。 */
sealed interface UpdateResult {
    data class Success(val manifest: UpdateManifest) : UpdateResult
    data class Failure(val reason: String) : UpdateResult
    data object NoUpdate : UpdateResult
}

/**
 * 检查更新。
 *
 * 只请求一个 JSON 文件，不上传任何设备信息，也不带任何标识参数。
 * 这是账本数据"不外传"承诺的关键 —— 网络权限仅用于读取公开的版本信息与下载 APK。
 */
suspend fun checkUpdate(
    manifestUrl: String,
    currentVersionCode: Int,
): UpdateResult = withContext(Dispatchers.IO) {
    if (manifestUrl.isBlank()) {
        return@withContext UpdateResult.Failure("未配置更新地址")
    }

    try {
        val connection = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                return@withContext UpdateResult.Failure("服务器返回 $code")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val manifest = UpdateManifest.parse(body)
                ?: return@withContext UpdateResult.Failure("版本信息格式无法解析")

            if (isNewerVersion(currentVersionCode, manifest)) {
                UpdateResult.Success(manifest)
            } else {
                UpdateResult.NoUpdate
            }
        } finally {
            connection.disconnect()
        }
    } catch (error: Exception) {
        UpdateResult.Failure(error.message ?: "网络异常")
    }
}

/**
 * 下载 APK。
 *
 * 下载到应用私有目录而非公共下载目录：不需要存储权限，也不污染用户空间。
 * 安装时通过 FileProvider 授权给系统安装器。
 */
suspend fun downloadApk(
    url: String,
    expectedSize: Long,
    onProgress: ProgressCallback = { _, _ -> },
): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
        val targetDir = File(AppContextHolder.context.cacheDir, "updates")
        if (!targetDir.exists()) targetDir.mkdirs()
        val target = File(targetDir, "update.apk")

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 20000
            requestMethod = "GET"
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                error("下载失败，服务器返回 $code")
            }

            // getContentLengthLong 需要 API 24，本项目 minSdk 23，需做版本判断
            val declaredLength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connection.contentLengthLong
            } else {
                connection.contentLength.toLong()
            }
            val total = declaredLength.takeIf { it > 0 } ?: expectedSize
            val temp = File(targetDir, "update.apk.part")

            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    copyWithProgress(input, output, total, onProgress)
                }
            }

            // 校验完整性：字节数与服务器声明不一致时丢弃，避免装出损坏的包
            if (expectedSize > 0 && temp.length() != expectedSize) {
                temp.delete()
                error("下载不完整：期望 ${expectedSize / 1024}KB，实际 ${temp.length() / 1024}KB")
            }

            if (temp.renameTo(target)) target else temp
        } finally {
            connection.disconnect()
        }
    }
}

/** 带进度回调的流拷贝。 */
private fun copyWithProgress(
    input: InputStream,
    output: OutputStream,
    total: Long,
    onProgress: ProgressCallback,
) {
    val buffer = ByteArray(64 * 1024)
    var downloaded = 0L
    var lastNotify = 0L

    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        output.write(buffer, 0, read)
        downloaded += read

        // 节流：每前进 256KB 才回调一次，避免过于频繁刷新 UI
        if (downloaded - lastNotify >= 256L * 1024L) {
            lastNotify = downloaded
            onProgress(downloaded, total)
        }
    }
    output.flush()
    onProgress(downloaded, total)
}

/** 计算文件 SHA-256，用于二次校验。 */
fun sha256(file: File): String? = runCatching {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}.getOrNull()

/**
 * 调起系统安装器。
 *
 * @return 是否成功调起；false 表示需要先开启"安装未知应用"权限
 */
fun launchInstaller(context: Context, apk: File): Boolean {
    val uri: Uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
    }.getOrElse {
        // 兜底：部分 ROM 不支持 FileProvider 时退回普通路径（现代 Android 均禁止）
        Uri.fromFile(apk)
    }

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    return runCatching { context.startActivity(intent); true }.getOrElse {
        // Android 8+ 需要用户先允许"安装未知应用"，引导到对应系统页
        runCatching {
            context.startActivity(
                Intent()
                    .setAction(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }
}

/** 检查是否允许安装未知来源应用（Android 8+ 必需）。 */
fun canRequestPackageInstalls(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.packageManager.canRequestPackageInstalls()
    } else {
        true
    }
