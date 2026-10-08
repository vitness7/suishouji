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

    /** 记录通知发送的最近状态，供自检页与设置页排查。 */
    object NotificationDiagnostics {
        @Volatile
        var lastFailure: String? = null

        @Volatile
        var lastSuccessAt: Long = 0L

        @Volatile
        var lastSuccessAmount: String = ""

        /** 监听器累计收到的通知数。用于判断「监听器到底有没有在跑」。 */
        @Volatile
        var receivedCount: Int = 0
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
}
