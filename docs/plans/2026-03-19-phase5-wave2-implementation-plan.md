# Phase 5 Wave 2: App Setup Flow — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the first-launch app setup flow — when no app is installed, users see a setup screen (scan QR, enter code, install from list) instead of jumping straight to login. Add ApplicationRecord persistence and DispatchScreen routing.

**Architecture:** Add `application_record` + `seated_app_preference` tables to SQLDelight. Expand `AppState` with `NoAppsInstalled` and `NeedsLogin` variants. Replace `App.kt` routing with a `DispatchScreen` that checks the app registry. New SetupScreen provides QR scan (existing `PlatformBarcodeScanner`), manual code entry, and install-from-list (HQ API). `InstallProgressScreen` replaces the generic `InstallScreen` with step-by-step feedback. After install, app record is persisted and user is routed to login.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, SQLDelight, existing PlatformBarcodeScanner

**Spec:** `docs/plans/2026-03-19-phase5-android-ux-parity-spec.md` (Waves 1-2)

**Working directory:** `/Users/jjackson/emdash-projects/commcare-ios/.claude/worktrees/phase5-wave1`
**JAVA_HOME:** `/opt/homebrew/Cellar/openjdk@17/17.0.18`

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `app/src/commonMain/sqldelight/.../CommCare.sq` | Modify | Add application_record + seated_app_preference tables |
| `app/src/commonMain/.../storage/AppRecordRepository.kt` | Create | SQLDelight queries for app records |
| `app/src/commonMain/.../model/ApplicationRecord.kt` | Create | Data class + AppStatus enum |
| `app/src/commonMain/.../state/AppState.kt` | Modify | Add NoAppsInstalled, NeedsLogin, AppCorrupted; update Ready |
| `app/src/commonMain/.../App.kt` | Modify | Replace routing with DispatchScreen |
| `app/src/commonMain/.../ui/DispatchScreen.kt` | Create | Routing logic (no UI) |
| `app/src/commonMain/.../ui/SetupScreen.kt` | Create | QR scan, enter code, install from list |
| `app/src/commonMain/.../ui/EnterCodeScreen.kt` | Create | Manual URL/code entry |
| `app/src/commonMain/.../ui/InstallFromListScreen.kt` | Create | Authenticate + browse HQ apps |
| `app/src/commonMain/.../ui/InstallProgressScreen.kt` | Create | Step-by-step install progress |
| `app/src/commonMain/.../viewmodel/SetupViewModel.kt` | Create | Setup state, QR result handling |
| `app/src/commonMain/.../viewmodel/AppInstallViewModel.kt` | Create | Install orchestration with granular progress |
| `app/src/commonMain/.../ui/LoginScreen.kt` | Modify | Accept app info for banner display |
| `app/src/commonMain/.../viewmodel/LoginViewModel.kt` | Modify | Accept ApplicationRecord, configure from it |
| `app/src/commonMain/.../ui/HomeScreen.kt` | Modify | Update AppState.Ready usage |
| `app/src/commonMain/.../viewmodel/DemoModeManager.kt` | Modify | Update AppState.Ready construction |

---

## Task 1: ApplicationRecord Model + SQLDelight Schema

**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/model/ApplicationRecord.kt`
- Create: `app/src/commonMain/kotlin/org/commcare/app/storage/AppRecordRepository.kt`
- Modify: `app/src/commonMain/sqldelight/org/commcare/app/storage/CommCare.sq`

- [ ] **Step 1: Create ApplicationRecord data class**

Write `app/src/commonMain/kotlin/org/commcare/app/model/ApplicationRecord.kt`:

```kotlin
package org.commcare.app.model

data class ApplicationRecord(
    val id: String,
    val profileUrl: String,
    val displayName: String,
    val domain: String,
    val majorVersion: Int,
    val minorVersion: Int = 0,
    val status: AppStatus = AppStatus.INSTALLED,
    val resourcesValidated: Boolean = false,
    val installDate: Long,
    val bannerUrl: String? = null,
    val iconUrl: String? = null
) {
    fun isUsable(): Boolean = status == AppStatus.INSTALLED
    fun isArchived(): Boolean = status == AppStatus.ARCHIVED
}

