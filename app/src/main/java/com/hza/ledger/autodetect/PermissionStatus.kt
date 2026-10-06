package com.hza.ledger.autodetect

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.hza.ledger.OneLedgerApplicationHolder

/**
 * 权限状态检测。
 *
 * 通知使用权属于「已授予」与「已授权」两级状态：用户开启开关后系统才真正调用监听器。
 * 这里只做读取判断，不做任何状态变更。
 */

/** 本应用的监听服务组件名。 */
private val LISTENER_COMPONENT = ComponentName(
    "com.hza.ledger",
    "com.hza.ledger.autodetect.PaymentNotificationListener",
)

/** 用户是否已在系统设置中授予本应用「通知使用权」。 */
fun isNotificationListenerEnabled(): Boolean {
    val enabled = Settings.Secure.getString(
        OneLedgerApplicationHolderContainer.context.contentResolver,
        "enabled_notification_listeners",
    ) ?: return false

    val flatten = enabled.split(":").mapNotNull(ComponentName::unflattenFromString)
    return flatten.any { it == LISTENER_COMPONENT }
}

/**
 * 短信权限是否已授予。
 *
 * Android 13+ 通知权限为运行时授予；短信权限同理。
 */
fun isSmsPermissionGranted(context: Context = OneLedgerApplicationHolderContainer.context): Boolean =
    ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECEIVE_SMS) ==
        PackageManager.PERMISSION_GRANTED

/**
 * 统一获取应用 Context 的小工具，避免在各处重复传参。
 *
 * 由 [com.hza.ledger.OneLedgerApplication] 在启动时注入。
 */
object OneLedgerApplicationHolderContainer {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val context: Context
        get() = requireNotNull(appContext) { "上下文尚未初始化" }
}

/** 供 UI 判断是否已具备自动识别能力的完整条件。 */
data class AutoDetectStatus(
    val notificationEnabled: Boolean,
    val smsGranted: Boolean,
) {
    /** 至少一条通道可用即可工作。 */
    val canDetect: Boolean get() = notificationEnabled || smsGranted
}
