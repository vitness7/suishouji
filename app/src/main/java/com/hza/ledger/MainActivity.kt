package com.hza.ledger

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hza.ledger.autodetect.PendingNotifier
import com.hza.ledger.ui.OneLedgerApp
import com.hza.ledger.ui.OneLedgerViewModel
import com.hza.ledger.ui.OneLedgerViewModelFactory
import com.hza.ledger.ui.theme.OneLedgerTheme

class MainActivity : ComponentActivity() {

    /** 从通知点击进来时，首次组合直接把用户送到待确认页。 */
    private var openPendingOnStart by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        openPendingOnStart = intent?.getBooleanExtra(EXTRA_OPEN_PENDING, false) == true

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent { OneLedgerRoot(openPendingOnStart) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_PENDING, false)) {
            openPendingOnStart = true
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到前台即消费掉一次性跳转，避免每次返回都强制跳页
        openPendingOnStart = false
        PendingNotifier.cancel(this)
    }

    companion object {
        /** 通知点击时携带的 extra，值恒为 true。 */
        const val EXTRA_OPEN_PENDING = "open_pending"
    }
}

@Composable
private fun OneLedgerRoot(openPendingOnStart: Boolean) {
    val context = LocalContext.current
    val application = context.applicationContext as OneLedgerApplication

    val viewModel: OneLedgerViewModel = viewModel(
        factory = OneLedgerViewModelFactory(
            repository = application.container.repository,
            autoDetectRepository = application.container.autoDetectRepository,
        ),
    )

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 识别出候选时主动发系统提醒：自动记账的价值在于"不用记得记"，
    // 若还得用户主动去翻，价值就折损了一半。
    LaunchedEffect(state.pendingTransactions.size) {
        if (state.pendingTransactions.isNotEmpty()) {
            PendingNotifier.ensureChannel(context)
        }
    }

    OneLedgerTheme(darkTheme = isSystemInDarkTheme()) {
        OneLedgerApp(
            viewModel = viewModel,
            openPendingOnStart = openPendingOnStart,
        )
    }
}