enum class AppStatus { INSTALLED, ARCHIVED }
```

- [ ] **Step 2: Add SQLDelight tables**

Append to `app/src/commonMain/sqldelight/org/commcare/app/storage/CommCare.sq`:

```sql
-- Application registry (Phase 5 Wave 2)
CREATE TABLE IF NOT EXISTS application_record (
    id TEXT PRIMARY KEY,
    profile_url TEXT NOT NULL,
    display_name TEXT NOT NULL,
    domain TEXT NOT NULL,
    major_version INTEGER NOT NULL,
    minor_version INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'INSTALLED',
    resources_validated INTEGER NOT NULL DEFAULT 0,
    install_date INTEGER NOT NULL,
    banner_url TEXT,
    icon_url TEXT
);

CREATE TABLE IF NOT EXISTS seated_app_preference (
    key TEXT PRIMARY KEY DEFAULT 'seated_app',
    app_id TEXT NOT NULL
);

-- Queries
getAllApps:
SELECT * FROM application_record ORDER BY install_date DESC;

getUsableApps:
SELECT * FROM application_record WHERE status = 'INSTALLED' ORDER BY install_date DESC;

getAppById:
SELECT * FROM application_record WHERE id = ?;

getAppCount:
SELECT COUNT(*) FROM application_record WHERE status = 'INSTALLED';

insertApp:
INSERT OR REPLACE INTO application_record(id, profile_url, display_name, domain, major_version, minor_version, status, resources_validated, install_date, banner_url, icon_url)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

updateAppStatus:
UPDATE application_record SET status = ? WHERE id = ?;

deleteApp:
DELETE FROM application_record WHERE id = ?;

getSeatedAppId:
SELECT app_id FROM seated_app_preference WHERE key = 'seated_app';

setSeatedAppId:
INSERT OR REPLACE INTO seated_app_preference(key, app_id) VALUES ('seated_app', ?);
```

- [ ] **Step 3: Create AppRecordRepository**

Write `app/src/commonMain/kotlin/org/commcare/app/storage/AppRecordRepository.kt`:

```kotlin
package org.commcare.app.storage

import org.commcare.app.model.AppStatus
import org.commcare.app.model.ApplicationRecord

class AppRecordRepository(private val db: CommCareDatabase) {

    fun getAllApps(): List<ApplicationRecord> {
        return db.commCareDatabaseQueries.getUsableApps().executeAsList().map { it.toRecord() }
    }

    fun getAppById(id: String): ApplicationRecord? {
        return db.commCareDatabaseQueries.getAppById(id).executeAsOneOrNull()?.toRecord()
    }

    fun getSeatedApp(): ApplicationRecord? {
        val appId = db.commCareDatabaseQueries.getSeatedAppId().executeAsOneOrNull() ?: return null
        return getAppById(appId)
    }

    fun getAppCount(): Long {
        return db.commCareDatabaseQueries.getAppCount().executeAsOne()
    }

    fun insertApp(app: ApplicationRecord) {
        db.commCareDatabaseQueries.insertApp(
            id = app.id,
            profile_url = app.profileUrl,
            display_name = app.displayName,
            domain = app.domain,
            major_version = app.majorVersion.toLong(),
            minor_version = app.minorVersion.toLong(),
            status = app.status.name,
            resources_validated = if (app.resourcesValidated) 1L else 0L,
            install_date = app.installDate,
            banner_url = app.bannerUrl,
            icon_url = app.iconUrl
        )
    }

    fun seatApp(appId: String) {
        db.commCareDatabaseQueries.setSeatedAppId(appId)
    }

    fun archiveApp(appId: String) {
        db.commCareDatabaseQueries.updateAppStatus("ARCHIVED", appId)
    }

    fun deleteApp(appId: String) {
        db.commCareDatabaseQueries.deleteApp(appId)
    }

    private fun GetUsableApps.toRecord() = ApplicationRecord(
        id = id,
        profileUrl = profile_url,
        displayName = display_name,
        domain = domain,
        majorVersion = major_version.toInt(),
        minorVersion = minor_version.toInt(),
        status = AppStatus.valueOf(status),
        resourcesValidated = resources_validated != 0L,
        installDate = install_date,
        bannerUrl = banner_url,
        iconUrl = icon_url
    )

