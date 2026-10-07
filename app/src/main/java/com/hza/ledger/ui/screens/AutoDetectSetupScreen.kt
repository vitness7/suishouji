package com.hza.ledger.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hza.ledger.ui.components.OneLedgerCard
import com.hza.ledger.ui.components.PressableSurface
import com.hza.ledger.ui.components.ScreenHeader
import com.hza.ledger.ui.theme.IncomeMint
import com.hza.ledger.ui.theme.SavingsAmber

/**
 * 自动记账权限引导。
 *
 * 两项权限都是「用户主动授权」而非「弹窗索要」：
 * 通知使用权需去系统设置开启，短信权限需运行时同意。
 * 这里把入口和说明放在一处，避免用户在系统设置里找不到地方。
 */
@Composable
fun AutoDetectSetupScreen(
    notificationEnabled: Boolean,
    smsGranted: Boolean,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        ScreenHeader(
            title = "自动记账",
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
            OneLedgerCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("开启后能做什么", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "微信、支付宝、银行的支付提示会自动读取金额，生成待确认记录。" +
                            "你点「确认」才会计入账本，识别不准可以忽略。\n\n" +
                            "全程在本机解析，不联网、不上传，应用也没有网络权限。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            PermissionCard(
                title = "通知使用权",
                description = "读取微信、支付宝、银行 App 的支付通知。这是识别的主要来源。\n\n" +
                    "⚠️ 注意：「关联启动」和「自启动」都不等于通知使用权。\n" +
                    "它们属于后台保活分类，管的是「允许被唤醒」。\n" +
                    "通知使用权是独立入口，点下面的按钮直达。",
                granted = notificationEnabled,
                actionLabel = "打开通知使用权列表",
                onAction = { openNotificationSettings(context) },
            )

            // vivo 等国产 ROM 的额外必做项：这些都不是通知使用权，但会影响稳定性
            OneLedgerCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("辅助设置（vivo / OPPO 等建议开启）", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "这些不是通知使用权的替代品，但不开会导致识别时灵时不灵",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "· 应用详情页「设备管理」里的「自启动」和「关联启动」都打开\n" +
                            "· 设置 → 电池 → 后台耗电管理 → 随手记 → 选「允许后台高耗电」\n" +
                            "· i管家 → 省电管理 → 把随手记加入加速白名单\n" +
                            "· 多任务界面里把随手记卡片锁定，避免一键清理时被清掉\n" +
                            "· 关掉「睡眠模式」，否则夜间会冻结应用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            PermissionCard(
                title = "短信权限",
                description = "识别银行发来的交易短信，能覆盖通知里没有的支出。验证码和营销短信会被自动忽略。\n\n" +
                    "在权限列表里点「短信」→ 选「允许」。",
                granted = smsGranted,
                actionLabel = "去授权短信权限",
                onAction = { openAppSettings(context) },
            )

            // 有问题先看自检：能直接告诉用户"到底哪个通道在工作"
            OneLedgerCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("识别不出交易？", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "打开自检诊断可以看到监听服务是否连上、最近收到了哪些通知，\n" +
                            "还能粘贴文本当场测试解析能力，不用靠猜。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    PressableSurface(
                        onClick = onOpenDiagnostics,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "打开自检诊断",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    PressableSurface(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "设置与检查更新",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            OneLedgerCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("隐私说明", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "· 只读取通知与短信的文本内容，不点击、不打开任何其他应用\n" +
                            "· 解析全部在本机完成，应用未申请网络权限，数据无法上传\n" +
                            "· 随时可在系统设置中关闭通知使用权，或在应用管理中撤回短信权限\n" +
                            "· 卸载应用即彻底停止监听",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    OneLedgerCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(
                            (if (granted) IncomeMint else SavingsAmber).copy(alpha = 0.16f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (granted) Icons.Default.CheckCircle else Icons.Default.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (granted) IncomeMint else SavingsAmber,
                    )
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (granted) "已开启" else "未开启",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (granted) IncomeMint else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!granted) {
                Spacer(Modifier.height(12.dp))
                PressableSurface(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            actionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 跳转到系统的「通知使用权」列表页。
 *
 * 优先级说明：
 * - `ACTION_NOTIFICATION_LISTENER_SETTINGS` 是 AOSP 标准 action，
 *   vivo/OPPO 等国产 ROM **是支持的**，会打开系统自带的通知使用权列表。
 *   前面 v1.2.1 误以为国产 ROM 不支持而改为跳应用详情页，属于判断失误，已纠正。
 * - 应用详情页仅作为最后兜底：部分 ROM 定制过跳转时会失败。
 */
private fun openNotificationSettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        appDetailsIntent(context),
    )

    for (intent in intents) {
        val launched = runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        if (launched) return
    }
}

/** 跳转到本应用详情页，用户可在其中授予短信权限。 */
private fun openAppSettings(context: Context) {
    runCatching { context.startActivity(appDetailsIntent(context)) }
}

private fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
