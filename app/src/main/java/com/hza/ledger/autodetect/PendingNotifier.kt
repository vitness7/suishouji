package com.hza.ledger.autodetect

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hza.ledger.MainActivity
import com.hza.ledger.R

/**
 * 待确认提醒通知。
 *
 * 存在意义：自动识别的价值在于"不用记得记"，如果识别完了还要用户主动去翻，
 * 价值就折损一半。识别成功即主动提醒，点通知直达待确认列表。
 *
 * ⚠️ 设计要求：**任何不发通知的情况都必须留下原因**。
 * 早期版本有 4 个静默 return 点，通知不弹时完全无从排查，
 * 用户只能看到「没反应」。现在统一走 [recordFailure]。
 */
object PendingNotifier {

    private const val CHANNEL_ID = "pending_transactions"
    private const val NOTIFICATION_ID_BASE = 8000

    /**
     * 记录一次「通知未发送」的原因。
     *
     * 不直接依赖 AutoDetectDiagnostics（避免包循环），
     * 改为写入崩溃记录，用户可在设置页看到。
     */
    private fun recordFailure(reason: String) {
        android.util.Log.w("PendingNotifier", "通知未发送：$reason")
        NotificationDiagnostics.lastFailure = reason
    }


    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "待确认交易",
            // 用低优先级：提醒但不打断，避免频繁打扰
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "自动识别出交易时提醒你去确认入账"
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 发送提醒。
     *
     * @param amountText 已格式化的金额，如 "¥35.80"
     * @param pendingCount 当前待确认总数
     */
    fun notifyPending(context: Context, amountText: String, pendingCount: Int) {
        ensureChannel(context)

        // Android 13+ 通知权限可被用户随时撤回，必须显式检查后再发，
        // 否则会抛 SecurityException 且 Lint 会报 MissingPermission。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            recordFailure("缺少通知权限（Android 13+ 需要用户授权）")
            return
        }

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            recordFailure("系统通知开关已关闭（应用设置里通知被禁用）")
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // 让应用打开后直接落在待确认页
            putExtra(MainActivity.EXTRA_OPEN_PENDING, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            // ⚠️ 不能写死「支出」——自动识别里也有收入（工资、退款）。
            // 之前写死会让工资到账时弹出「识别到新的支出」，明显不对。
            .setContentTitle("识别到新的交易")
            .setContentText("$amountText　点此确认是否入账")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("自动识别到一笔 $amountText 的交易，等你确认后才会记入账本。"),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID_BASE, notification)
            NotificationDiagnostics.lastSuccessAt = System.currentTimeMillis()
            NotificationDiagnostics.lastSuccessAmount = amountText
            // ⚠️ 不在这里清空 lastFailure。
            // 用户反馈「不弹通知」时，若这里清空了，设置页就什么都不显示 ——
            // 正好把唯一的线索抹掉。改为只更新成功时间，失败原因保留最近一次。
            android.util.Log.i("PendingNotifier", "通知已发送：$amountText")
        }.onFailure { error ->
            recordFailure("发送异常：${error.message}")
        }
    }

    /** 取消提醒，用户处理完待确认后调用。 */
    fun cancel(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_BASE)
        }
    }

    /**
     * 发送一条测试通知，用于验证「本应用能否弹出通知」。
     *
     * 存在意义：用户反馈「不弹提醒」时，需要区分两种完全不同的原因 ——
     * 1. 通知权限被关 → 本方法能直接暴露
     * 2. 监听服务被系统杀掉 → 由 [listenerHealth] 判定
     *
     * 没有这个按钮，用户只能靠猜，或者反复支付来试探。
     *
     * @return 发送成功返回 null；失败返回原因
     */
    fun sendTestNotification(context: Context): String? {
        ensureChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val reason = "缺少通知权限。请到系统设置 → 应用 → 随手记 → 通知 里允许通知"
            recordFailure(reason)
            return reason
        }

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            val reason = "系统通知开关已关闭。请到系统设置 → 应用 → 随手记 → 通知 里打开"
            recordFailure(reason)
            return reason
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("测试通知")
            .setContentText("如果你看到这条，说明通知功能正常")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("这条是测试通知。能看到它，说明本应用可以正常弹出提醒。"),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID_BASE + 1, notification)
            null
        }.getOrElse { error ->
            val reason = "发送失败：${error.message}"
            recordFailure(reason)
            reason
        }
    }
}


/**
 * 通知链路的可观测状态。
 *
 * ## 为什么必须持久化（v1.7.1 的关键修复）
 *
 * 早期版本这些值只在内存里，**App 一重启就全部清零**。
 * 后果是排查「前几天能用、今天忽然不行」这类历史问题时分外困难：
 * 用户看到的是 0，但无法区分「从来没收到过」与「刚重启所以清零」。
 *
 * 现在每个字段的 setter 都会自动落盘，引用处无需改动。
 *
 * ## 为什么要记 lastReceivedAt
 *
 * 判断「监听服务是否真在工作」不能靠布尔标志 ——
 * `ListenerConnectionProbe` 一旦持久化过 true，服务被系统杀掉后
 * 冷启动仍会显示「已授权」，形成假信号。
 * 时间戳不会骗人：若最后收到通知是 5 天前，而用户天天在用手机，
 * 那就是服务已经停了。
 */