    // Same mapper for getAppById result type
    private fun GetAppById.toRecord() = ApplicationRecord(
        id = id,
        profileUrl = profile_url,
        displayName = display_name,
        domain = domain,
        majorVersion = major_version.toInt(),
        minorVersion = minor_version.toInt(),
        status = AppStatus.valueOf(status),
        resourcesValidated = resources_validated != 0L,
        installDate = install_date,
        bannerUrl = banner_url,
        iconUrl = icon_url
    )
}
```

Note: The exact generated class names from SQLDelight (`GetUsableApps`, `GetAppById`) need to match what SQLDelight generates. Read the generated code after compilation to verify, and adjust mapper names if needed.

- [ ] **Step 4: Verify compilation**

```bash
cd app
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18 ./gradlew generateCommonMainCommCareDatabaseInterface compileKotlinJvm 2>&1 | tail -10
```

Fix any SQLDelight generated class name mismatches in the repository.

- [ ] **Step 5: Commit**

```bash
git add app/src/commonMain/kotlin/org/commcare/app/model/ApplicationRecord.kt \
       app/src/commonMain/kotlin/org/commcare/app/storage/AppRecordRepository.kt \
       app/src/commonMain/sqldelight/
git commit -m "feat: add ApplicationRecord model + SQLDelight schema"
```

---

## Task 2: Expand AppState + Update Consumers

**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/state/AppState.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/LoginViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/DemoModeManager.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/HomeScreen.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/App.kt`

- [ ] **Step 1: Read all consumer files**

Read AppState.kt, LoginViewModel.kt, DemoModeManager.kt, HomeScreen.kt, App.kt to understand every place AppState is constructed or destructured.

- [ ] **Step 2: Update AppState sealed class**

Add new variants and update Ready to include ApplicationRecord:

```kotlin
package org.commcare.app.state

import org.commcare.app.model.ApplicationRecord
import org.commcare.app.storage.SqlDelightUserSandbox
import org.commcare.util.CommCarePlatform

sealed class AppState {
    // No apps installed — show setup screen
    data object NoAppsInstalled : AppState()

    // App(s) installed but no active session — show login
    data class NeedsLogin(
        val seatedApp: ApplicationRecord,
        val allApps: List<ApplicationRecord>
    ) : AppState()

    // Existing states
    data object LoggedOut : AppState()
    data class LoggingIn(val serverUrl: String, val username: String) : AppState()
    data class LoginError(val message: String) : AppState()
    data class Installing(val progress: Float, val statusMessage: String) : AppState()
    data class InstallError(val message: String) : AppState()

    // Active session
    data class Ready(
        val platform: CommCarePlatform,
        val sandbox: SqlDelightUserSandbox,
        val app: ApplicationRecord,
        val serverUrl: String,
        val domain: String,
        val authHeader: String
    ) : AppState()

    // Error/recovery
    data class AppCorrupted(val app: ApplicationRecord, val message: String) : AppState()
}
```

- [ ] **Step 3: Update LoginViewModel**

Every place that creates `AppState.Ready(platform, sandbox, serverUrl, domain, authHeader!!)` must now include an `app: ApplicationRecord` parameter.

For Wave 2, create a temporary `ApplicationRecord` from the login context when no real record exists yet. Add a property:

```kotlin
private var currentApp: ApplicationRecord? = null

fun configureApp(serverUrl: String, appId: String, app: ApplicationRecord? = null) {
    this.serverUrl = serverUrl
    this.appId = appId
    this.currentApp = app
}
```

Then in `installApp()` where `AppState.Ready` is created, use:

```kotlin
val app = currentApp ?: ApplicationRecord(
    id = appId.ifBlank { "unknown" },
    profileUrl = resolvedProfileUrl,
    displayName = platform.getCurrentProfile()?.getDisplayName() ?: "CommCare",
    domain = domain,
    majorVersion = platform.majorVersion,
    minorVersion = platform.minorVersion,
    installDate = System.currentTimeMillis() // or use kotlinx.datetime
)
appState = AppState.Ready(platform, sandbox, app, serverUrl, domain, authHeader!!)
```

Note: Import `org.commcare.app.model.ApplicationRecord`.

- [ ] **Step 4: Update DemoModeManager**

DemoModeManager creates `AppState.Ready` in two places. Add a demo ApplicationRecord:

```kotlin
val demoApp = ApplicationRecord(
    id = "demo",
    profileUrl = "",
    displayName = "Demo Mode",
    domain = "demo",
    majorVersion = 2,
    minorVersion = 53,
    installDate = 0L
)
```

