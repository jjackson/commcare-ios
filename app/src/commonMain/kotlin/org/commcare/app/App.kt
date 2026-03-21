package org.commcare.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.commcare.app.network.ConnectIdApi
import org.commcare.app.network.ConnectMarketplaceApi
import org.commcare.app.platform.PlatformKeychainStore
import org.commcare.app.state.AppState
import org.commcare.app.storage.AppRecordRepository
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.storage.ConnectIdRepository
import org.commcare.app.ui.AppManagerScreen
import org.commcare.app.ui.EnterCodeScreen
import org.commcare.app.ui.HomeScreen
import org.commcare.app.ui.InstallErrorScreen
import org.commcare.app.ui.InstallFromListScreen
import org.commcare.app.ui.InstallProgressScreen
import org.commcare.app.ui.InstallScreen
import org.commcare.app.ui.LoginScreen
import org.commcare.app.ui.SetupScreen
import org.commcare.app.ui.connect.ConnectScreen
import org.commcare.app.ui.connect.PersonalIdScreen
import org.commcare.app.ui.PinEntryScreen
import org.commcare.app.viewmodel.AppInstallViewModel
import org.commcare.app.viewmodel.AppManagerViewModel
import org.commcare.app.viewmodel.ConnectIdTokenManager
import org.commcare.app.viewmodel.ConnectIdViewModel
import org.commcare.app.viewmodel.DemoModeManager
import org.commcare.app.viewmodel.InstallState
import org.commcare.app.viewmodel.LoginViewModel
import org.commcare.app.viewmodel.SetupStep
import org.commcare.app.viewmodel.SetupViewModel
import org.commcare.app.viewmodel.UserKeyRecordManager

