package org.commcare.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.commcare.app.engine.FormEntrySession
import org.commcare.app.engine.NavigationStep
import org.commcare.app.engine.SessionNavigatorImpl
import org.javarosa.core.model.FormDef
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.commcare.app.state.AppState
import org.commcare.app.storage.AppRecordRepository
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.viewmodel.CaseItem
import org.commcare.app.viewmodel.CaseListViewModel
import org.commcare.app.viewmodel.CaseSearchViewModel
import org.commcare.app.viewmodel.DrawerViewModel
import org.commcare.app.viewmodel.FormEntryViewModel
import org.commcare.app.viewmodel.FormQueueViewModel
import org.commcare.app.viewmodel.FormRecordViewModel
import org.commcare.app.viewmodel.LanguageViewModel
import org.commcare.app.viewmodel.MenuViewModel
import org.commcare.app.viewmodel.NavigationState
import org.commcare.app.viewmodel.DiagnosticsViewModel
import org.commcare.app.viewmodel.RecoveryViewModel
import org.commcare.app.viewmodel.SettingsViewModel
import org.commcare.app.viewmodel.SyncViewModel
import org.commcare.app.viewmodel.UpdateViewModel
import org.commcare.core.interfaces.createHttpClient

/**
 * Navigation state within the home screen after login.
 */
sealed class HomeNav {
    data object Landing : HomeNav()
    data object InMenu : HomeNav()
    data object InCaseList : HomeNav()
    data object InCaseSearch : HomeNav()
    data object InFormEntry : HomeNav()
    data object InSync : HomeNav()
    data object InSettings : HomeNav()
    data object InDiagnostics : HomeNav()
    data object InRecovery : HomeNav()
}

/**
 * Main home screen that coordinates navigation between menus, case lists, forms, and sync.
 */
