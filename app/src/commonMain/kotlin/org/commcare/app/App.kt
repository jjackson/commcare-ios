package org.commcare.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.commcare.app.state.AppState
import org.commcare.app.storage.AppRecordRepository
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.ui.EnterCodeScreen
import org.commcare.app.ui.HomeScreen
import org.commcare.app.ui.InstallErrorScreen
import org.commcare.app.ui.InstallFromListScreen
import org.commcare.app.ui.InstallProgressScreen
import org.commcare.app.ui.InstallScreen
import org.commcare.app.ui.LoginScreen
import org.commcare.app.ui.SetupScreen
import org.commcare.app.viewmodel.AppInstallViewModel
import org.commcare.app.viewmodel.DemoModeManager
import org.commcare.app.viewmodel.InstallState
import org.commcare.app.viewmodel.LoginViewModel
import org.commcare.app.viewmodel.SetupStep
import org.commcare.app.viewmodel.SetupViewModel

@Composable
fun App(db: CommCareDatabase) {
    val appRepository = remember { AppRecordRepository(db) }
    val loginViewModel = remember { LoginViewModel(db) }
    val demoModeManager = remember { DemoModeManager(db) }
    val setupViewModel = remember { SetupViewModel() }
    val appInstallViewModel = remember { AppInstallViewModel(db, appRepository) }

    // Track whether any apps are installed; starts false on first launch
    val hasApps = remember { mutableStateOf(appRepository.getAppCount() > 0) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val appState = loginViewModel.appState

            // Once an app is seated (NeedsLogin or Ready), flip hasApps so we leave setup flow
            if (appState is AppState.NeedsLogin || appState is AppState.Ready) {
                hasApps.value = true
            }

            if (!hasApps.value) {
                // No apps installed — show setup flow (including install progress within it)
                SetupFlow(
                    setupViewModel = setupViewModel,
                    loginViewModel = loginViewModel,
                    appInstallViewModel = appInstallViewModel,
                    hasApps = hasApps
                )
            } else {
                // Normal routing: login, install, home, etc.
                when (appState) {
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
                    is AppState.Installing -> InstallScreen(appState)
                    is AppState.InstallError -> InstallErrorScreen(appState) {
                        loginViewModel.resetError()
                    }
                    is AppState.Ready -> HomeScreen(appState, db)
                    is AppState.AppCorrupted -> InstallErrorScreen(
                        AppState.InstallError("App corrupted: ${appState.message}")
                    ) { loginViewModel.resetError() }
                }
            }
        }
    }
}

@Composable
private fun SetupFlow(
    setupViewModel: SetupViewModel,
    loginViewModel: LoginViewModel,
    appInstallViewModel: AppInstallViewModel,
    hasApps: MutableState<Boolean>
) {
    // React to AppInstallViewModel state transitions
    when (val installState = appInstallViewModel.installState) {
        is InstallState.Installing, is InstallState.Failed -> {
            // Show the new step-by-step install progress screen
            InstallProgressScreen(
                viewModel = appInstallViewModel,
                onCancel = {
                    appInstallViewModel.reset()
                    setupViewModel.backToMain()
                },
                onRetry = {
                    appInstallViewModel.reset()
                    // Re-run install with the same URL stored in setupViewModel
                    if (setupViewModel.profileUrl.isNotBlank()) {
                        appInstallViewModel.install(setupViewModel.profileUrl)
                    }
                }
            )
            return
        }
        is InstallState.Completed -> {
            // Install succeeded — flip hasApps to leave setup flow
            hasApps.value = true
            return
        }
        is InstallState.Idle -> { /* fall through to setup navigation */ }
    }

    // If loginViewModel's older install path is still active (e.g. login→install flow),
    // show the legacy install screens so they aren't broken.
    when (val loginState = loginViewModel.appState) {
        is AppState.Installing -> {
            InstallScreen(loginState)
            return
        }
        is AppState.InstallError -> {
            InstallErrorScreen(loginState) {
                loginViewModel.resetError()
                setupViewModel.backToMain()
            }
            return
        }
        else -> { /* fall through to setup navigation */ }
    }

    when (setupViewModel.currentStep) {
        SetupStep.MAIN -> SetupScreen(
            setupViewModel = setupViewModel,
            onInstall = { profileUrl ->
                appInstallViewModel.install(profileUrl)
            }
        )
        SetupStep.ENTER_CODE -> EnterCodeScreen(
            setupViewModel = setupViewModel,
            onNavigateBack = { setupViewModel.backToMain() }
        )
        SetupStep.INSTALL_FROM_LIST -> {
            InstallFromListScreen(
                onAppSelected = { profileUrl ->
                    setupViewModel.onCodeEntered(profileUrl)
                    setupViewModel.backToMain()
                    appInstallViewModel.install(profileUrl)
                },
                onNavigateBack = { setupViewModel.backToMain() }
            )
        }
        SetupStep.SCANNING -> {
            // Scanning is launched imperatively via PlatformBarcodeScanner from SetupScreen,
            // not as a navigation destination — return to main
            setupViewModel.backToMain()
            SetupScreen(
                setupViewModel = setupViewModel,
                onInstall = { profileUrl ->
                    appInstallViewModel.install(profileUrl)
                }
            )
        }
    }
}
