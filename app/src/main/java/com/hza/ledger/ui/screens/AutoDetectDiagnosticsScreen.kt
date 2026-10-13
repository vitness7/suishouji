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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import com.hza.ledger.autodetect.AutoDetectDiagnostics
import com.hza.ledger.autodetect.ListenerHealth
import com.hza.ledger.autodetect.NotificationDiagnostics
import com.hza.ledger.autodetect.hint
import com.hza.ledger.autodetect.listenerHealth
import com.hza.ledger.autodetect.PendingNotifier
import com.hza.ledger.autodetect.RecentNotification
import com.hza.ledger.autodetect.parseTransactionText
import com.hza.ledger.ui.components.OneLedgerCard
import com.hza.ledger.ui.components.PressableSurface
import com.hza.ledger.ui.components.ScreenHeader
import com.hza.ledger.autodetect.AppContextHolder
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import com.hza.ledger.ui.components.SectionTitle
import com.hza.ledger.ui.theme.ExpenseCoral
import com.hza.ledger.ui.theme.IncomeMint
import com.hza.ledger.ui.theme.SavingsAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 自动记账自检页。
 *
 * 存在的意义：自动识别失败时用户无法判断问题出在哪。
 * 本页把三件事摊开讲清楚：
 * 1. 监听服务是否真的连上（不只是"开关打开"）
 * 2. 最近收到了哪些通知、分别被解析成什么
 * 3. 手动粘贴一段文本，当场验证解析器能力
 */
