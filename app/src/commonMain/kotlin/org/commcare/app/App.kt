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
import org.commcare.app.state.AppState
import org.commcare.app.storage.CommCareDatabase
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
import org.commcare.app.viewmodel.InstallState
import org.commcare.app.viewmodel.SetupStep
import org.commcare.app.viewmodel.SetupViewModel
import org.commcare.app.viewmodel.UserKeyRecordManager

@Composable
fun App(db: CommCareDatabase) {
    val deps = remember { AppDependencies(db) }
    val appRepository = deps.appRepository
    val loginViewModel = deps.loginViewModel
    val appInstallViewModel = deps.appInstallViewModel

    // Track whether any apps are installed; starts false on first launch
    val hasApps = remember { mutableStateOf(appRepository.getAppCount() > 0) }
    var showAppManager by remember { mutableStateOf(false) }
    var showPersonalIdRegistration by remember { mutableStateOf(false) }
    var showOpportunities by remember { mutableStateOf(false) }
    // When non-null, ConnectScreen opens at the given tab ("opportunities" or "messaging")
    var connectInitialTab by remember { mutableStateOf("opportunities") }
    var connectIdRegistered by remember { mutableStateOf(deps.connectIdRepository.isRegistered()) }
    // When non-null, an in-progress Connect app download is pending install
    // Triple: (installUrl, appName, ccDomain)
    var pendingConnectInstall by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val appState = loginViewModel.appState

            // Show PersonalIdScreen overlay when registration is requested
            if (showPersonalIdRegistration) {
                PersonalIdScreen(
                    viewModel = deps.connectIdViewModel,
                    onComplete = {
                        showPersonalIdRegistration = false
                        connectIdRegistered = deps.connectIdRepository.isRegistered()
                    },
                    onCancel = { showPersonalIdRegistration = false }
                )
                return@Surface
            }

            // Handle a Connect-initiated app install: run the standard install-progress
            // screen, then return to Connect opportunities when done.
            if (pendingConnectInstall != null) {
                val (installUrl, _, connectDomain) = pendingConnectInstall!!

                // Kick off the install via LaunchedEffect (not inline during
                // composition) to avoid CMP iOS recomposition issues (#416, #433).
                LaunchedEffect(installUrl) {
                    if (appInstallViewModel.installState is InstallState.Idle) {
                        appInstallViewModel.install(installUrl)
                    }
                }

                // Handle completion via LaunchedEffect to avoid inline state
                // mutation during composition (CMP iOS doesn't reliably
                // trigger recomposition for inline mutations).
                LaunchedEffect(appInstallViewModel.installState) {
                    if (appInstallViewModel.installState is InstallState.Completed) {
                        appInstallViewModel.reset()

                        // Attempt SSO auto-login: get HQ token and restore
                        val ssoUsername = deps.connectIdTokenManager.getStoredUsername() ?: ""
                        val ssoToken = if (ssoUsername.isNotBlank()) {
                            try {
                                deps.connectIdTokenManager.getHqSsoToken(
                                    hqUrl = "https://www.commcarehq.org",
                                    domain = connectDomain,
                                    hqUsername = ssoUsername
                                )
                            } catch (_: Exception) { null }
                        } else null

                        if (ssoToken != null) {
                            // Seat the newly installed app and auto-login
                            val seatedApp = appRepository.getSeatedApp()
                            if (seatedApp != null) {
                                loginViewModel.configureApp(
                                    serverUrl = "https://www.commcarehq.org",
                                    appId = seatedApp.id,
                                    app = seatedApp
                                )
                            }
                            pendingConnectInstall = null
                            loginViewModel.loginWithSsoToken(ssoToken, connectDomain)
                        } else {
                            // Fallback: return to opportunities list
                            pendingConnectInstall = null
                            showOpportunities = true
                        }
                    }
                }

                when (appInstallViewModel.installState) {
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
                    else -> {
                        // Idle or Completed — show a loading indicator while waiting
                        // for install to start or completion to be processed
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                }
                return@Surface
            }

            // Show ConnectScreen overlay when requested
            if (showOpportunities) {
                ConnectScreen(
                    api = deps.marketplaceApi,
                    tokenManager = deps.connectIdTokenManager,
                    onBack = { showOpportunities = false },
                    initialTab = connectInitialTab,
                    onDownloadApp = { installUrl, appName, domain ->
                        showOpportunities = false
                        pendingConnectInstall = Triple(installUrl, appName, domain)
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
            // This must run as a LaunchedEffect (not inline during composition) because
            // state mutations during composition don't reliably trigger recomposition
            // on Compose Multiplatform iOS — see #416.
            LaunchedEffect(hasApps.value, appState) {
                if (hasApps.value && appState is AppState.LoggedOut) {
                    val seatedApp = appRepository.getSeatedApp()
                    if (seatedApp != null) {
                        val allApps = appRepository.getAllApps()
                        loginViewModel.configureApp(
                            serverUrl = "https://www.commcarehq.org",
                            appId = seatedApp.id,
                            app = seatedApp
                        )
                        loginViewModel.setReadyState(AppState.NeedsLogin(seatedApp, allApps))
                    }
                }
            }

            if (!hasApps.value) {
                // No apps installed — show setup flow (including install progress within it)
                SetupFlow(
                    setupViewModel = deps.setupViewModel,
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
                            val demoState = deps.demoModeManager.enterDemoMode()
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
                                    // Reset both hasApps AND appState so the setup flow
                                    // renders. Without resetting appState, the line-120
                                    // override (NeedsLogin → hasApps=true) immediately
                                    // overrides hasApps=false on the next composition.
                                    loginViewModel.setReadyState(AppState.LoggedOut)
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
                                // Trigger system biometric prompt on first composition.
                                // On success, auto-login with the stored encrypted password.
                                // On failure/cancel, the PIN screen is already showing as fallback.
                                val biometricAuth = remember { org.commcare.app.platform.PlatformBiometricAuth() }
                                LaunchedEffect(Unit) {
                                    if (biometricAuth.canAuthenticate()) {
                                        biometricAuth.authenticate("Unlock CommCare") { result ->
                                            when (result) {
                                                org.commcare.app.platform.BiometricResult.Success -> {
                                                    loginViewModel.loginWithBiometric()
                                                }
                                                org.commcare.app.platform.BiometricResult.Cancelled -> {
                                                    // User cancelled — PIN screen already visible
                                                }
                                                is org.commcare.app.platform.BiometricResult.Failure -> {
                                                    loginViewModel.pinError = "Biometric failed: ${result.message}"
                                                }
                                                org.commcare.app.platform.BiometricResult.Unavailable -> {
                                                    loginViewModel.forgotPin() // fall back to password
                                                }
                                            }
                                        }
                                    } else {
                                        // Biometric not available — use stored password directly
                                        loginViewModel.loginWithBiometric()
                                    }
                                }
                            }
                            UserKeyRecordManager.LoginMode.PASSWORD -> {
                                LoginScreen(
                                    viewModel = loginViewModel,
                                    onDemoMode = {
                                        val demoState = deps.demoModeManager.enterDemoMode()
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
                    is AppState.Ready -> {
                        // Biometric enrollment offer after first password login
                        if (loginViewModel.showBiometricEnrollment) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { loginViewModel.showBiometricEnrollment = false },
                                title = { androidx.compose.material3.Text("Enable Face ID?") },
                                text = {
                                    androidx.compose.material3.Text(
                                        "Would you like to use Face ID to log in faster next time?"
                                    )
                                },
                                confirmButton = {
                                    androidx.compose.material3.TextButton(
                                        onClick = {
                                            // Biometric is already primed by primeQuickLogin —
                                            // the encrypted password is stored. Just dismiss.
                                            loginViewModel.showBiometricEnrollment = false
                                        }
                                    ) {
                                        androidx.compose.material3.Text("Enable")
                                    }
                                },
                                dismissButton = {
                                    androidx.compose.material3.TextButton(
                                        onClick = {
                                            loginViewModel.showBiometricEnrollment = false
                                        }
                                    ) {
                                        androidx.compose.material3.Text("Not Now")
                                    }
                                }
                            )
                        }
                        HomeScreen(
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
                        onConnectIdSignIn = {
                            showPersonalIdRegistration = true
                        },
                        onConnectIdSignOut = {
                            deps.connectIdRepository.deleteUser()
                            deps.keychainStore.delete("connect_username")
                            deps.keychainStore.delete("connect_password")
                            deps.keychainStore.delete("connect_access_token")
                            deps.keychainStore.delete("connect_token_expiry")
                            deps.keychainStore.delete("connect_db_key")
                            connectIdRegistered = false
                        },
                        keyRecordManager = deps.keyRecordManager
                    )
                    }
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