Use it in both `AppState.Ready` constructions.

- [ ] **Step 5: Update HomeScreen**

HomeScreen receives `AppState.Ready` and destructures it. Add `state.app` access where needed (e.g., for the title bar). The existing `state.serverUrl`, `state.domain`, `state.authHeader` usages remain the same.

- [ ] **Step 6: Update App.kt routing**

For now, keep the simple routing but handle the new state variants:

```kotlin
when (val state = loginViewModel.appState) {
    is AppState.NoAppsInstalled -> LoginScreen(loginViewModel, ...) // temporary — Wave 2 Task 4 replaces
    is AppState.NeedsLogin -> LoginScreen(loginViewModel, ...)      // temporary
    is AppState.LoggedOut, is AppState.LoginError -> LoginScreen(loginViewModel, ...)
    is AppState.LoggingIn -> LoginScreen(loginViewModel)
    is AppState.Installing -> InstallScreen(state)
    is AppState.InstallError -> InstallErrorScreen(state) { loginViewModel.resetError() }
    is AppState.Ready -> HomeScreen(state, db)
    is AppState.AppCorrupted -> InstallErrorScreen(...) { loginViewModel.resetError() }
}
```

- [ ] **Step 7: Verify compilation**

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18 ./gradlew compileKotlinIosSimulatorArm64 compileKotlinJvm 2>&1 | tail -10
```

- [ ] **Step 8: Run tests**

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18 ./gradlew jvmTest 2>&1 | tail -5
```

- [ ] **Step 9: Commit**

```bash
git add app/src/commonMain/kotlin/org/commcare/app/state/AppState.kt \
       app/src/commonMain/kotlin/org/commcare/app/viewmodel/LoginViewModel.kt \
       app/src/commonMain/kotlin/org/commcare/app/viewmodel/DemoModeManager.kt \
       app/src/commonMain/kotlin/org/commcare/app/ui/HomeScreen.kt \
       app/src/commonMain/kotlin/org/commcare/app/App.kt
git commit -m "feat: expand AppState with NoAppsInstalled, NeedsLogin, ApplicationRecord"
```

---

## Task 3: DispatchScreen + SetupScreen

**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/ui/DispatchScreen.kt`
- Create: `app/src/commonMain/kotlin/org/commcare/app/ui/SetupScreen.kt`
- Create: `app/src/commonMain/kotlin/org/commcare/app/ui/EnterCodeScreen.kt`
- Create: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/SetupViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/App.kt`

- [ ] **Step 1: Create SetupViewModel**

Manages setup state: which sub-screen is shown, QR scan results, manual code entry.

```kotlin
package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class SetupStep { MAIN, ENTER_CODE, INSTALL_FROM_LIST, SCANNING }

class SetupViewModel {
    var currentStep by mutableStateOf(SetupStep.MAIN)
        private set
    var profileUrl by mutableStateOf("")
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun showEnterCode() { currentStep = SetupStep.ENTER_CODE }
    fun showInstallFromList() { currentStep = SetupStep.INSTALL_FROM_LIST }
    fun showScanning() { currentStep = SetupStep.SCANNING }
    fun backToMain() { currentStep = SetupStep.MAIN; errorMessage = null }

    fun onQrScanned(url: String) {
        profileUrl = url
        currentStep = SetupStep.MAIN
    }

    fun onCodeEntered(url: String) {
        profileUrl = url
    }

    fun clearError() { errorMessage = null }
    fun setError(msg: String) { errorMessage = msg }
}
```

- [ ] **Step 2: Create SetupScreen**

Main setup screen with scan QR, enter code, install from list buttons. Follows the spec's layout.

- [ ] **Step 3: Create EnterCodeScreen**

Simple screen with a text field for profile URL/code and a "Install" button.

- [ ] **Step 4: Create DispatchScreen**

No visible UI — checks app registry and routes:

