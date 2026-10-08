package com.hza.ledger

import android.app.Application
import com.hza.ledger.autodetect.AppContextHolder
import com.hza.ledger.autodetect.ListenerConnectionProbe
import com.hza.ledger.autodetect.PendingNotifier
import com.hza.ledger.util.AppContextRef
import com.hza.ledger.util.CrashReporter
import com.hza.ledger.data.local.OneLedgerDatabase
import com.hza.ledger.data.repository.AutoDetectRepository
import com.hza.ledger.export.ExportRepository
import com.hza.ledger.data.repository.LedgerRepository
import com.hza.ledger.data.repository.OfflineLedgerRepository

class OneLedgerApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // 监听器可能早于任何界面启动，这里必须完成容器初始化
        OneLedgerApplicationHolder.container = container
        AppContextHolder.init(this)
        AppContextRef.app = this
        // 尽早安装崩溃捕获：闪退类问题必须拿到运行时堆栈，光看代码猜不出来
        CrashReporter.install()
        // 冷启动时恢复监听器的授权状态，否则用户会看到"未授权"而误以为设置失败
        ListenerConnectionProbe.restore()

        // 识别成功后发送提醒通知。
        // ⚠️ 必须在 Application 里绑定 —— 若依赖 Activity 赋值，
        // 监听器在后台收到通知时回调为空，通知永远不会弹。
        OneLedgerApplicationHolder.onPendingCreated = { amountText ->
            PendingNotifier.notifyPending(this, amountText, 1)
        }
    }
}

class AppContainer(application: Application) {
    private val database = OneLedgerDatabase.getInstance(application)
    val repository: LedgerRepository = OfflineLedgerRepository(database.ledgerDao())
    val autoDetectRepository: AutoDetectRepository =
        AutoDetectRepository(database.ledgerDao(), repository.activeBookId)

    /** 账本导出（CSV）。用于把数据取出来，避免卸载/换机导致丢失。 */
    val exportRepository: ExportRepository =
        ExportRepository(database.ledgerDao())
}

object OneLedgerApplicationHolder {
    /**
     * 通知与短信监听器需要在无 UI 场景下拿到仓库。
     * 由 [OneLedgerApplication] 在onCreate 时赋值。
     */
    var container: AppContainer? = null
        internal set

    /**
     * 识别出候选时回调，用于发送系统通知提醒用户去确认。
     *
     * ⚠️ 这个回调**必须在 Application.onCreate 里直接绑定**。
     * 之前注释写的是「由 Activity 在前台挂载」，但实测发现 Activity 从未赋值，
     * 导致 `?.invoke()` 永远是 no-op，**通知从来不弹** ——
     * 而交易其实已进待确认列表，用户却完全不知道。
     *
     * 通知本身不需要 Activity 上下文，PendingNotifier 只需 applicationContext。
     */
    @Volatile
    var onPendingCreated: ((amountText: String) -> Unit)? = null

    val activeBookId: String
        get() = container?.repository?.activeBookId?.value.orEmpty()

    val autoDetectRepository: AutoDetectRepository
        get() = requireNotNull(container) {
            "AppContainer 尚未初始化，监听器不应早于 Application.onCreate 启动"
        }.autoDetectRepository

    /** 账本导出（CSV）。 */
    val exportRepository: ExportRepository
        get() = requireNotNull(container) {
            "AppContainer 尚未初始化"
        }.exportRepository
}
