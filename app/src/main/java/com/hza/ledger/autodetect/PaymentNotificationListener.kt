package com.hza.ledger.autodetect

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.hza.ledger.OneLedgerApplicationHolder
import com.hza.ledger.util.MoneyFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 监听支付类通知并解析金额。
 *
 * 关键设计（v1.1.1 修正）：
 * **不再按包名白名单过滤。** 早期版本只监听微信/支付宝等 6 个包名，导致
 * 微信支付完成通知（常来自「服务通知」、小程序等）被提前丢弃而完全无法识别。
 * 现在改为「让解析器说话」—— 能解析出金额且置信度达标即为候选，解析不出就丢弃。
 *
 * 隐私边界不变：只读取通知文本，不点击、不打开任何应用、不联网。
 */
class PaymentNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        // 这是最可靠的"已授权"信号：只有系统真正认可了监听器才会回调。
        // 比读 Settings.Secure 的配置更准，国产 ROM 常把配置写在非标准位置。
        ListenerConnectionProbe.markConnected()
        AutoDetectDiagnostics.recordStatus(ListenerStatus.CONNECTED)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        ListenerConnectionProbe.markDisconnected()
        AutoDetectDiagnostics.recordStatus(ListenerStatus.DISCONNECTED)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val packageName = sbn.packageName ?: return

        // 排除本应用自己的通知，避免自循环
        if (packageName == applicationContext.packageName) return

        val appLabel = packageLabel(packageName)
        val text = extractText(notification)

        if (text.isNullOrBlank()) {
            AutoDetectDiagnostics.record(
                RecentNotification(
                    appLabel = appLabel,
                    rawText = "",
                    result = "无文本内容（可能是系统脱敏）",
                    parsed = false,
                ),
            )
            return
        }

        val parsed = parseTransactionText(text)

        // 无论成功与否都记录，便于用户对照原始通知排查
        AutoDetectDiagnostics.record(
            RecentNotification(
                appLabel = appLabel,
                rawText = text.take(120),
                result = if (parsed != null) {
                    "识别为${directionLabel(parsed.direction)} ${MoneyFormatter.format(parsed.amountMinor)}" +
                        "（可信度 ${(parsed.confidence * 100).toInt()}%）"
                } else {
                    "未能识别为交易"
                },
                parsed = parsed != null,
            ),
        )

        if (parsed == null || parsed.confidence < MIN_CONFIDENCE) return

        val bookId = OneLedgerApplicationHolder.activeBookId
        if (bookId.isBlank()) return

        scope.launch {
            runCatching {
                val inserted = OneLedgerApplicationHolder.autoDetectRepository.ingest(
                    bookId = bookId,
                    text = text,
                    sourceKey = "notif:${sbn.key}:${sbn.postTime}",
                )
                if (inserted) {
                    OneLedgerApplicationHolder.onPendingCreated?.invoke(
                        MoneyFormatter.format(parsed.amountMinor),
                    )
                }
            }
        }
    }

    /**
     * 拼接通知的标题与正文。
     *
     * Android 13+ 会对敏感通知脱敏，此时文本里不含金额，
     * 解析器会自然返回 null，不会误判。
     */
    private fun extractText(notification: Notification): String? {
        val extras = notification.extras ?: return null
        val parts = buildList {
            extras.getCharSequence(Notification.EXTRA_TITLE)?.let { add(it.toString()) }
            extras.getCharSequence(Notification.EXTRA_TEXT)?.let { add(it.toString()) }
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { add(it.toString()) }
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach {
                add(it.toString())
            }
        }
        return parts
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
    }

    private fun packageLabel(packageName: String): String = runCatching {
        val pm = applicationContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

/** 监听器连接状态。 */
enum class ListenerStatus { CONNECTED, DISCONNECTED }

/** 自检页展示的一条记录。 */
data class RecentNotification(
    val appLabel: String,
    val rawText: String,
    val result: String,
    val parsed: Boolean,
    val timeMillis: Long = System.currentTimeMillis(),
)

/**
 * 接收短信并解析银行/支付类交易。
 *
 * 需要用户授予短信权限。验证码、营销短信由解析器自动拒绝。
 */
class SmsReceiver : android.content.BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        if (body.isBlank()) return

        val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
        val bookId = OneLedgerApplicationHolder.activeBookId
        if (bookId.isBlank()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inserted = OneLedgerApplicationHolder.autoDetectRepository.ingest(
                    bookId = bookId,
                    text = "$sender $body",
                    sourceKey = "sms:${sender.hashCode()}:${body.hashCode()}",
                )
                if (inserted) {
                    OneLedgerApplicationHolder.onPendingCreated?.invoke("1 笔短信交易")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

internal fun directionLabel(direction: ParseDirection): String = when (direction) {
    ParseDirection.EXPENSE -> "支出"
    ParseDirection.INCOME -> "收入"
    ParseDirection.UNKNOWN -> "未知方向"
}

/**
 * 调试日志环形缓冲，供自检页读取。
 *
 * 仅保留最近几十条，**不持久化到磁盘**，App 退出即清空。
 * 不含完整短信原文，仅截取前 120 字用于排查。
 */
object AutoDetectDiagnostics {
    private const val MAX_ENTRIES = 30

    private val entries = ArrayDeque<RecentNotification>()

    fun record(entry: RecentNotification) {
        synchronized(entries) {
            entries.addFirst(entry)
            while (entries.size > MAX_ENTRIES) entries.removeLast()
        }
    }

    fun recordStatus(status: ListenerStatus) {
        record(
            RecentNotification(
                appLabel = "监听服务",
                rawText = "",
                result = when (status) {
                    ListenerStatus.CONNECTED -> "已连接，正在监听通知"
                    ListenerStatus.DISCONNECTED -> "已断开，通知将无法识别"
                },
                parsed = status == ListenerStatus.CONNECTED,
            ),
        )
    }

    fun recent(): List<RecentNotification> = synchronized(entries) { entries.toList() }

    fun clear() {
        synchronized(entries) { entries.clear() }
    }
}
