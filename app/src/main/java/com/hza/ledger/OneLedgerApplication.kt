package com.hza.ledger

import android.app.Application
import com.hza.ledger.data.local.OneLedgerDatabase
import com.hza.ledger.data.repository.LedgerRepository
import com.hza.ledger.data.repository.OfflineLedgerRepository

class OneLedgerApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    private val database = OneLedgerDatabase.getInstance(application)
    val repository: LedgerRepository = OfflineLedgerRepository(database.ledgerDao())
}