@Composable
fun HomeScreen(state: AppState.Ready, db: CommCareDatabase) {
    var nav by remember { mutableStateOf<HomeNav>(HomeNav.Landing) }

    val navigator = remember { SessionNavigatorImpl(state.platform, state.sandbox) }
    val menuViewModel = remember { MenuViewModel(navigator) }
    val httpClient = remember { createHttpClient() }
    val formQueueViewModel = remember {
        FormQueueViewModel(httpClient, state.serverUrl, state.domain, state.authHeader, db).also {
            it.loadFromDatabase()
        }
    }
    val syncViewModel = remember {
        SyncViewModel(httpClient, state.serverUrl, state.domain, state.authHeader, state.sandbox)
    }
    val formRecordViewModel = remember { FormRecordViewModel(db).also { it.loadRecords() } }
    val languageViewModel = remember { LanguageViewModel() }
    val settingsViewModel = remember { SettingsViewModel() }
    val updateViewModel = remember {
        if (state.serverUrl.isNotBlank()) {
            UpdateViewModel(state.sandbox, state.platform, state.serverUrl)
        } else null
    }
    val diagnosticsViewModel = remember {
        DiagnosticsViewModel(httpClient, state.serverUrl, state.domain, state.authHeader)
    }
    val recoveryViewModel = remember { RecoveryViewModel() }

    // Drawer setup
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val appRepository = remember { AppRecordRepository(db) }
    val drawerViewModel = remember { DrawerViewModel(appRepository) }

    LaunchedEffect(Unit) { drawerViewModel.refresh() }

    // Current form entry state (set when navigating to a form)
    var formEntryViewModel by remember { mutableStateOf<FormEntryViewModel?>(null) }
    var caseListViewModel by remember { mutableStateOf<CaseListViewModel?>(null) }
    var caseSearchViewModel by remember { mutableStateOf<CaseSearchViewModel?>(null) }

    // Observe MenuViewModel.navigationState to drive screen transitions
    val menuNavState = menuViewModel.navigationState
    LaunchedEffect(menuNavState) {
        if (nav == HomeNav.InMenu || nav == HomeNav.InCaseList || nav == HomeNav.InCaseSearch) {
            when (menuNavState) {
                is NavigationState.EntitySelect -> {
                    val clvm = CaseListViewModel(navigator, state.sandbox)
                    clvm.loadCases()
                    caseListViewModel = clvm
                    nav = HomeNav.InCaseList
                }
                is NavigationState.CaseSearch -> {
                    val csvm = CaseSearchViewModel(navigator, state.sandbox, httpClient, state.authHeader)
                    csvm.loadSearchConfig()
                    caseSearchViewModel = csvm
                    nav = HomeNav.InCaseSearch
                }
                is NavigationState.FormEntry -> {
                    val fevm = loadFormEntry(navigator, state, languageViewModel)
                    if (fevm != null) {
                        formEntryViewModel = fevm
                        nav = HomeNav.InFormEntry
                    }
                }
                is NavigationState.Menu -> {
                    // Stay on menu screen
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                NavigationDrawerContent(
                    viewModel = drawerViewModel,
                    username = state.domain,
                    onSwitchApp = { /* placeholder — multi-app switching not yet wired */ },
                    onOpportunities = { /* placeholder */ },
                    onMessaging = { /* placeholder */ },
                    onAbout = { /* placeholder */ },
                    onConnectIdAction = { /* placeholder */ },
                    onClose = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
    when (nav) {
        is HomeNav.Landing -> {
            HomeLanding(
                appName = state.app.displayName,
                onStart = {
                    menuViewModel.loadMenus()
                    nav = HomeNav.InMenu
                },
                onSync = {
                    nav = HomeNav.InSync
                },
                onSettings = {
                    nav = HomeNav.InSettings
                },
                onDiagnostics = {
                    nav = HomeNav.InDiagnostics
                },
                pendingFormCount = formQueueViewModel.pendingCount,
                lastSyncTime = syncViewModel.lastSyncTime,
                onOpenDrawer = { scope.launch { drawerState.open() } }
            )
        }

        is HomeNav.InMenu -> {
            MenuScreen(
                viewModel = menuViewModel,
                onBack = {
                    navigator.clearSession()
                    menuViewModel.loadMenus()
                    nav = HomeNav.Landing
                }
            )
        }

        is HomeNav.InCaseList -> {
            val clvm = caseListViewModel
            if (clvm != null) {
                CaseListScreen(
                    viewModel = clvm,
                    onCaseSelected = { caseItem ->
                        when (val result = handleCaseSelection(caseItem, clvm, navigator, state, languageViewModel)) {
                            is CaseSelectionResult.FormReady -> {
                                formEntryViewModel = result.viewModel
                                nav = HomeNav.InFormEntry
                            }
                            is CaseSelectionResult.NextCaseList -> {
                                // Tiered selection: load child case list
                                caseListViewModel = result.viewModel
                                // nav stays InCaseList, but with new ViewModel
                            }
                            is CaseSelectionResult.None -> {
                                // No action
                            }
                        }
                    },
                    onActionSelected = { action ->
                        // Action buttons execute stack operations then navigate
                        try {
                            val ops = action.stackOperations
                            navigator.session.executeStackOperations(ops, navigator.session.getEvaluationContext())
                            when (val step = navigator.getNextStep()) {
                                is NavigationStep.StartForm -> {
                                    val fevm = loadFormEntry(navigator, state, languageViewModel)
                                    if (fevm != null) {
                                        formEntryViewModel = fevm
                                        nav = HomeNav.InFormEntry
                                    }
                                }
                                is NavigationStep.ShowMenu -> {
                                    menuViewModel.loadMenus()
                                    nav = HomeNav.InMenu
                                }
                                else -> { /* ignore */ }
                            }
                        } catch (_: Exception) {
                            // Action execution failed
                        }
                    },
                    onBack = {
                        menuViewModel.goBack()
                        nav = HomeNav.InMenu
                    }
                )
            }
        }

        is HomeNav.InCaseSearch -> {
            val csvm = caseSearchViewModel
            if (csvm != null) {
                CaseSearchScreen(
                    viewModel = csvm,
                    onResultSelected = { caseItem ->
                        csvm.selectResult(caseItem)
                        // After selecting, check next step
                        when (val step = navigator.getNextStep()) {
                            is NavigationStep.StartForm -> {
                                val fevm = loadFormEntry(navigator, state, languageViewModel)
                                if (fevm != null) {
                                    fevm.persistentTileData = buildPersistentTileData(caseItem, navigator, state)
                                    formEntryViewModel = fevm
                                    nav = HomeNav.InFormEntry
                                }
                            }
                            is NavigationStep.ShowCaseList -> {
                                val clvm = CaseListViewModel(navigator, state.sandbox)
                                clvm.loadCases()
                                caseListViewModel = clvm
                                nav = HomeNav.InCaseList
                            }
                            else -> { /* stay */ }
                        }
                    },
                    onBack = {
                        menuViewModel.goBack()
                        nav = HomeNav.InMenu
                    }
                )
            }
        }

        is HomeNav.InFormEntry -> {
            val fevm = formEntryViewModel
            if (fevm != null) {
                FormEntryScreen(
                    viewModel = fevm,
                    onComplete = {
                        val xml = fevm.submitForm(state.sandbox)
                        if (xml != null) {
                            formQueueViewModel.enqueueForm(xml, fevm.formTitle, fevm.getFormXmlns())
                        }
                        // Check for chained forms via session stack
                        val hasNext = navigator.finishAndPop()
                        if (hasNext) {
                            // Chained form: load the next form in the workflow
                            val nextFevm = loadFormEntry(navigator, state, languageViewModel)
                            if (nextFevm != null) {
                                formEntryViewModel = nextFevm
                                // nav stays InFormEntry
                            } else {
                                navigator.clearSession()
                                formEntryViewModel = null
                                nav = HomeNav.Landing
                            }
                        } else {
                            navigator.clearSession()
                            formEntryViewModel = null
                            nav = HomeNav.Landing
                        }
                    },
                    onBack = {
                        navigator.clearSession()
                        formEntryViewModel = null
                        nav = HomeNav.Landing
                    },
                    onSaveDraft = {
                        fevm.saveDraft(formRecordViewModel)
                    },
                    languageViewModel = languageViewModel
                )
            }
        }

        is HomeNav.InSync -> {
            SyncScreen(
                syncViewModel = syncViewModel,
                formQueueViewModel = formQueueViewModel,
                formRecordViewModel = formRecordViewModel,
                onBack = { nav = HomeNav.Landing }
            )
        }

        is HomeNav.InSettings -> {
            SettingsScreen(
                viewModel = settingsViewModel,
                updateViewModel = updateViewModel,
                onBack = { nav = HomeNav.Landing },
                onRecovery = { nav = HomeNav.InRecovery }
            )
        }

        is HomeNav.InDiagnostics -> {
            DiagnosticsScreen(
                viewModel = diagnosticsViewModel,
                lastSyncTime = syncViewModel.lastSyncTime,
                pendingFormCount = formQueueViewModel.pendingCount,
                onBack = { nav = HomeNav.Landing }
            )
        }

        is HomeNav.InRecovery -> {
            RecoveryScreen(
                viewModel = recoveryViewModel,
                onBack = { nav = HomeNav.Landing },
                onClearData = { nav = HomeNav.Landing }
            )
        }
    }
    } // end ModalNavigationDrawer content
}

@Composable
private fun HomeLanding(
    appName: String,
    onStart: () -> Unit,
    onSync: () -> Unit,
    onSettings: () -> Unit,
    onDiagnostics: () -> Unit,
    pendingFormCount: Int,
    lastSyncTime: String?,
    onOpenDrawer: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar with hamburger menu button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\u2630", // hamburger menu character
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .clickable { onOpenDrawer() }
                    .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                    .padding(end = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "CommCare",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = appName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Ready",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onSync,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sync")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onSettings,
                modifier = Modifier.weight(1f)
            ) {
                Text("Settings")
            }
            OutlinedButton(
                onClick = onDiagnostics,
                modifier = Modifier.weight(1f)
            ) {
                Text("Diagnostics")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (pendingFormCount > 0) {
            Text(
                text = "$pendingFormCount unsent form${if (pendingFormCount > 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (lastSyncTime != null) {
            Text(
                text = "Last sync: $lastSyncTime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Load a FormDef from the platform's storage by xmlns, then create a FormEntryViewModel.
 * Returns null if forms aren't installed or the xmlns isn't found.
 */
@Suppress("UNCHECKED_CAST")
private fun loadFormEntry(
    navigator: SessionNavigatorImpl,
    state: AppState.Ready,
    languageViewModel: LanguageViewModel
): FormEntryViewModel? {
    return try {
        val xmlns = navigator.session.getForm() ?: return null
        val storageManager = state.platform.getStorageManager() ?: return null
        val formStorage = storageManager.getStorage(FormDef.STORAGE_KEY) as IStorageUtilityIndexed<FormDef>
        val formDef = formStorage.getRecordForValue("XMLNS", xmlns)
        val session = FormEntrySession(formDef, state.sandbox, state.platform, navigator.session)
        val viewModel = FormEntryViewModel(session)
        viewModel.loadForm()
        viewModel.loadLanguages(languageViewModel)
        viewModel
    } catch (_: Exception) {
        null
    }
}

/**
 * Result of case selection — either a form is ready, another case list is needed, or nothing.
 */
sealed class CaseSelectionResult {
    data class FormReady(val viewModel: FormEntryViewModel) : CaseSelectionResult()
    data class NextCaseList(val viewModel: CaseListViewModel) : CaseSelectionResult()
    data object None : CaseSelectionResult()
}

/**
 * Handle case selection — advance session and potentially start a form or next case list.
 * Supports tiered selection (parent/child) by returning NextCaseList when another datum is needed.
 */
private fun handleCaseSelection(
    caseItem: CaseItem,
    caseListViewModel: CaseListViewModel,
    navigator: SessionNavigatorImpl,
    state: AppState.Ready,
    languageViewModel: LanguageViewModel
): CaseSelectionResult {
    return try {
        val step = caseListViewModel.selectCase(caseItem)
        when (step) {
            is NavigationStep.StartForm -> {
                val fevm = loadFormEntry(navigator, state, languageViewModel)
                if (fevm != null) {
                    fevm.persistentTileData = buildPersistentTileData(caseItem, navigator, state)
                    CaseSelectionResult.FormReady(fevm)
                } else {
                    CaseSelectionResult.None
                }
            }
            is NavigationStep.ShowCaseList -> {
                // Tiered selection: another case list needed (e.g., child cases)
                val childClvm = CaseListViewModel(navigator, state.sandbox)
                childClvm.loadCases()
                CaseSelectionResult.NextCaseList(childClvm)
            }
            else -> CaseSelectionResult.None
        }
    } catch (_: Exception) {
        CaseSelectionResult.None
    }
}

/**
 * Build persistent tile data from the selected case and detail configuration.
 */
private fun buildPersistentTileData(
    caseItem: CaseItem,
    navigator: SessionNavigatorImpl,
    state: AppState.Ready
): PersistentTileData? {
    return try {
        // Build from case properties
        val fields = caseItem.properties.entries
            .filter { it.key != "case_name" && it.key != "case_type" && it.value.isNotBlank() }
            .take(4) // Show at most 4 fields
            .map { (key, value) -> key.replace("_", " ").replaceFirstChar { it.uppercase() } to value }

        PersistentTileData(
            caseName = caseItem.name,
            fields = fields
        )
    } catch (_: Exception) {
        PersistentTileData(caseName = caseItem.name)
    }
}