```kotlin
@Composable
fun DispatchScreen(
    db: CommCareDatabase,
    appRepository: AppRecordRepository,
    loginViewModel: LoginViewModel,
    demoModeManager: DemoModeManager
) {
    val apps = remember { appRepository.getAllApps() }
    val seatedApp = remember { appRepository.getSeatedApp() }

    if (apps.isEmpty()) {
        // No apps installed — show setup
        SetupFlow(db, appRepository, loginViewModel)
    } else if (seatedApp != null && loginViewModel.appState is AppState.Ready) {
        // Active session — show home
        HomeScreen(loginViewModel.appState as AppState.Ready, db)
    } else {
        // App installed, no session — configure and show login
        val app = seatedApp ?: apps.first()
        loginViewModel.configureApp(
            serverUrl = "https://www.commcarehq.org",
            appId = app.id,
            app = app
        )
        LoginScreen(loginViewModel, onDemoMode = { ... })
    }
}
```

- [ ] **Step 5: Update App.kt to use DispatchScreen**

Replace the current `when` block with DispatchScreen as the entry point.

- [ ] **Step 6: Verify compilation and test**

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18 ./gradlew compileKotlinIosSimulatorArm64 compileKotlinJvm 2>&1 | tail -5
```

- [ ] **Step 7: Commit**

```bash
git add app/src/commonMain/kotlin/org/commcare/app/ui/DispatchScreen.kt \
       app/src/commonMain/kotlin/org/commcare/app/ui/SetupScreen.kt \
       app/src/commonMain/kotlin/org/commcare/app/ui/EnterCodeScreen.kt \
       app/src/commonMain/kotlin/org/commcare/app/viewmodel/SetupViewModel.kt \
       app/src/commonMain/kotlin/org/commcare/app/App.kt
git commit -m "feat: add DispatchScreen, SetupScreen, EnterCodeScreen"
```

---

## Task 4: InstallProgressScreen + AppInstallViewModel

**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/ui/InstallProgressScreen.kt`
- Create: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/AppInstallViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/engine/AppInstaller.kt`

- [ ] **Step 1: Create AppInstallViewModel**

Orchestrates app installation with granular progress reporting. Persists the ApplicationRecord on success.

```kotlin
package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.commcare.app.engine.AppInstaller
import org.commcare.app.model.AppStatus
import org.commcare.app.model.ApplicationRecord
import org.commcare.app.storage.AppRecordRepository
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.storage.SqlDelightUserSandbox

data class InstallStep(val label: String, val status: StepStatus)
enum class StepStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED }

sealed class InstallState {
    object Idle : InstallState()
    data class Installing(val steps: List<InstallStep>, val appName: String) : InstallState()
    data class Completed(val app: ApplicationRecord) : InstallState()
    data class Failed(val message: String) : InstallState()
}

class AppInstallViewModel(
    private val db: CommCareDatabase,
    private val appRepository: AppRecordRepository
) {
    var installState by mutableStateOf<InstallState>(InstallState.Idle)
        private set

    private val scope = CoroutineScope(Dispatchers.Default)

    fun install(profileUrl: String) {
        val steps = mutableListOf(
            InstallStep("Downloading profile", StepStatus.PENDING),
            InstallStep("Installing suite", StepStatus.PENDING),
            InstallStep("Downloading forms", StepStatus.PENDING),
            InstallStep("Loading locale files", StepStatus.PENDING),
            InstallStep("Initializing", StepStatus.PENDING)
        )
        installState = InstallState.Installing(steps.toList(), "Installing...")

        scope.launch {
            try {
                val sandbox = SqlDelightUserSandbox(db)
                val installer = AppInstaller(sandbox)
                val platform = installer.install(profileUrl) { progress, message ->
                    val stepIndex = when {
                        progress < 0.2f -> 0
                        progress < 0.3f -> 1
                        progress < 0.8f -> 2
                        progress < 0.9f -> 3
                        else -> 4
                    }
                    val updatedSteps = steps.mapIndexed { i, step ->
                        when {
                            i < stepIndex -> step.copy(status = StepStatus.COMPLETED)
                            i == stepIndex -> step.copy(status = StepStatus.IN_PROGRESS)
                            else -> step
                        }
                    }
                    installState = InstallState.Installing(updatedSteps, message)
                }

                val profile = platform.getCurrentProfile()
                val app = ApplicationRecord(
                    id = profile?.getUniqueId() ?: profileUrl.hashCode().toString(),
                    profileUrl = profileUrl,
                    displayName = profile?.getDisplayName() ?: "CommCare App",
                    domain = extractDomainFromUrl(profileUrl),
                    majorVersion = platform.majorVersion,
                    minorVersion = platform.minorVersion,
                    status = AppStatus.INSTALLED,
                    resourcesValidated = true,
                    installDate = currentTimeMillis()
                )

                appRepository.insertApp(app)
                appRepository.seatApp(app.id)
                installState = InstallState.Completed(app)
            } catch (e: Exception) {
                installState = InstallState.Failed("Installation failed: ${e.message}")
            }
        }
    }

    fun reset() {
        installState = InstallState.Idle
    }

    private fun extractDomainFromUrl(url: String): String {
        // Extract domain from profile URL like .../a/DOMAIN/apps/download/...
        val match = Regex("/a/([^/]+)/").find(url)
        return match?.groupValues?.get(1) ?: "unknown"
    }

    private fun currentTimeMillis(): Long {
        return kotlin.system.getTimeMillis()
    }
}
```

- [ ] **Step 2: Create InstallProgressScreen**

Step-by-step progress display with checkmarks, spinner, and pending indicators.

- [ ] **Step 3: Wire into SetupScreen/DispatchScreen flow**

When user submits a profile URL (from QR scan or manual entry), start the install via AppInstallViewModel. On completion, update DispatchScreen to route to login.

- [ ] **Step 4: Verify compilation and test**

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18 ./gradlew compileKotlinIosSimulatorArm64 compileKotlinJvm jvmTest 2>&1 | tail -10
```

