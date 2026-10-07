package com.hza.ledger.autodetect

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat

/**
 * 权限与监听状态检测。
 *
 * 通知使用权属于「已授予」与「已授权」两级状态：用户开启开关后系统才真正调用监听器。
 * 这里只做读取判断，不做任何状态变更。
 */

/** 本应用的监听服务组件名。 */
private val LISTENER_COMPONENT = ComponentName(
    "com.hza.ledger",
    "com.hza.ledger.autodetect.PaymentNotificationListener",
)

/** 全局应用上下文，由 [com.hza.ledger.OneLedgerApplication] 注入。 */
object AppContextHolder {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val context: Context
        get() = requireNotNull(appContext) { "上下文尚未初始化，请在 Application.onCreate 中调用 init" }
}

/**
 * 用户是否已授予本应用「通知使用权」。
 *
 * 采用**双通道检测**，因为不同 ROM 对此的实现差异很大：
 * 1. 读系统设置 `enabled_notification_listeners`（标准做法，AOSP 与多数 ROM 适用）
 * 2. 兜底：看监听服务是否已成功连接过（`onListenerConnected` 才会置位）。
 *    只有系统真正认可了监听器才会回调该方法，因此这是最权威的信号。
 *
 * 只用第 1 条会在部分国产 ROM 上误报未授权（配置被写到了非标准位置）。
 */
fun isNotificationListenerEnabled(): Boolean =
    readFromSettings() || ListenerConnectionProbe.connected

/** 通道一：读系统设置里的已授权组件列表。 */
private fun readFromSettings(): Boolean {
    val enabled = runCatching {
        Settings.Secure.getString(
            AppContextHolder.context.contentResolver,
            "enabled_notification_listeners",
        )
    }.getOrNull() ?: return false

    return enabled.split(":")
        .mapNotNull(ComponentName::unflattenFromString)
        .any { it == LISTENER_COMPONENT }
}

/**
 * 监听服务连接状态的记录。
 *
 * 由 [PaymentNotificationListener] 在 onListenerConnected / onListenerDisconnected 中维护。
 *
 * 为什么需要额外这一层：
 * `onListenerConnected` 只在进程存活时回调，冷启动应用后这个内存值会丢。
 * 但系统其实仍在授权状态 —— 所以这里写入 SharedPreferences 做持久化，
 * 让应用重启后仍能正确显示「已授权」，不至于让用户误以为失败而反复折腾。
 */
object ListenerConnectionProbe {
    private const val PREFS = "listener_state"
    private const val KEY_AUTHORIZED = "authorized_once"

    @Volatile
    var connected: Boolean = false
        internal set

    /** 冷启动时从持久化记录恢复。 */
    fun restore() {
        connected = runCatching {
            AppContextHolder.context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTHORIZED, false)
        }.getOrDefault(false)
    }

    internal fun markConnected() {
        connected = true
        runCatching {
            AppContextHolder.context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_AUTHORIZED, true)
                .apply()
        }
    }

    internal fun markDisconnected() {
        connected = false
    }
}

/** 短信权限是否已授予。 */
fun isSmsPermissionGranted(context: Context = AppContextHolder.context): Boolean =
    ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECEIVE_SMS) ==
        PackageManager.PERMISSION_GRANTED

/**
 * 自动识别的可用状态。
 *
 * @param notificationEnabled 通知使用权是否已授予
 * @param smsGranted 短信权限是否已授予
 */
data class AutoDetectStatus(
    val notificationEnabled: Boolean,
    val smsGranted: Boolean,
) {
    /** 至少一条通道可用即可尝试工作。 */
    val canDetect: Boolean get() = notificationEnabled || smsGranted

    /** 授权了但一条都没识别到 —— 大概率是系统脱敏或规则未覆盖。 */
    val suspicious: Boolean
        get() = canDetect && AutoDetectDiagnostics.recent().none { it.parsed }
}
