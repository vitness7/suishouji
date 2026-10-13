package com.hza.ledger.autodetect

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService

/**
 * 监听服务的自我恢复。
 *
 * ## 为什么需要它
 *
 * 国产 ROM 的省电策略会静默杀掉监听服务，表现为
 * **「通知使用权还开着，但收不到任何通知」**。
 *
 * 传统解法是让用户去系统设置里把通知使用权**关掉再打开** ——
 * 这能强制系统重新绑定服务，但用户得翻好几层设置，很麻烦
 * （而且很多人根本不知道要去那里操作）。
 *
 * Android 从 7.0 起提供了 [NotificationListenerService.requestRebind]，
 * 允许应用请求系统重新绑定自己的监听服务，
 * **效果等同于「关掉再开启」，但用户不必离开应用**。
 */
object ListenerRecovery {

    private const val PREFS = "listener_recovery"
    private const val KEY_LAST_AUTO_ATTEMPT = "last_auto_attempt_at"

    /**
     * 自动恢复的最小间隔：1 小时。
     *
     * 为什么要限流：`requestRebind` 会让系统重建监听服务，
     * 服务本来正常时重建会造成短暂中断。若每次打开应用都无脑调用，
     * 反而可能把好好的服务折腾坏。
     */
    private const val MIN_AUTO_INTERVAL_MS = 60L * 60 * 1000

    /**
     * 请求系统重新绑定监听服务。
     *
     * 调用后系统会重建服务并回调 `onListenerConnected`。
     * 这是异步过程，通常需要一两秒 —— 调用方应提示用户稍候，
     * 而不是立刻断言成功。
     *
     * @return null 表示请求已发出；否则返回可直接展示给用户的失败原因
     */
    fun requestRebind(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // API 24 以下没有这个能力，只能退回手动操作
            return "系统版本较低，请到「设置 → 通知使用权」里把随手记关掉再打开"
        }

        return runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(
                    // ⚠️ 用 context.packageName 而非硬编码 ——
                    // 一旦给 debug 构建加包名后缀，硬编码的组件名就会指向不存在的服务
                    context.packageName,
                    PaymentNotificationListener::class.java.name,
                ),
            )
            null
        }.getOrElse { error ->
            "自动重启未成功（${error.message ?: "系统限制"}）。" +
                "请到「设置 → 通知使用权」里把随手记关掉再打开"
        }
    }

    /**
     * 打开应用时自动尝试恢复（无需用户操作）。
     *
     * ## 触发条件（必须同时满足）
     * 1. 健康状态为 [ListenerHealth.STALLED] —— 曾正常工作过、但已很久没收到通知。
     *    **只处理这一种**：`DISABLED` 是没授权，重绑也无用；
     *    `NEVER_WORKED` 是新装还没触发过，不是异常；`WORKING` 更不该打扰。
     * 2. 距上次自动尝试超过 [MIN_AUTO_INTERVAL_MS]。
     *
     * ## 为什么做自动化
     *
     * 用户对「失灵了要去设置里关掉再开启」这件事的反馈是
     * 「太麻烦了」—— 手动操作本身就是负担。
     * 既然系统能重绑，就不该把这件事交给用户。
     *
     * @return 本次是否真的发起了恢复请求
     */
    fun autoRecoverIfNeeded(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        if (
            !shouldAutoRecover(
                health = listenerHealth(),
                lastAttemptAt = prefs.getLong(KEY_LAST_AUTO_ATTEMPT, 0L),
                now = now,
                minIntervalMs = MIN_AUTO_INTERVAL_MS,
            )
        ) {
            return false
        }

        prefs.edit().putLong(KEY_LAST_AUTO_ATTEMPT, now).apply()
        return requestRebind(context) == null
    }
}

/**
 * 判断是否应该自动尝试恢复（纯函数，便于单测）。
 *
 * ⚠️ 只有 [ListenerHealth.STALLED] 会触发：
 * - `WORKING` 服务正常，重绑会造成无谓的中断
 * - `NEVER_WORKED` 是新装还没触发过通知，不是异常
 * - `DISABLED` 是没授权，重绑没有任何用
 *
 * 这是「宁可少管，也不要乱管」的设计 ——
 * 自动操作若误伤正常状态，比不自动更糟。
 */
internal fun shouldAutoRecover(
    health: ListenerHealth,
    lastAttemptAt: Long,
    now: Long,
    minIntervalMs: Long,
): Boolean {
    if (health != ListenerHealth.STALLED) return false

    // 从未尝试过时 lastAttemptAt 为 0，应允许尝试
    if (lastAttemptAt <= 0L) return true

    // ⚠️ 时间被用户调早时 lastAttemptAt 会"落在未来"，
    // 此时 now - lastAttemptAt 为负，若直接比较会永久锁死自动恢复。
    // 明确视为可重试 —— 限流是为了防频繁重建，不是要永久禁用。
    if (lastAttemptAt > now) return true

    return now - lastAttemptAt >= minIntervalMs
}
