package org.commcare.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.commcare.app.state.AppState
import org.commcare.app.ui.HomeScreen
import org.commcare.app.ui.InstallErrorScreen
import org.commcare.app.ui.InstallScreen
import org.commcare.app.ui.LoginScreen
import org.commcare.app.viewmodel.LoginViewModel

@Composable
fun App() {
    val loginViewModel = remember { LoginViewModel() }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val state = loginViewModel.appState) {
                is AppState.LoggedOut,
                is AppState.LoginError -> LoginScreen(loginViewModel)
                is AppState.LoggingIn -> LoginScreen(loginViewModel)
                is AppState.Installing -> InstallScreen(state)
                is AppState.InstallError -> InstallErrorScreen(state) {
                    loginViewModel.resetError()
                }
                is AppState.Ready -> HomeScreen()
            }
        }
    }
}
