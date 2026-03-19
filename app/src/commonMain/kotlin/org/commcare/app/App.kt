package org.commcare.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.commcare.app.state.AppState
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.ui.HomeScreen
import org.commcare.app.ui.InstallErrorScreen
import org.commcare.app.ui.InstallScreen
import org.commcare.app.ui.LoginScreen
import org.commcare.app.viewmodel.DemoModeManager
import org.commcare.app.viewmodel.LoginViewModel

@Composable
fun App(db: CommCareDatabase) {
    val loginViewModel = remember { LoginViewModel(db) }
    val demoModeManager = remember { DemoModeManager(db) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val state = loginViewModel.appState) {
                is AppState.NoAppsInstalled,
                is AppState.NeedsLogin,
                is AppState.LoggedOut,
                is AppState.LoginError -> LoginScreen(
                    viewModel = loginViewModel,
                    onDemoMode = {
                        val demoState = demoModeManager.enterDemoMode()
                        if (demoState != null) {
                            loginViewModel.setReadyState(demoState)
                        }
                    }
                )
                is AppState.LoggingIn -> LoginScreen(loginViewModel)
                is AppState.Installing -> InstallScreen(state)
                is AppState.InstallError -> InstallErrorScreen(state) {
                    loginViewModel.resetError()
                }
                is AppState.Ready -> HomeScreen(state, db)
                is AppState.AppCorrupted -> InstallErrorScreen(
                    AppState.InstallError("App corrupted: ${state.message}")
                ) { loginViewModel.resetError() }
            }
        }
    }
}
