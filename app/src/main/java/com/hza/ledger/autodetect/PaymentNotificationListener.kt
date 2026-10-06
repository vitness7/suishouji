package com.hza.ledger.autodetect

import android.app.Notification
import android.content.Context
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
 * 监听支付类应用的通知并解析金额。
 *
 * 关键设计：**只读取通知文本，不点击、不打开应用、不做任何交互**。
 * 用户可在系统设置随时撤销「通知使用权」，撤销后本服务不会被系统调用。
 */
class PaymentNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 只关心这些来源，避免读取无关通知。 */
    private val watchedPackages = setOf(
        "com.tencent.mm",              // 微信
        "com.eg.android.AlipayGphone", // 支付宝
        "com.unionpay",                // 云闪付
        "com.chinamworld.main",// 中国建设银行等
        "com.icbc",                    // 工商银行
        "com.chinamobile.creditcard",  // 掌上生活
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val packageName = sbn.packageName ?: return

        if (packageName !in watchedPackages) return

        val text = extractText(notification) ?: return
        val appLabel = packageLabel(packageName)
        val combined = "$appLabel $text"

        val bookId = OneLedgerApplicationHolder.activeBookId
        if (bookId.isBlank()) return

        scope.launch {
            runCatching {
                OneLedgerApplicationHolder.autoDetectRepository.ingest(
                    bookId = bookId,
                    text = combined,
                    // 用通知 key + 时间戳做去重键，同一条通知不会重复入候选
                    sourceKey = "notif:${sbn.key}:${sbn.postTime}",
                )
            }
        }
    }

    /**
     * 拼接通知的标题与正文。
     *
     * 部分系统（如 Android 13+）会对敏感通知脱敏，此时文本里不含金额，
     * 解析器会自然返回 null，不会误判。
     */
    private fun extractText(notification: Notification): String? {
        val extras = notification.extras ?: return null
        val parts = buildList {
            extras.getCharSequence(Notification.EXTRA_TITLE)?.let { add(it.toString()) }
            extras.getCharSequence(Notification.EXTRA_TEXT)?.let { add(it.toString()) }
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { add(it.toString()) }
            if (extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) != null) {
                extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach {
                    add(it.toString())
                }
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

/**
 * 接收短信并解析银行/支付类交易。
 *
 * 需要用户授予「短信」权限。仅在明确识别为金融交易时才写入候选，
 * 验证码、营销短信一律由解析器拒绝。
 */
class SmsReceiver : android.content.BroadcastReceiver() {

    override fun onReceive(context: Context, intent: android.content.Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        if (body.isBlank()) return

        val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
        val bookId = OneLedgerApplicationHolder.activeBookId
        if (bookId.isBlank()) return

        // 广播接收器生命周期短，必须用 goAsync 拿到异步执行权
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                OneLedgerApplicationHolder.autoDetectRepository.ingest(
                    bookId = bookId,
                    text = "$sender $body",
                    sourceKey = "sms:${sender.hashCode()}:${body.hashCode()}",
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** 供界面展示识别结果预览时复用。 */
internal fun formatPreviewAmount(amountMinor: Long): String = MoneyFormatter.format(amountMinor)
