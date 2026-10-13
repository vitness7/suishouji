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
        // ⚠️ 这里**故意不写入持久化** —— 这是刻意的设计取舍。
        //
        // 原因：服务被系统回收时会触发 onListenerDisconnected，
        // 但系统往往很快又会重新拉起。若把 false 写进持久化，
        // 用户冷启动应用就会看到「未授权」，白白跑去重新设置。
        //
        // 代价是：服务被永久杀掉后，这里仍会显示「曾授权」。
        // 这个代价由 [NotificationDiagnostics.lastReceivedAt] 来兜底 ——
        // 用「多久没收到通知」判断服务是否真的在工作，
        // 比一个会骗人的布尔值可靠得多。
    }
}

/**
 * 监听服务的真实健康状态。
 *
 * 为什么要单独一个类型：`isNotificationListenerEnabled()` 只能回答
 * 「用户有没有授权」，回答不了「服务现在到底在不在跑」。
 * 国产 ROM 的省电策略会在权限仍显示开启的情况下静默杀掉服务，
 * 于是出现「权限开着但收不到通知」的现象 —— 用户完全无从理解。
 */
enum class ListenerHealth {
    /** 从未收到过通知，可能是刚安装或确实没触发过。 */
    NEVER_WORKED,

    /** 最近有收到通知，链路正常。 */
    WORKING,

    /** 曾经正常工作过，但已超过阈值没有收到任何通知 —— 大概率被系统杀了。 */
    STALLED,

    /** 通知使用权未授予。 */
    DISABLED,
    ;

    /** 是否需要在界面上醒目提示用户处理。 */
    val needsAttention: Boolean
        get() = this == STALLED || this == DISABLED
}

/**
 * 判定监听服务的健康状态。
 *
 * 优先级：未授权 > 从未工作 > 疑似停止 > 正常。
 */
fun listenerHealth(): ListenerHealth {
    val diag = NotificationDiagnostics
    val authorized = isNotificationListenerEnabled()

    return when {
        !authorized && diag.receivedCount == 0 -> ListenerHealth.DISABLED
        diag.receivedCount == 0 -> ListenerHealth.NEVER_WORKED
        diag.serviceLooksStalled() -> ListenerHealth.STALLED
        else -> ListenerHealth.WORKING
    }
}

/** 健康状态对应的可操作提示，供界面直接显示。 */
fun ListenerHealth.hint(): String = when (this) {
    ListenerHealth.DISABLED ->
        "通知使用权未开启，自动识别无法工作。请到系统设置里开启。"
    ListenerHealth.NEVER_WORKED ->
        "尚未收到过任何通知。若已开启通知使用权，请支付一笔试试，再回来看这里。"
    ListenerHealth.STALLED ->
        "已经很久没收到任何通知，监听服务可能被系统停止了。" +
            "请到系统设置把「通知使用权」关掉再重新打开，并允许自启动与后台运行。"
    ListenerHealth.WORKING ->
        "监听服务运行正常。"
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
