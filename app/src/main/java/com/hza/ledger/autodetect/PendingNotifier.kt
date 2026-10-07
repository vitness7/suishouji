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
 */
object PendingNotifier {

    private const val CHANNEL_ID = "pending_transactions"
    private const val NOTIFICATION_ID_BASE = 8000

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
            return
        }

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

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
            .setContentTitle("识别到新的支出")
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
        }
    }

    /** 取消提醒，用户处理完待确认后调用。 */
    fun cancel(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_BASE)
        }
    }
}
