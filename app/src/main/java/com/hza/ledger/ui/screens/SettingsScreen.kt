package com.hza.ledger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Warning
import com.hza.ledger.autodetect.PendingNotifier
import com.hza.ledger.util.CrashReporter
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hza.ledger.ui.components.OneLedgerCard
import com.hza.ledger.ui.components.PressableSurface
import com.hza.ledger.ui.components.ScreenHeader
import com.hza.ledger.ui.components.SectionTitle
import com.hza.ledger.ui.theme.ExpenseCoral
import com.hza.ledger.ui.theme.IncomeMint
import com.hza.ledger.ui.theme.SavingsAmber
import com.hza.ledger.update.UpdateManifest
import com.hza.ledger.update.UpdateSettingsStore
import com.hza.ledger.update.canRequestPackageInstalls
import com.hza.ledger.update.checkUpdate
import com.hza.ledger.update.currentVersionCode
import com.hza.ledger.update.currentVersionName
import com.hza.ledger.update.downloadApk
import com.hza.ledger.update.formatApkSize
import com.hza.ledger.update.launchInstaller
import kotlinx.coroutines.launch

/**
 * 设置页：自动记账诊断 + 应用更新。
 *
 * 两个功能放在一起，因为它们都属于"应用自身的可维护性"，
 * 用户遇到问题时通常会先来这里找入口。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { UpdateSettingsStore(context) }

    val versionName = remember { currentVersionName(context) }
    val versionCode = remember { currentVersionCode(context) }

    var urlInput by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var statusText by remember { mutableStateOf("") }
    var available by remember { mutableStateOf<UpdateManifest?>(null) }

    val effectiveUrl = remember { store.getManifestUrl() }

    LaunchedEffect(effectiveUrl) {
        urlInput = if (store.hasCustomUrl()) effectiveUrl else ""
        if (effectiveUrl.isNotBlank() && store.lastCheckAt > 0) {
            statusText = store.lastResult
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        ScreenHeader(
            title = "设置",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            action = {
                PressableSurface(
                    onClick = onBack,
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 通知链路诊断：用户反馈"识别成功但不弹通知"时，
            // 没有这卡片就完全无从下手 —— 因为静默 return 点有4个。
            val notifyFailure = PendingNotifier.NotificationDiagnostics.lastFailure
            if (notifyFailure != null) {
                OneLedgerCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = ExpenseCoral,
                            )
                            Text(
                                "上次识别后未能发送通知",
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.titleMedium,
                                color = ExpenseCoral,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "原因：$notifyFailure",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "把这行字截图发给开发者即可定位。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 崩溃提示：闪退类问题必须有运行时信息，否则只能靠猜
            val crash = remember { CrashReporter.lastCrash() }
            if (crash != null) {
                OneLedgerCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = ExpenseCoral,
                            )
                            Text(
                                "上次打开时发生过闪退",
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.titleMedium,
                                color = ExpenseCoral,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            crash.message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "线程 ${crash.threadName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "截图这段信息发给开发者，能极大加快定位。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        SecondaryButton(
                            text = "我已记录，关闭提示",
                            onClick = { CrashReporter.clear() },
                        )
                    }
                }
            }

            // ---- 版本信息 ----
            OneLedgerCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("随手记", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "版本 $versionName（$versionCode）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Default.SystemUpdateAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- 自动记账诊断 ----
            SectionTitle("自动记账")
            OneLedgerCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("自检诊断", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "查看监听服务状态、最近收到的通知，并可粘贴文本当场测试解析能力",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(text = "打开自检诊断", onClick = onOpenDiagnostics)
                }
            }

            // ---- 应用更新 ----
            SectionTitle("应用更新")
            OneLedgerCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("检查更新", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "填入你的服务器地址后，应用会定期检查并提示新版本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("版本信息地址") },
                        placeholder = { Text("https://你的服务器/update.json") },
                        singleLine = true,
                    )
                    Text(
                        "服务器需提供一个 JSON 文件，格式见更新说明文档",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(
                            text = if (store.hasCustomUrl()) "清除地址" else "保存地址",
                            onClick = {
                                if (store.hasCustomUrl()) {
                                    store.clearManifestUrl()
                                    urlInput = ""
                                    statusText = "已清除，将使用内置地址"
                                } else {
                                    val normalized = store.normalizeUrl(urlInput)
                                    if (normalized == null) {
                                        statusText = "地址格式不正确"
                                    } else {
                                        store.setManifestUrl(normalized)
                                        statusText = "已保存"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton(
                            text = if (checking) "检查中" else "检查更新",
                            onClick = {
                                if (checking) return@PrimaryButton
                                checking = true
                                statusText = "正在检查…"
                                scope.launch {
                                    val url = store.getManifestUrl()
                                    if (url.isBlank()) {
                                        statusText = "请先填写版本信息地址"
                                        checking = false
                                        return@launch
                                    }
                                    val result = checkUpdate(url, versionCode)
                                    checking = false
                                    when (result) {
                                        is com.hza.ledger.update.UpdateResult.Success -> {
                                            available = result.manifest
                                            statusText =
                                                "发现新版本 ${result.manifest.versionName}"
                                        }
                                        is com.hza.ledger.update.UpdateResult.NoUpdate -> {
                                            available = null
                                            statusText = "当前已是最新版本"
                                        }
                                        is com.hza.ledger.update.UpdateResult.Failure -> {
                                            available = null
                                            statusText = "检查失败：${result.reason}"
                                        }
                                    }
                                    store.lastCheckAt = System.currentTimeMillis()
                                    store.lastResult = statusText
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !checking,
                        )
                    }

                    if (statusText.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                statusText.contains("失败") || statusText.contains("不正确") -> ExpenseCoral
                                statusText.contains("新版本") -> SavingsAmber
                                statusText.contains("最新") -> IncomeMint
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            // ---- 新版本详情与下载 ----
            available?.let { manifest ->
                OneLedgerCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            manifest.versionTitle.ifBlank { "新版本 ${manifest.versionName}" },
                            style = MaterialTheme.typography.titleMedium,
                            color = SavingsAmber,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            manifest.changelog,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "版本 ${manifest.versionName}　大小 ${formatApkSize(manifest.apkSize)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (downloading) {
                            Spacer(Modifier.height(14.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "下载中 ${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Spacer(Modifier.height(14.dp))
                            PrimaryButton(
                                text = "下载并安装",
                                onClick = {
                                    if (canRequestPackageInstalls(context)) {
                                        downloading = true
                                        progress = 0f
                                        scope.launch {
                                            val result = downloadApk(
                                                url = manifest.apkUrl,
                                                expectedSize = manifest.apkSize,
                                                onProgress = { done, total ->
                                                    progress = if (total > 0) {
                                                        (done.toFloat() / total).coerceIn(0f, 1f)
                                                    } else {
                                                        0f
                                                    }
                                                },
                                            )
                                            downloading = false
                                            result.onSuccess { file ->
                                                launchInstaller(context, file)
                                            }.onFailure { error ->
                                                statusText = "下载失败：${error.message}"
                                            }
                                        }
                                    } else {
                                        statusText = "请先允许「安装未知应用」"
                                        launchInstaller(context, java.io.File("/dev/null"))
                                    }
                                },
                                color = SavingsAmber,
                            )
                        }
                    }
                }
            }

            // ---- 隐私说明 ----
            SectionTitle("隐私")
            OneLedgerCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "应用已声明网络权限，但用途仅限两件事：\n" +
                            "· 读取上面配置的版本信息\n" +
                            "· 下载新版本安装包\n\n" +
                            "账本、账单、交易记录全部存在手机本地，不会被上传。" +
                            "检查更新的请求也不携带任何设备标识。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    PressableSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) color else color.copy(alpha = 0.4f),
        enabled = enabled,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(42.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PressableSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(42.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
