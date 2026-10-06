package com.hza.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hza.ledger.ui.OneLedgerApp
import com.hza.ledger.ui.OneLedgerViewModel
import com.hza.ledger.ui.OneLedgerViewModelFactory
import com.hza.ledger.ui.theme.OneLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
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
        setContent { OneLedgerRoot() }
    }
}

@Composable
private fun OneLedgerRoot() {
    val application = androidx.compose.ui.platform.LocalContext.current.applicationContext as OneLedgerApplication
    val viewModel: OneLedgerViewModel = viewModel(
        factory = OneLedgerViewModelFactory(application.container.repository),
    )
    OneLedgerTheme(darkTheme = isSystemInDarkTheme()) {
        OneLedgerApp(viewModel = viewModel)
    }
}