- [ ] **Step 5: Commit**

```bash
git add app/src/commonMain/kotlin/org/commcare/app/ui/InstallProgressScreen.kt \
       app/src/commonMain/kotlin/org/commcare/app/viewmodel/AppInstallViewModel.kt
git commit -m "feat: add InstallProgressScreen with step-by-step progress"
```

---

## Task 5: InstallFromListScreen (HQ App Browser)

**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/ui/InstallFromListScreen.kt`

- [ ] **Step 1: Create InstallFromListScreen**

A screen that:
1. Shows username + password fields (for HQ authentication)
2. On submit, calls `GET https://www.commcarehq.org/phone/list_apps` with Basic Auth
3. Parses the response (list of available apps with name, domain, profileRef)
4. Displays the list for selection
5. On selection, returns the profileRef URL to start installation

The `/phone/list_apps` endpoint returns a simple XML or JSON list. If the response format is unknown, implement a basic HTTP fetch and parse. The Android app hits both `commcarehq.org/phone/list_apps` and `india.commcarehq.org/phone/list_apps`.

- [ ] **Step 2: Wire into SetupScreen flow**

When "Install from App List" is tapped on SetupScreen, navigate to InstallFromListScreen. On app selection, trigger installation.

- [ ] **Step 3: Verify compilation**

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18 ./gradlew compileKotlinIosSimulatorArm64 compileKotlinJvm 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/org/commcare/app/ui/InstallFromListScreen.kt
git commit -m "feat: add InstallFromListScreen for HQ app browsing"
```

---

## Task 6: Full Build + E2E Verification

**Files:** None (verification only)

- [ ] **Step 1: Build iOS framework**

```bash
cd app
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18 ./gradlew linkDebugFrameworkIosSimulatorArm64 2>&1 | tail -5
```

- [ ] **Step 2: Build Xcode project**

```bash
cd iosApp
xcodebuild -project CommCare.xcodeproj -scheme CommCare -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' build 2>&1 | tail -5
```

- [ ] **Step 3: Fresh install and verify setup flow**

Uninstall the app (to clear data), reinstall, and verify:
- First launch shows SetupScreen (not login)
- Can enter a profile URL manually
- Install progress shows step-by-step
- After install, routes to login screen
- Login works

- [ ] **Step 4: Run all tests**

```bash
cd ../commcare-core
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18 ./gradlew iosSimulatorArm64Test 2>&1 | tail -5
cd ../app
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18 ./gradlew jvmTest 2>&1 | tail -5
```

- [ ] **Step 5: Commit any cleanup**

---

## Task 7: PR and Merge

- [ ] **Step 1: Push and create PR**

```bash
git push -u origin worktree-phase5-wave1
gh pr create --title "feat: Phase 5 Wave 2 — app setup flow + ApplicationRecord" --body "..."  --base main
```

- [ ] **Step 2: Merge**

```bash
gh pr merge --squash
```
