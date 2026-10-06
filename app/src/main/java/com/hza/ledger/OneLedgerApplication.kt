package com.hza.ledger

import android.app.Application
import com.hza.ledger.autodetect.OneLedgerApplicationHolderContainer
import com.hza.ledger.data.local.OneLedgerDatabase
import com.hza.ledger.data.repository.AutoDetectRepository
import com.hza.ledger.data.repository.LedgerRepository
import com.hza.ledger.data.repository.OfflineLedgerRepository

class OneLedgerApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // 监听器可能早于任何界面启动，这里必须完成容器初始化
        OneLedgerApplicationHolder.container = container
        OneLedgerApplicationHolderContainer.init(this)
    }
}

class AppContainer(application: Application) {
    private val database = OneLedgerDatabase.getInstance(application)
    val repository: LedgerRepository = OfflineLedgerRepository(database.ledgerDao())
    val autoDetectRepository: AutoDetectRepository =
        AutoDetectRepository(database.ledgerDao(), repository.activeBookId)
}

object OneLedgerApplicationHolder {
    /**
     * 通知与短信监听器需要在无 UI 场景下拿到仓库。
     * 由 [OneLedgerApplication] 在 onCreate 时赋值。
     */
    var container: AppContainer? = null
        internal set

    val activeBookId: String
        get() = container?.repository?.activeBookId?.value.orEmpty()

    val autoDetectRepository: AutoDetectRepository
        get() = requireNotNull(container) {
            "AppContainer 尚未初始化，监听器不应早于 Application.onCreate 启动"
        }.autoDetectRepository
}