@Composable
fun App(db: CommCareDatabase) {
    val appRepository = remember { AppRecordRepository(db) }
    val connectIdRepository = remember { ConnectIdRepository(db) }
    val connectIdApi = remember { ConnectIdApi() }
    val keychainStore = remember { PlatformKeychainStore() }
    val connectIdViewModel = remember { ConnectIdViewModel(connectIdApi, connectIdRepository, keychainStore) }
    val keyRecordManager = remember { UserKeyRecordManager(db, keychainStore) }
    val loginViewModel = remember {
        LoginViewModel(db).also { it.setKeyRecordManager(keyRecordManager) }
    }
    val demoModeManager = remember { DemoModeManager(db) }
    val setupViewModel = remember { SetupViewModel() }
    val appInstallViewModel = remember { AppInstallViewModel(db, appRepository) }
    val marketplaceApi = remember { ConnectMarketplaceApi() }
    val connectIdTokenManager = remember { ConnectIdTokenManager(connectIdApi, connectIdRepository, keychainStore) }

    // Track whether any apps are installed; starts false on first launch
    val hasApps = remember { mutableStateOf(appRepository.getAppCount() > 0) }
    var showAppManager by remember { mutableStateOf(false) }
    var showPersonalIdRegistration by remember { mutableStateOf(false) }
    var showOpportunities by remember { mutableStateOf(false) }
    // When non-null, ConnectScreen opens at the given tab ("opportunities" or "messaging")
    var connectInitialTab by remember { mutableStateOf("opportunities") }
    var connectIdRegistered by remember { mutableStateOf(connectIdRepository.isRegistered()) }
    // When non-null, an in-progress Connect app download is pending install
    var pendingConnectInstall by remember { mutableStateOf<Pair<String, String>?>(null) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val appState = loginViewModel.appState

            // Show PersonalIdScreen overlay when registration is requested
            if (showPersonalIdRegistration) {
                PersonalIdScreen(
                    viewModel = connectIdViewModel,
                    onComplete = {
                        showPersonalIdRegistration = false
                        connectIdRegistered = connectIdRepository.isRegistered()
                    },
                    onCancel = { showPersonalIdRegistration = false }
                )
                return@Surface
            }

            // Handle a Connect-initiated app install: run the standard install-progress
            // screen, then return to Connect opportunities when done.
            if (pendingConnectInstall != null) {
                val (installUrl, _) = pendingConnectInstall!!
                // Kick off the install if not already running
                if (appInstallViewModel.installState is InstallState.Idle) {
                    appInstallViewModel.install(installUrl)
                }
                when (val installState = appInstallViewModel.installState) {
                    is InstallState.Installing, is InstallState.Failed -> {
                        InstallProgressScreen(
                            viewModel = appInstallViewModel,
                            onCancel = {
                                appInstallViewModel.reset()
                                pendingConnectInstall = null
                                showOpportunities = true
                            },
                            onRetry = {
                                appInstallViewModel.reset()
                                appInstallViewModel.install(installUrl)
                            }
                        )
                    }
                    is InstallState.Completed -> {
                        // Install succeeded — return to Connect screen
                        appInstallViewModel.reset()
                        pendingConnectInstall = null
                        showOpportunities = true
                    }
                    is InstallState.Idle -> { /* waiting for install to start */ }
                }
                return@Surface
            }

            // Show ConnectScreen overlay when requested
            if (showOpportunities) {
                ConnectScreen(
                    api = marketplaceApi,
                    tokenManager = connectIdTokenManager,
                    onBack = { showOpportunities = false },
                    initialTab = connectInitialTab,
                    onDownloadApp = { installUrl, appName ->
                        showOpportunities = false
                        pendingConnectInstall = Pair(installUrl, appName)
                    }
                )
                return@Surface
            }

            // Once an app is seated (NeedsLogin or Ready), flip hasApps so we leave setup flow
            if (appState is AppState.NeedsLogin || appState is AppState.Ready) {
                hasApps.value = true
            }

            // If apps exist in DB but LoginViewModel is still LoggedOut, bridge the gap:
            // load the seated app and transition to NeedsLogin so the app switcher gets data.
            if (hasApps.value && appState is AppState.LoggedOut) {
                val seatedApp = appRepository.getSeatedApp()
                if (seatedApp != null) {
                    val allApps = appRepository.getAllApps()
                    loginViewModel.setReadyState(AppState.NeedsLogin(seatedApp, allApps))
                }
            }

            if (!hasApps.value) {
                // No apps installed — show setup flow (including install progress within it)
                SetupFlow(
                    setupViewModel = setupViewModel,
                    loginViewModel = loginViewModel,
                    appInstallViewModel = appInstallViewModel,
                    hasApps = hasApps,
                    onSignUpPersonalId = { showPersonalIdRegistration = true },
                    isConnectIdRegistered = connectIdRegistered,
                    onConnectOpportunities = { showOpportunities = true }
                )
            } else {
                // Normal routing: login, install, home, etc.
                when (appState) {
                    is AppState.NoAppsInstalled,
                    is AppState.LoggedOut,
                    is AppState.LoginError -> LoginScreen(
                        viewModel = loginViewModel,
                        onDemoMode = {
                            val demoState = demoModeManager.enterDemoMode()
                            if (demoState != null) {
                                loginViewModel.setReadyState(demoState)
                            }
                        },
                        onConnectIdLogin = {
                            showPersonalIdRegistration = true
                        }
                    )
                    is AppState.NeedsLogin -> {
                        // Auto-configure LoginViewModel from the seated app
                        LaunchedEffect(appState.seatedApp.id) {
                            loginViewModel.configureApp(
                                serverUrl = "https://www.commcarehq.org",
                                appId = appState.seatedApp.id,
                                app = appState.seatedApp
                            )
                            loginViewModel.detectLoginMode()
                        }

                        if (showAppManager) {
                            val appManagerViewModel = remember { AppManagerViewModel(appRepository) }
                            AppManagerScreen(
                                viewModel = appManagerViewModel,
                                onBack = { showAppManager = false },
                                onInstallNew = {
                                    showAppManager = false
                                    hasApps.value = false
                                }
                            )
                        } else when (loginViewModel.loginMode) {
                            UserKeyRecordManager.LoginMode.PIN -> {
                                PinEntryScreen(
                                    appName = appState.seatedApp.displayName,
                                    onPinEntered = { pin -> loginViewModel.loginWithPin(pin) },
                                    onForgotPin = { loginViewModel.forgotPin() },
                                    errorMessage = loginViewModel.pinError,
                                    isLoading = loginViewModel.appState is AppState.LoggingIn
                                )
                            }
                            UserKeyRecordManager.LoginMode.BIOMETRIC -> {
                                // Show PIN screen as fallback while biometric triggers
                                PinEntryScreen(
                                    appName = appState.seatedApp.displayName,
                                    onPinEntered = { pin -> loginViewModel.loginWithPin(pin) },
                                    onForgotPin = { loginViewModel.forgotPin() },
                                    errorMessage = loginViewModel.pinError,
                                    isLoading = loginViewModel.appState is AppState.LoggingIn
                                )
                                // Trigger biometric on first composition only
                                LaunchedEffect(Unit) {
                                    loginViewModel.loginWithBiometric()
                                }
                            }
                            UserKeyRecordManager.LoginMode.PASSWORD -> {
                                LoginScreen(
                                    viewModel = loginViewModel,
                                    onDemoMode = {
                                        val demoState = demoModeManager.enterDemoMode()
                                        if (demoState != null) {
                                            loginViewModel.setReadyState(demoState)
                                        }
                                    },
                                    seatedApp = appState.seatedApp,
                                    allApps = appState.allApps,
                                    onSwitchApp = { app ->
                                        appRepository.seatApp(app.id)
                                        loginViewModel.configureApp(
                                            serverUrl = "https://www.commcarehq.org",
                                            appId = app.id,
                                            app = app
                                        )
                                        val allApps = appRepository.getAllApps()
                                        loginViewModel.setReadyState(AppState.NeedsLogin(app, allApps))
                                    },
                                    onAppManager = { showAppManager = true },
                                    onConnectIdLogin = {
                                        showPersonalIdRegistration = true
                                    }
                                )
                            }
                        }
                    }
                    is AppState.LoggingIn -> LoginScreen(loginViewModel)
                    is AppState.Installing -> InstallScreen(appState)
                    is AppState.InstallError -> InstallErrorScreen(appState) {
                        loginViewModel.resetError()
                    }
                    is AppState.Ready -> HomeScreen(
                        state = appState,
                        db = db,
                        onConnectOpportunities = {
                            connectInitialTab = "opportunities"
                            showOpportunities = true
                        },
                        onConnectMessaging = {
                            connectInitialTab = "messaging"
                            showOpportunities = true
                        },
                        keyRecordManager = keyRecordManager
                    )
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
    hasApps: MutableState<Boolean>,
    onSignUpPersonalId: (() -> Unit)? = null,
    isConnectIdRegistered: Boolean = false,
    onConnectOpportunities: (() -> Unit)? = null
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
            },
            onSignUpPersonalId = onSignUpPersonalId,
            isConnectIdRegistered = isConnectIdRegistered,
            onConnectOpportunities = onConnectOpportunities
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
                },
                onSignUpPersonalId = onSignUpPersonalId,
                isConnectIdRegistered = isConnectIdRegistered,
                onConnectOpportunities = onConnectOpportunities
            )
        }
    }
}