object NotificationDiagnostics {
    private const val PREFS = "notify_diagnostics"
    private const val KEY_LAST_RECEIVED_AT = "last_received_at"
    private const val KEY_LAST_SUCCESS_AT = "last_success_at"
    private const val KEY_LAST_SUCCESS_AMOUNT = "last_success_amount"
    private const val KEY_RECEIVED_COUNT = "received_count"
    private const val KEY_LAST_FAILURE = "last_failure"

    private val prefs
        get() = AppContextHolder.context
            .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

    /** 最后一次收到任意通知的时间。判断监听服务是否存活的核心依据。 */
    @Volatile
    var lastReceivedAt: Long = 0L
        set(value) {
            field = value
            prefs.edit().putLong(KEY_LAST_RECEIVED_AT, value).apply()
        }

    /** 监听器累计收到的通知数。 */
    @Volatile
    var receivedCount: Int = 0
        set(value) {
            field = value
            prefs.edit().putInt(KEY_RECEIVED_COUNT, value).apply()
        }

    @Volatile
    var lastSuccessAt: Long = 0L
        set(value) {
            field = value
            prefs.edit().putLong(KEY_LAST_SUCCESS_AT, value).apply()
        }

    @Volatile
    var lastSuccessAmount: String = ""
        set(value) {
            field = value
            prefs.edit().putString(KEY_LAST_SUCCESS_AMOUNT, value).apply()
        }

    /** 最后一次发送失败的原因。成功后**不清空** —— 否则排查时没有线索。 */
    @Volatile
    var lastFailure: String? = null
        set(value) {
            field = value
            prefs.edit().putString(KEY_LAST_FAILURE, value).apply()
        }

    /** 冷启动时恢复。必须在 Application.onCreate 中调用。 */
    fun restore() {
        runCatching {
            val p = prefs
            lastReceivedAt = p.getLong(KEY_LAST_RECEIVED_AT, 0L)
            receivedCount = p.getInt(KEY_RECEIVED_COUNT, 0)
            lastSuccessAt = p.getLong(KEY_LAST_SUCCESS_AT, 0L)
            lastSuccessAmount = p.getString(KEY_LAST_SUCCESS_AMOUNT, "").orEmpty()
            lastFailure = p.getString(KEY_LAST_FAILURE, null)
        }
    }

    /** 清空全部记录，供用户主动重置。 */
    fun clear() {
        runCatching { prefs.edit().clear().apply() }
        lastReceivedAt = 0L
        receivedCount = 0
        lastSuccessAt = 0L
        lastSuccessAmount = ""
        lastFailure = null
    }

    /**
     * 监听服务是否疑似已停止。
     *
     * ⚠️ 用「时间」而非「布尔标志」判断 ——
     * 权限开关显示开启不代表服务在运行（国产 ROM 省电策略会静默杀掉）。
     * 时间戳是唯一不会说谎的信号。
     */
    fun serviceLooksStalled(now: Long = System.currentTimeMillis()): Boolean =
        isListenerStalled(receivedCount, lastReceivedAt, now)

    /** 多久没收到通知了，用于界面显示。 */
    fun silentForText(now: Long = System.currentTimeMillis()): String =
        formatSilentDuration(lastReceivedAt, now)
}

/** 超过这个时长没收到任何通知，就认为服务可能已停止。 */
internal const val STALL_THRESHOLD_MS = 24L * 60 * 60 * 1000

/**
 * 判断监听服务是否疑似停止（纯函数，便于单测）。
 *
 * 判据：**曾经工作过**（收到过通知）**但已超过阈值没再收到**。
 *
 * ⚠️ `receivedCount == 0` 时返回 false ——
 * 刚安装还没触发过通知，不能判定为"停止"，那是另一种状态
 * （[ListenerHealth.NEVER_WORKED]）。
 */
internal fun isListenerStalled(
    receivedCount: Int,
    lastReceivedAt: Long,
    now: Long,
): Boolean {
    if (receivedCount <= 0) return false
    if (lastReceivedAt <= 0L) return false
    return now - lastReceivedAt > STALL_THRESHOLD_MS
}

/**
 * 把「多久没收到通知」格式化成中文相对时间。
 *
 * 用自然语言而非毫秒数，因为这是给用户看的 ——
 * 「3 天前」比「259200000ms」有用得多。
 *
 * ⚠️ 返回的是相对时间（"3 天前"），不是完整句子 ——
 * 由界面决定前缀怎么写，避免出现「上次收到通知：已 3 天未收到」这种重复表述。
 */
internal fun formatSilentDuration(lastReceivedAt: Long, now: Long): String {
    if (lastReceivedAt <= 0L) return "从未收到过"
    val elapsed = (now - lastReceivedAt).coerceAtLeast(0L)
    val minutes = elapsed / 60_000
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        minutes < 60 * 24 -> "${minutes / 60} 小时前"
        else -> "${minutes / (60 * 24)} 天前"
    }
}