@Composable
fun AutoDetectDiagnosticsScreen(
    notificationEnabled: Boolean,
    smsGranted: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var refreshKey by remember { mutableStateOf(0) }
    var testInput by remember { mutableStateOf("") }
    val entries = remember(refreshKey) { AutoDetectDiagnostics.recent() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        ScreenHeader(
            title = "自检诊断",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            action = {
                PressableSurface(
                    onClick = { AutoDetectDiagnostics.clear(); refreshKey++ },
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清空记录")
                    }
                }
            },
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { ChannelStatusCard(notificationEnabled, smsGranted) }

            // 通知链路状态：区分「没收到通知」与「收到了但没发出提醒」
            item { NotifyPipelineCard(refreshKey) }

            item {
                ParserTestCard(
                    input = testInput,
                    onInputChange = { testInput = it },
                )
            }

            item {
                SectionTitle(
                    "最近收到的通知",
                    trailing = "${entries.size} 条",
                )
            }

            if (entries.isEmpty()) {
                item {
                    OneLedgerCard(Modifier.fillMaxWidth()) {
                        Text(
                            "还没有记录。请确认通知使用权已开启，然后去微信或支付宝付一笔小额，\n" +
                                "回到这里下拉刷新即可看到结果。",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(entries.size) { index ->
                    DiagnosticRow(entries[index])
                }
            }
        }
    }
}

@Composable
private fun ChannelStatusCard(notificationEnabled: Boolean, smsGranted: Boolean) {
    // ⚠️ 「通知使用权」已授权 ≠「能发通知」。前者管读别人的通知，
    // 后者管本应用能否弹通知 —— Android 13+ 用户可以单独关掉后者。
    // 这里显式列出，否则用户会以为授权了就一定会有提醒。
    val canPostNotification = NotificationManagerCompat.from(AppContextHolder.context)
        .areNotificationsEnabled()
    val hasPostPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            AppContextHolder.context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    OneLedgerCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("通道状态", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            StatusRow(
                name = "通知监听",
                enabled = notificationEnabled,
                hint = if (notificationEnabled) "已授权" else "未开启，通知不会被读取",
            )
            Spacer(Modifier.height(10.dp))
            StatusRow(
                name = "发出提醒",
                enabled = canPostNotification && hasPostPermission,
                hint = when {
                    !hasPostPermission -> "缺少通知权限，无法弹提醒（Android 13+ 需单独授权）"
                    !canPostNotification -> "系统通知开关已关闭，请到系统设置里允许本应用发通知"
                    else -> "可以正常弹提醒"
                },
            )
            Spacer(Modifier.height(10.dp))
            StatusRow(
                name = "短信识别",
                enabled = smsGranted,
                hint = if (smsGranted) "已授权" else "未授权，银行短信无法识别",
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "提示：开关显示「已开启」只代表系统授权成功，" +
                    "能否真正识别还要看下方「最近收到的通知」里有没有记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 通知链路状态卡。
 *
 * ## 为什么要有这张卡
 *
 * 用户反馈「前几天还能用，今天忽然不弹了」这类问题时，
 * 光看「权限已开启」是没用的 —— 国产 ROM 会在权限仍显示开启的情况下
 * 静默杀掉监听服务。必须看**时间**才能发现真相：
 * 「上次收到通知：3 天前」比任何布尔标志都可靠。
 *
 * 卡片给出三样东西：
 * 1. 健康结论（一句话说明到底哪里有问题）
 * 2. 原始数据（收到多少条、上次什么时候）
 * 3. 测试按钮（让用户能主动验证通知功能本身是否正常）
 */
@Composable
private fun NotifyPipelineCard(refreshKey: Int) {
    val diag = remember(refreshKey) { NotificationDiagnostics }
    val health = remember(refreshKey) { listenerHealth() }
    val context = LocalContext.current
    var testResult by remember { mutableStateOf<String?>(null) }

    val stamp = remember(diag.lastSuccessAt) {
        if (diag.lastSuccessAt > 0L) {
            java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
                .format(java.util.Date(diag.lastSuccessAt))
        } else {
            null
        }
    }

    OneLedgerCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("通知链路", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))

            // ---- 健康结论：最重要的一行 ----
            val attentionColor = if (health.needsAttention) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            Text(
                health.hint(),
                style = MaterialTheme.typography.bodyMedium,
                color = attentionColor,
            )

            Spacer(Modifier.height(12.dp))

            // ---- 原始数据 ----
            Text(
                "累计收到：${diag.receivedCount} 条通知",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "上次收到通知：${diag.silentForText()}",
                style = MaterialTheme.typography.bodySmall,
                color = if (health == ListenerHealth.STALLED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "上次成功提醒：${stamp ?: "暂无记录"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (diag.lastFailure != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "上次失败原因：${diag.lastFailure}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(12.dp))

            // ---- 主动测试 ----
            DiagnosticActionButton(
                text = "发送测试通知",
                onClick = {
                    testResult = PendingNotifier.sendTestNotification(context)
                        ?: "已发送。若几秒内没看到通知，请检查系统通知设置。"
                },
            )

            testResult?.let { result ->
                Spacer(Modifier.height(8.dp))
                Text(
                    result,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "排查提示：「上次收到通知」比权限开关可靠。" +
                    "若显示已隔多日、而你这几天都在用手机，说明监听服务被系统停了，" +
                    "把通知使用权关掉再打开即可恢复。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusRow(name: String, enabled: Boolean, hint: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(30.dp)
                .background((if (enabled) IncomeMint else ExpenseCoral).copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (enabled) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (enabled) IncomeMint else ExpenseCoral,
            )
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ParserTestCard(input: String, onInputChange: (String) -> Unit) {
    OneLedgerCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("解析器测试", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "粘贴一段真实的支付通知或短信文本，当场看能不能解析出来",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("例：微信支付 付款给星巴克 ¥35.80", style = MaterialTheme.typography.bodyMedium)
                },
                minLines = 2,
                maxLines = 4,
            )
            if (input.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                val parsed = parseTransactionText(input)
                if (parsed == null) {
                    Text(
                        "未能识别为交易",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ExpenseCoral,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "如果这确实是一笔真实扣款，说明解析规则还没覆盖这种格式，" +
                            "请把原文发给开发者补充规则。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = IncomeMint,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "可识别　可信度 ${(parsed.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IncomeMint,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "金额 ¥${"%.2f".format(parsed.amountMinor / 100.0)}　" +
                            "方向 ${com.hza.ledger.autodetect.directionLabel(parsed.direction)}　" +
                            "渠道 ${parsed.channel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    parsed.merchant?.let {
                        Text(
                            "商户 $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(entry: RecentNotification) {
    val timeText = remember(entry.timeMillis) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.timeMillis))
    }
    val accent = when {
        entry.appLabel == "监听服务" && entry.parsed -> IncomeMint
        entry.appLabel == "监听服务" -> ExpenseCoral
        entry.parsed -> IncomeMint
        else -> SavingsAmber
    }

    OneLedgerCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(accent, CircleShape),
                )
                Text(
                    entry.appLabel,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                entry.result,
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
                fontWeight = FontWeight.Medium,
            )
            if (entry.rawText.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "原文：${entry.rawText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 诊断页的操作按钮，风格与整体保持一致。 */
@Composable
private fun DiagnosticActionButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text)
    }
}
