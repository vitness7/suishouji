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

        // ⚠️ 无论后续解析成功与否，都记录「收到了哪个 App 的通知」。
        // 用户反馈「不弹」时，第一件事要确认的是：
        // 监听器到底有没有收到支付 App 的通知 —— 若根本没收到，
        // 后面所有解析与通知逻辑都是空谈。
        //
        // lastReceivedAt 会被持久化，因此即使用户几天后重启应用，
        // 也能看出「上一次收到通知是多久以前」——
        // 这是判断监听服务是否被系统杀掉的唯一可靠依据。
        NotificationDiagnostics.receivedCount += 1
        NotificationDiagnostics.lastReceivedAt = System.currentTimeMillis()

        val appLabel = packageLabel(packageName)
        val isBusinessApp = EcommerceClassifier.isBusinessPackage(packageName)
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

        // 营收单独处理：店铺收到的货款不该自动入账，需要给用户明确提示
        if (EcommerceClassifier.isRevenue(text)) {
            AutoDetectDiagnostics.record(
                RecentNotification(
                    appLabel = appLabel,
                    rawText = text.take(120),
                    result = "识别为店铺营收，不自动入账",
                    parsed = false,
                ),
            )
            return
        }

        val parsed = parseTransactionText(text, packageName)

        // 无论成功与否都记录，便于用户对照原始通知排查
        val isBusiness = parsed != null &&
            EcommerceClassifier.isBusinessCost(text, packageName)

        AutoDetectDiagnostics.record(
            RecentNotification(
                appLabel = appLabel,
                rawText = text.take(120),
                result = when {
                    parsed == null -> "未能识别为交易"
                    isBusiness -> "识别为经营支出 ${MoneyFormatter.format(parsed.amountMinor)}" +
                        "（可信度 ${(parsed.confidence * 100).toInt()}%）"
                    else -> "识别为${directionLabel(parsed.direction)} ${MoneyFormatter.format(parsed.amountMinor)}" +
                        "（可信度 ${(parsed.confidence * 100).toInt()}%）"
                },
                parsed = parsed != null,
            ),
        )

        if (parsed == null || parsed.confidence < MIN_CONFIDENCE) return

        val amountText = MoneyFormatter.format(parsed.amountMinor)

        val bookId = OneLedgerApplicationHolder.activeBookId
        if (bookId.isBlank()) {
            AutoDetectDiagnostics.recordRecognized(
                appLabel, text, amountText,
                inserted = false, notified = false,
                failureReason = "账本未初始化",
            )
            return
        }

        scope.launch {
            runCatching {
                val inserted = OneLedgerApplicationHolder.autoDetectRepository.ingest(
                    bookId = bookId,
                    text = text,
                    sourceKey = "notif:${sbn.key}:${sbn.postTime}",
                    packageName = packageName,
                )

                var notified = false
                if (inserted) {
                    // 优先走回调（回调里可能附带其他 UI 联动）。
                    // ⚠️ 但不能只依赖回调：回调在 Application.onCreate 里绑定，
                    // 若监听器比 Application 初始化更早收到通知，回调为空，
                    // 通知就永远发不出去。所以回调缺失时**降级为直接发通知**。
                    val callbackInvoked = runCatching {
                        OneLedgerApplicationHolder.onPendingCreated?.invoke(amountText)
                            ?: return@runCatching false
                        true
                    }.getOrDefault(false)

                    notified = callbackInvoked

                    if (!notified) {
                        android.util.Log.i(
                            "PendingNotifier",
                            "回调未注册，降级为直接发送通知",
                        )
                        PendingNotifier.notifyPending(AppContextHolder.context, amountText, 1)
                        notified = NotificationDiagnostics.lastFailure == null
                    }
                }

                // 把完整链路结果写入诊断，让「识别成功但没弹通知」这类问题可排查
                AutoDetectDiagnostics.recordRecognized(
                    appLabel, text, amountText,
                    inserted = inserted,
                    notified = notified,
                    failureReason = NotificationDiagnostics.lastFailure,
                )
            }
        }
    }

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

    /**
     * 记录一次「识别成功」的完整结果，含通知是否送达。
     *
     * 之前只记录识别结果，用户看到"识别为支出 ¥xx"却没弹通知，
     * 完全无法判断卡在哪一步。现在把通知链路的状态一并写入，
     * 自检页能一眼看出是识别没成功，还是通知没发出去。
     */
    fun recordRecognized(
        appLabel: String,
        rawText: String,
        amountText: String,
        inserted: Boolean,
        notified: Boolean,
        failureReason: String?,
    ) {
        val outcome = when {
            !inserted -> "已识别但未入库（可能重复投递）"
            notified -> "已入库 + 通知已发送"
            failureReason != null -> "已入库，通知未发送：$failureReason"
            else -> "已入库，通知状态未知"
        }
        record(
            RecentNotification(
                appLabel = appLabel,
                rawText = rawText.take(120),
                result = "$amountText  $outcome",
                parsed = inserted,
            ),
        )
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
