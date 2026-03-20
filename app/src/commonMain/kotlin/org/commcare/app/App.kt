package org.commcare.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.commcare.app.platform.PlatformKeychainStore
import org.commcare.app.state.AppState
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.ui.HomeScreen
import org.commcare.app.ui.InstallErrorScreen
import org.commcare.app.ui.InstallScreen
import org.commcare.app.ui.LoginScreen
import org.commcare.app.ui.PinEntryScreen
import org.commcare.app.viewmodel.DemoModeManager
import org.commcare.app.viewmodel.LoginViewModel
import org.commcare.app.viewmodel.UserKeyRecordManager

@Composable
fun App(db: CommCareDatabase) {
    val loginViewModel = remember { LoginViewModel(db) }
    val demoModeManager = remember { DemoModeManager(db) }
    val keyRecordManager = remember {
        UserKeyRecordManager(db, PlatformKeychainStore())
    }

    // Wire key record manager into login view model
    remember {
        loginViewModel.setKeyRecordManager(keyRecordManager)
        true // remember requires a return value
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val state = loginViewModel.appState) {
                is AppState.LoggedOut,
                is AppState.LoginError -> {
                    // Detect login mode (PIN/biometric/password) for returning users
                    val mode = loginViewModel.loginMode
                    if (mode == UserKeyRecordManager.LoginMode.PIN) {
                        PinEntryScreen(
                            appName = "CommCare",
                            onPinEntered = { pin -> loginViewModel.loginWithPin(pin) },
                            onForgotPin = { loginViewModel.forgotPin() },
                            errorMessage = loginViewModel.pinError,
                            isLoading = loginViewModel.appState is AppState.LoggingIn
                        )
                    } else {
                        LoginScreen(
                            viewModel = loginViewModel,
                            onDemoMode = {
                                val demoState = demoModeManager.enterDemoMode()
                                if (demoState != null) {
                                    loginViewModel.setReadyState(demoState)
                                }
                            }
                        )
                    }
                }
                is AppState.LoggingIn -> LoginScreen(loginViewModel)
                is AppState.Installing -> InstallScreen(state)
                is AppState.InstallError -> InstallErrorScreen(state) {
                    loginViewModel.resetError()
                }
                is AppState.Ready -> HomeScreen(state, db, keyRecordManager)
            }
        }
    }
}
