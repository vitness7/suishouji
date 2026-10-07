package com.hza.ledger.autodetect

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
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

/** 用户是否已在系统设置中授予本应用「通知使用权」。 */
fun isNotificationListenerEnabled(): Boolean {
    val enabled = Settings.Secure.getString(
        AppContextHolder.context.contentResolver,
        "enabled_notification_listeners",
    ) ?: return false

    val flatten = enabled.split(":").mapNotNull(ComponentName::unflattenFromString)
    return flatten.any { it == LISTENER_COMPONENT }
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
