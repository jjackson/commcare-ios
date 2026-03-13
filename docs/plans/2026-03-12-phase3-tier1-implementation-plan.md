# Phase 3 Tier 1: Minimum Viable App — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** One complete user journey (login → install → menu → case → form → submit → sync) working end-to-end on both Android and iOS.

**Architecture:** Compose Multiplatform app targeting both Android and iOS, using SQLDelight for cross-platform storage. ViewModels in commonMain call commcare-core engine APIs directly. Platform-specific code limited to expect/actual for DB drivers, HTTP, and crypto.

**Tech Stack:** Kotlin 2.0.21, Compose Multiplatform 1.7.3, SQLDelight 2.0.2, commcare-core KMP engine

**Reference docs:**
- Design: `docs/plans/2026-03-12-phase3-feature-implementation-design.md`
- Engine APIs: `commcare-core/src/commonMain/kotlin/org/javarosa/form/api/FormEntryController.kt`
- Session: `commcare-core/src/commonMain/kotlin/org/commcare/session/CommCareSession.kt`
- Resources: `commcare-core/src/commonMain/kotlin/org/commcare/resources/ResourceManager.kt`

---

## Task 1: Add Android Target to App Module

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/settings.gradle.kts`
- Create: `app/src/androidMain/AndroidManifest.xml`
- Create: `app/src/androidMain/kotlin/org/commcare/app/android/MainActivity.kt`

**Step 1: Update build.gradle.kts with Android target**

Add the Android Gradle plugin and target. The key changes:

```kotlin
plugins {
    kotlin("multiplatform") version "2.0.21"
    id("com.android.application") version "8.2.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

kotlin {
    jvmToolchain(17)

    androidTarget()

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "CommCareApp"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.commcare:commcare-core")
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.9.3")
            }
        }

        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }
    }
}

android {
    namespace = "org.commcare.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.commcare.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
}
```

**Step 2: Create AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:label="CommCare"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".android.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

**Step 3: Create MainActivity.kt**

```kotlin
package org.commcare.app.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.commcare.app.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
```

**Step 4: Update settings.gradle.kts**

Change project name from `commcare-ios-app` to `commcare-app` and add google() repo for Android plugin:

```kotlin
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "commcare-app"

includeBuild("../commcare-core") {
    dependencySubstitution {
        substitute(module("org.commcare:commcare-core")).using(project(":"))
    }
}
```

**Step 5: Verify both targets compile**

Run from `app/`:
```bash
cd app && ./gradlew compileKotlinAndroid && echo "Android OK"
cd app && ./gradlew compileKotlinIosSimulatorArm64 && echo "iOS OK"
```

Expected: Both compile successfully. The existing Compose screens work unchanged because they're in commonMain.

**Step 6: Commit**

```bash
git add app/
git commit -m "feat: add Android target to app module"
```

---

## Task 2: Add SQLDelight Storage Infrastructure

**Files:**
- Modify: `app/build.gradle.kts` (add SQLDelight plugin + deps)
- Create: `app/src/commonMain/sqldelight/org/commcare/app/storage/CommCare.sq`
- Create: `app/src/commonMain/kotlin/org/commcare/app/storage/DatabaseDriverFactory.kt` (expect)
- Create: `app/src/androidMain/kotlin/org/commcare/app/storage/DatabaseDriverFactory.kt` (actual)
- Create: `app/src/iosMain/kotlin/org/commcare/app/storage/DatabaseDriverFactory.kt` (actual)

**Step 1: Add SQLDelight to build.gradle.kts**

Add plugin and dependencies:

```kotlin
plugins {
    // ... existing plugins
    id("app.cash.sqldelight") version "2.0.2"
}

// Add after kotlin {} block:
sqldelight {
    databases {
        create("CommCareDatabase") {
            packageName.set("org.commcare.app.storage")
        }
    }
}

// In sourceSets:
val commonMain by getting {
    dependencies {
        // ... existing deps
        implementation("app.cash.sqldelight:runtime:2.0.2")
        implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")
    }
}

val androidMain by getting {
    dependencies {
        // ... existing deps
        implementation("app.cash.sqldelight:android-driver:2.0.2")
    }
}

// In iosMain:
val iosMain by creating {
    // ... existing config
    dependencies {
        implementation("app.cash.sqldelight:native-driver:2.0.2")
    }
}
```

**Step 2: Create SQL schema**

File: `app/src/commonMain/sqldelight/org/commcare/app/storage/CommCare.sq`

```sql
-- Case storage
CREATE TABLE cases (
    case_id TEXT PRIMARY KEY NOT NULL,
    case_type TEXT NOT NULL,
    case_name TEXT NOT NULL,
    owner_id TEXT NOT NULL,
    date_opened TEXT NOT NULL,
    closed INTEGER NOT NULL DEFAULT 0,
    user_id TEXT,
    external_id TEXT
);

CREATE TABLE case_properties (
    case_id TEXT NOT NULL,
    property_key TEXT NOT NULL,
    property_value TEXT NOT NULL,
    PRIMARY KEY (case_id, property_key),
    FOREIGN KEY (case_id) REFERENCES cases(case_id) ON DELETE CASCADE
);

CREATE TABLE case_indices (
    case_id TEXT NOT NULL,
    index_name TEXT NOT NULL,
    target_case_id TEXT NOT NULL,
    target_case_type TEXT NOT NULL,
    relationship TEXT NOT NULL DEFAULT 'child',
    PRIMARY KEY (case_id, index_name),
    FOREIGN KEY (case_id) REFERENCES cases(case_id) ON DELETE CASCADE
);

-- User storage
CREATE TABLE users (
    user_id TEXT PRIMARY KEY NOT NULL,
    username TEXT NOT NULL,
    domain TEXT NOT NULL,
    password_hash TEXT,
    sync_token TEXT
);

-- Fixture storage
CREATE TABLE fixtures (
    fixture_id TEXT PRIMARY KEY NOT NULL,
    fixture_type TEXT NOT NULL,
    user_id TEXT,
    xml_content TEXT NOT NULL
);

-- Form queue
CREATE TABLE form_queue (
    form_id TEXT PRIMARY KEY NOT NULL,
    xmlns TEXT NOT NULL,
    xml_content TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    created_at TEXT NOT NULL,
    submitted_at TEXT
);

-- Resource table
CREATE TABLE resources (
    resource_id TEXT PRIMARY KEY NOT NULL,
    version INTEGER NOT NULL,
    resource_type TEXT NOT NULL,
    status TEXT NOT NULL,
    location TEXT,
    content BLOB
);

-- Queries
selectOpenCases:
SELECT * FROM cases WHERE closed = 0;

selectCasesByType:
SELECT * FROM cases WHERE case_type = ? AND closed = 0;

selectCaseById:
SELECT * FROM cases WHERE case_id = ?;

insertCase:
INSERT OR REPLACE INTO cases VALUES (?, ?, ?, ?, ?, ?, ?, ?);

closeCase:
UPDATE cases SET closed = 1 WHERE case_id = ?;

selectCaseProperties:
SELECT * FROM case_properties WHERE case_id = ?;

insertCaseProperty:
INSERT OR REPLACE INTO case_properties VALUES (?, ?, ?);

selectCaseIndices:
SELECT * FROM case_indices WHERE case_id = ?;

insertCaseIndex:
INSERT OR REPLACE INTO case_indices VALUES (?, ?, ?, ?, ?);

insertUser:
INSERT OR REPLACE INTO users VALUES (?, ?, ?, ?, ?);

selectUser:
SELECT * FROM users WHERE username = ? AND domain = ?;

updateSyncToken:
UPDATE users SET sync_token = ? WHERE user_id = ?;

insertFixture:
INSERT OR REPLACE INTO fixtures VALUES (?, ?, ?, ?);

selectFixturesByType:
SELECT * FROM fixtures WHERE fixture_type = ?;

insertFormQueue:
INSERT INTO form_queue VALUES (?, ?, ?, ?, ?, ?);

selectPendingForms:
SELECT * FROM form_queue WHERE status = 'pending';

updateFormStatus:
UPDATE form_queue SET status = ?, submitted_at = ? WHERE form_id = ?;

insertResource:
INSERT OR REPLACE INTO resources VALUES (?, ?, ?, ?, ?, ?);

selectAllResources:
SELECT * FROM resources;

selectResourceById:
SELECT * FROM resources WHERE resource_id = ?;
```

**Step 3: Create expect/actual DatabaseDriverFactory**

Expect declaration (`app/src/commonMain/kotlin/org/commcare/app/storage/DatabaseDriverFactory.kt`):

```kotlin
package org.commcare.app.storage

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
```

Android actual (`app/src/androidMain/kotlin/org/commcare/app/storage/DatabaseDriverFactory.kt`):

```kotlin
package org.commcare.app.storage

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(CommCareDatabase.Schema, context, "commcare.db")
    }
}
```

iOS actual (`app/src/iosMain/kotlin/org/commcare/app/storage/DatabaseDriverFactory.kt`):

```kotlin
package org.commcare.app.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(CommCareDatabase.Schema, "commcare.db")
    }
}
```

**Step 4: Verify schema generates**

```bash
cd app && ./gradlew generateCommonMainCommCareDatabaseInterface
```

Expected: Generates `CommCareDatabase` interface in build/generated/.

**Step 5: Verify both platforms compile with SQLDelight**

```bash
cd app && ./gradlew compileKotlinAndroid compileKotlinIosSimulatorArm64
```

**Step 6: Commit**

```bash
git add app/
git commit -m "feat: add SQLDelight storage infrastructure with schemas"
```

---

## Task 3: Implement SqlDelightUserSandbox

**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/storage/SqlDelightUserSandbox.kt`
- Create: `app/src/commonMain/kotlin/org/commcare/app/storage/SqlDelightCaseStorage.kt`
- Test: `app/src/commonTest/kotlin/org/commcare/app/storage/SqlDelightUserSandboxTest.kt`

**Context:** commcare-core's `UserSandbox` is an abstract class that provides case, user, fixture, and ledger storage. The engine requires a concrete implementation. Currently the iOS app uses `IosInMemoryStorage` — we replace it with SQLDelight-backed storage that works on both platforms.

**Key engine interface:** `commcare-core/src/commonMain/kotlin/org/commcare/core/sandbox/UserSandbox.kt`
**Storage interface:** `commcare-core/src/commonMain/kotlin/org/javarosa/core/services/storage/IStorageUtilityIndexed.kt`

**Step 1: Write failing test**

```kotlin
package org.commcare.app.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SqlDelightUserSandboxTest {

    // Use in-memory driver for tests
    private fun createTestDatabase(): CommCareDatabase {
        // Platform-specific driver creation handled by expect/actual
        // For tests, use in-memory variant
        val driver = createTestDriver()
        return CommCareDatabase(driver)
    }

    @Test
    fun testInsertAndRetrieveCase() {
        val db = createTestDatabase()
        val sandbox = SqlDelightUserSandbox(db)

        // Insert a case via the database
        db.commCareQueries.insertCase(
            case_id = "abc-123",
            case_type = "patient",
            case_name = "Test Patient",
            owner_id = "user-1",
            date_opened = "2026-03-12",
            closed = 0,
            user_id = "user-1",
            external_id = null
        )

        val storage = sandbox.getCaseStorage()
        assertNotNull(storage)
    }
}
```

**Step 2: Run test to verify it fails**

```bash
cd app && ./gradlew jvmTest
```

Expected: FAIL — `SqlDelightUserSandbox` not defined.

**Step 3: Implement SqlDelightUserSandbox**

This is the core integration between commcare-core's storage interfaces and SQLDelight. The engine uses `IStorageUtilityIndexed<Case>` extensively — we need an adapter that translates between SQLDelight queries and the engine's iterator-based storage API.

Create `SqlDelightUserSandbox.kt` implementing `UserSandbox` abstract class. Key methods to implement:
- `getCaseStorage()` → returns `IStorageUtilityIndexed<Case>` backed by SQLDelight
- `getLedgerStorage()` → returns ledger storage (initially empty impl)
- `getUserStorage()` → returns user storage
- `getAppFixtureStorage()` / `getUserFixtureStorage()` → fixture storage
- `getLoggedInUser()` / `setLoggedInUser()` → current user

Create `SqlDelightCaseStorage.kt` implementing `IStorageUtilityIndexed<Case>`. Key methods:
- `read(id: Int)` → query case by record ID
- `getRecordForValue(fieldName, value)` → query case by field
- `iterate()` → return iterator over all cases
- `write(p: Persistable)` → insert/update case
- `getNumRecords()` → count cases

**Step 4: Run tests to verify they pass**

```bash
cd app && ./gradlew jvmTest
```

**Step 5: Commit**

```bash
git add app/src/
git commit -m "feat: implement SqlDelightUserSandbox for cross-platform storage"
```

---

## Task 4: Wire Real Authentication

**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/LoginViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/state/AppState.kt`
- Test: `app/src/commonTest/kotlin/org/commcare/app/viewmodel/LoginViewModelTest.kt`

**Context:** Current LoginViewModel makes an HTTP GET to the restore endpoint and checks status codes. For Tier 1, we need to:
1. Parse the restore response XML to extract user data, cases, and fixtures
2. Store the sync token for incremental sync
3. Persist user credentials for offline login
4. Transition to app installation after successful restore

**Key engine APIs:**
- `commcare-core/src/commonMain/kotlin/org/commcare/core/parse/CommCareTransactionParserFactory.kt` — creates parsers for case/user/fixture transactions in restore XML
- `commcare-core/src/commonMain/kotlin/org/javarosa/xml/TreeElementParser.kt` — XML parsing

**Step 1: Write failing test for restore parsing**

Test that LoginViewModel transitions to Installing state and populates sandbox after successful auth.

**Step 2: Implement restore response parsing**

After successful HTTP 200 from restore endpoint:
1. Parse response XML using `PlatformXmlParser`
2. Create `CommCareTransactionParserFactory` with sandbox references
3. Process case, user, and fixture transactions
4. Store sync token from `<restore_id>` element
5. Transition AppState to Installing

**Step 3: Update AppState sealed class**

Add states needed for the full flow:
```kotlin
sealed class AppState {
    data object LoggedOut : AppState()
    data class LoggingIn(val message: String = "Authenticating...") : AppState()
    data class Installing(val progress: Float = 0f, val message: String = "") : AppState()
    data class Ready(val platform: CommCarePlatform, val sandbox: SqlDelightUserSandbox) : AppState()
    data class Error(val message: String) : AppState()
}
```

**Step 4: Run tests, verify pass**

**Step 5: Commit**

```bash
git commit -m "feat: wire real auth with restore parsing and sandbox population"
```

---

## Task 5: Implement App Installation (ResourceManager Integration)

**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/engine/AppInstaller.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/LoginViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/InstallScreen.kt`
- Test: `app/src/commonTest/kotlin/org/commcare/app/engine/AppInstallerTest.kt`

**Context:** After auth succeeds, the app must install the CommCare application (profile, suites, forms, fixtures, media). This uses `ResourceManager.installAppResources()`.

**Key engine APIs:**
- `ResourceManager.installAppResources(platform, profileReference, global, forceInstall, authorityForProfile, resourceInstallContext)` — `commcare-core/src/commonMain/kotlin/org/commcare/resources/ResourceManager.kt`
- `CommCarePlatform(majorVersion, minorVersion, minimalVersion)` — `commcare-core/src/commonMain/kotlin/org/commcare/util/CommCarePlatform.kt`
- `ResourceTable` — `commcare-core/src/commonMain/kotlin/org/commcare/resources/model/ResourceTable.kt`

**Step 1: Write failing test**

Test that AppInstaller downloads a profile reference, installs resources, and initializes CommCarePlatform.

**Step 2: Implement AppInstaller**

```kotlin
class AppInstaller(
    private val httpClient: PlatformHttpClient,
    private val sandbox: SqlDelightUserSandbox
) {
    fun install(
        profileUrl: String,
        onProgress: (Float, String) -> Unit
    ): CommCarePlatform {
        val platform = CommCarePlatform(2, 0, 0)
        val resourceTable = ResourceTable()

        // Install app resources from profile URL
        ResourceManager.installAppResources(
            platform,
            profileUrl,
            resourceTable,
            true, // forceInstall
            Resource.RESOURCE_AUTHORITY_REMOTE,
            ResourceInstallContext(/* ... */)
        )

        // Initialize platform with installed resources
        platform.initialize(resourceTable, false)
        return platform
    }
}
```

**Step 3: Wire InstallScreen to real progress**

Update InstallScreen to display progress from ResourceTable status callbacks.

**Step 4: Update LoginViewModel to call AppInstaller after auth**

After restore parsing completes, extract profile URL from restore response and call `AppInstaller.install()`.

**Step 5: Run tests, verify pass**

**Step 6: Commit**

```bash
git commit -m "feat: implement app installation with ResourceManager"
```

---

## Task 6: Wire Real Menu Navigation with Session State Machine

**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/MenuViewModel.kt`
- Create: `app/src/commonMain/kotlin/org/commcare/app/engine/SessionNavigatorImpl.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/MenuScreen.kt`
- Test: `app/src/commonTest/kotlin/org/commcare/app/viewmodel/MenuViewModelTest.kt`

**Context:** Current MenuViewModel manually iterates suites. For Tier 1, we wire it to the full `CommCareSession.getNeededData()` state machine, which handles menu selection, case datum requirements, computed datums, and form readiness.

**Key engine APIs:**
- `CommCareSession.getNeededData(evalContext)` → returns `STATE_COMMAND_ID`, `STATE_DATUM_VAL`, `STATE_DATUM_COMPUTED`, `STATE_SYNC_REQUEST`, or `null` (ready for form)
- `SessionWrapper(platform, sandbox)` — wraps session with platform context
- `CommCareSession.setCommand(commandId)` — select menu/form
- `CommCareSession.setEntityDatum(datum, value)` — select case
- `CommCareSession.getNeededDatum()` — get the next SessionDatum definition

**Step 1: Implement SessionNavigatorImpl**

Implement the `getNeededData()` dispatch loop:

```kotlin
class SessionNavigatorImpl(
    private val platform: CommCarePlatform,
    private val sandbox: SqlDelightUserSandbox
) {
    val session = CommCareSession(platform)

    fun getNextStep(): NavigationStep {
        val evalContext = session.getEvaluationContext(
            CommCareInstanceInitializer(session, sandbox, platform)
        )
        return when (session.getNeededData(evalContext)) {
            SessionFrame.STATE_COMMAND_ID -> NavigationStep.ShowMenu
            SessionFrame.STATE_DATUM_VAL -> NavigationStep.ShowCaseList(session.getNeededDatum())
            SessionFrame.STATE_DATUM_COMPUTED -> {
                session.setComputedDatum(evalContext)
                getNextStep() // recurse
            }
            SessionFrame.STATE_SYNC_REQUEST -> NavigationStep.SyncRequired
            null -> NavigationStep.StartForm(session.getForm())
            else -> NavigationStep.Error("Unknown state")
        }
    }

    fun selectCommand(commandId: String) { session.setCommand(commandId) }
    fun selectCase(caseId: String) { session.setEntityDatum(session.getNeededDatum(), caseId) }
    fun stepBack() { /* session.stepBack(evalContext) */ }
}

sealed class NavigationStep {
    data object ShowMenu : NavigationStep()
    data class ShowCaseList(val datum: SessionDatum) : NavigationStep()
    data class StartForm(val xmlns: String?) : NavigationStep()
    data object SyncRequired : NavigationStep()
    data class Error(val message: String) : NavigationStep()
}
```

**Step 2: Refactor MenuViewModel to use SessionNavigatorImpl**

Replace manual suite iteration with `navigator.getNextStep()` dispatch.

**Step 3: Run tests, verify pass**

**Step 4: Commit**

```bash
git commit -m "feat: wire menu navigation to CommCareSession state machine"
```

---

## Task 7: Wire Case Selection from SQLDelight Storage

**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/CaseListViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/CaseListScreen.kt`
- Test: `app/src/commonTest/kotlin/org/commcare/app/viewmodel/CaseListViewModelTest.kt`

**Context:** Current CaseListViewModel accepts an `IStorageUtilityIndexed<Case>` and iterates it. For Tier 1, we connect it to the `SqlDelightCaseStorage` from Task 3 and add datum-based filtering (only show cases matching the session's required case type).

**Step 1: Write failing test**

Test that CaseListViewModel loads cases of the correct type from SQLDelight storage and that selecting a case calls `navigator.selectCase()`.

**Step 2: Refactor CaseListViewModel**

```kotlin
class CaseListViewModel(
    private val navigator: SessionNavigatorImpl,
    private val sandbox: SqlDelightUserSandbox
) {
    fun loadCases() {
        val datum = navigator.session.getNeededDatum()
        val caseType = datum?.getDataId() // e.g., "case_type"
        val storage = sandbox.getCaseStorage()

        // Load cases matching the datum's nodeset filter
        cases = storage.iterate().asSequence()
            .filter { !it.isClosed }
            .filter { caseType == null || it.getTypeId() == caseType }
            .map { case -> CaseItem(case) }
            .toList()
    }

    fun selectCase(caseId: String) {
        navigator.selectCase(caseId)
    }
}
```

**Step 3: Run tests, verify pass**

**Step 4: Commit**

```bash
git commit -m "feat: wire case selection to SQLDelight storage"
```

---

## Task 8: Implement Real Form Entry with FormEntryController

**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/FormEntryViewModel.kt`
- Create: `app/src/commonMain/kotlin/org/commcare/app/engine/FormEntrySession.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/FormEntryScreen.kt`
- Test: `app/src/commonTest/kotlin/org/commcare/app/viewmodel/FormEntryViewModelTest.kt`

**Context:** This is the most complex task. Current FormEntryViewModel is a stub. We need to:
1. Load a FormDef from installed resources
2. Create FormEntryModel + FormEntryController
3. Navigate questions with stepToNextEvent/stepToPreviousEvent
4. Render question prompts (text, select options, constraints)
5. Answer questions with answerQuestion()
6. Support basic types: text, integer, decimal, select-one, select-multi, date, time

**Key engine APIs:**
- `FormEntryController` — `commcare-core/src/commonMain/kotlin/org/javarosa/form/api/FormEntryController.kt`
  - `stepToNextEvent(): Int` — returns EVENT_QUESTION, EVENT_GROUP, EVENT_END_OF_FORM, etc.
  - `answerQuestion(data: IAnswerData?): Int` — returns ANSWER_OK, ANSWER_CONSTRAINT_VIOLATED, etc.
  - `getQuestionPrompts(): Array<FormEntryPrompt>` — current questions to display
- `FormEntryModel(form: FormDef)` — `commcare-core/src/commonMain/kotlin/org/javarosa/form/api/FormEntryModel.kt`
- `FormEntryPrompt` — `commcare-core/src/commonMain/kotlin/org/javarosa/form/api/FormEntryPrompt.kt`
  - `getQuestionText()` — display text
  - `getControlType()` — INPUT, SELECT, SELECT1, TRIGGER, etc.
  - `getSelectChoices()` — list of SelectChoice for selects
  - `getConstraintText()` — validation error message
  - `getAnswerValue()` — current answer
- Answer types in `commcare-core/src/commonMain/kotlin/org/javarosa/core/model/data/`:
  - `StringData`, `IntegerData`, `DecimalData`, `DateData`, `TimeData`, `SelectOneData`, `SelectMultiData`

**Step 1: Create FormEntrySession**

Wraps engine APIs into a simpler interface for the ViewModel:

```kotlin
class FormEntrySession(
    private val formDef: FormDef,
    private val sandbox: SqlDelightUserSandbox,
    private val platform: CommCarePlatform
) {
    private val model = FormEntryModel(formDef, FormEntryModel.REPEAT_STRUCTURE_LINEAR)
    val controller = FormEntryController(model)

    fun initialize() {
        // Set up instance initializer for case/fixture data access during form eval
        val initializer = CommCareInstanceInitializer(/* session, sandbox, platform */)
        formDef.initialize(true, initializer)
    }

    fun currentEvent(): Int = model.getEvent()
    fun stepNext(): Int = controller.stepToNextEvent()
    fun stepPrev(): Int = controller.stepToPreviousEvent()
    fun getPrompts(): Array<FormEntryPrompt> = controller.getQuestionPrompts()
    fun answer(data: IAnswerData?): Int = controller.answerQuestion(data)
    fun isAtEnd(): Boolean = model.getEvent() == FormEntryController.EVENT_END_OF_FORM
    fun getSubmissionXml(): ByteArray = /* serialize form instance to XML */
}
```

**Step 2: Refactor FormEntryViewModel**

```kotlin
class FormEntryViewModel(
    private val formSession: FormEntrySession
) {
    var questions by mutableStateOf<List<QuestionState>>(emptyList())
    var isComplete by mutableStateOf(false)
    var validationError by mutableStateOf<String?>(null)

    fun loadForm() {
        formSession.initialize()
        formSession.stepNext() // Move past BEGINNING_OF_FORM
        updateQuestions()
    }

    fun answerQuestion(index: Int, answer: IAnswerData?) {
        val result = formSession.answer(answer)
        when (result) {
            FormEntryController.ANSWER_OK -> {
                validationError = null
            }
            FormEntryController.ANSWER_CONSTRAINT_VIOLATED -> {
                val prompts = formSession.getPrompts()
                validationError = prompts[index].getConstraintText()
            }
            FormEntryController.ANSWER_REQUIRED_BUT_EMPTY -> {
                validationError = "This field is required"
            }
        }
    }

    fun nextQuestion() {
        val event = formSession.stepNext()
        if (event == FormEntryController.EVENT_END_OF_FORM) {
            isComplete = true
        } else {
            updateQuestions()
        }
    }

    fun previousQuestion() {
        formSession.stepPrev()
        updateQuestions()
    }

    private fun updateQuestions() {
        questions = formSession.getPrompts().map { prompt ->
            QuestionState(
                text = prompt.getQuestionText() ?: "",
                controlType = prompt.getControlType(),
                choices = prompt.getSelectChoices()?.map { it.getLabelInnerText() } ?: emptyList(),
                currentAnswer = prompt.getAnswerValue()?.displayText,
                required = prompt.isRequired()
            )
        }
    }
}

data class QuestionState(
    val text: String,
    val controlType: Int,
    val choices: List<String>,
    val currentAnswer: String?,
    val required: Boolean
)
```

**Step 3: Update FormEntryScreen**

Render questions based on `controlType`:
- `Constants.CONTROL_INPUT` → text field (check data type for integer/decimal/date/time)
- `Constants.CONTROL_SELECT_ONE` → radio buttons
- `Constants.CONTROL_SELECT_MULTI` → checkboxes
- Navigation: Back/Next buttons calling `previousQuestion()`/`nextQuestion()`

**Step 4: Write test that loads a form and navigates questions**

**Step 5: Run tests, verify pass**

**Step 6: Commit**

```bash
git commit -m "feat: implement real form entry with FormEntryController"
```

---

## Task 9: Implement Form Submission

**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/FormEntryViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/FormQueueViewModel.kt`
- Test: `app/src/commonTest/kotlin/org/commcare/app/viewmodel/FormSubmissionTest.kt`

**Context:** After completing a form, the app must:
1. Serialize the FormDef instance to submission XML
2. Queue the XML in SQLDelight (form_queue table)
3. Submit to HQ's receiver endpoint: `POST /a/{domain}/receiver/`
4. Handle success (mark submitted) and failure (keep in queue for retry)

**Key engine APIs:**
- `XFormSerializingVisitor` — serializes FormDef instance to XML bytes
- `FormRecord` — wraps form metadata for submission

**Step 1: Write failing test**

Test that submitting a completed form writes to form_queue and HTTP POST succeeds.

**Step 2: Implement form serialization and queue**

After `FormEntryController` reaches END_OF_FORM:
1. Call `XFormSerializingVisitor.serializeInstance(formDef.getInstance())` to get XML
2. Insert into `form_queue` table via SQLDelight
3. Trigger submission via `FormQueueViewModel`

**Step 3: Implement HTTP submission**

POST XML to `{serverUrl}/a/{domain}/receiver/` with auth header. Handle 201 (success), 401 (re-auth needed), 500 (retry).

**Step 4: Run tests, verify pass**

**Step 5: Commit**

```bash
git commit -m "feat: implement form submission with queue and retry"
```

---

## Task 10: Implement Sync / Restore Parsing

**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/engine/SyncEngine.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/SyncViewModel.kt`
- Test: `app/src/commonTest/kotlin/org/commcare/app/engine/SyncEngineTest.kt`

**Context:** Current SyncViewModel makes HTTP calls but doesn't parse the response. For Tier 1, we need to:
1. GET `/a/{domain}/phone/restore/?since={syncToken}` for incremental sync
2. Parse the restore XML with `CommCareTransactionParserFactory`
3. Apply case transactions (create/update/close) to SQLDelight
4. Store new sync token
5. Submit any queued forms

**Key engine APIs:**
- `CommCareTransactionParserFactory(sandbox)` — creates transaction parsers
- `DataModelPullParser(xmlStream, transactionParserFactory)` — drives XML parsing

**Step 1: Write failing test**

Test that SyncEngine parses a sample restore XML and updates case storage.

**Step 2: Implement SyncEngine**

```kotlin
class SyncEngine(
    private val sandbox: SqlDelightUserSandbox,
    private val httpClient: PlatformHttpClient
) {
    fun sync(serverUrl: String, domain: String, authHeader: String): SyncResult {
        val syncToken = sandbox.getCurrentSyncToken()

        // 1. Download restore
        val restoreUrl = "$serverUrl/a/$domain/phone/restore/" +
            if (syncToken != null) "?since=$syncToken" else ""
        val response = httpClient.execute(
            PlatformHttpRequest(restoreUrl, "GET", mapOf("Authorization" to authHeader))
        )

        when (response.statusCode) {
            412 -> return SyncResult.NoNewData
            in 200..299 -> { /* parse below */ }
            401 -> return SyncResult.AuthExpired
            else -> return SyncResult.Error("HTTP ${response.statusCode}")
        }

        // 2. Parse restore response
        val factory = CommCareTransactionParserFactory(sandbox)
        val parser = DataModelPullParser(response.body, factory)
        parser.parse()

        // 3. Extract and store new sync token
        val newToken = extractSyncToken(response.body)
        sandbox.setSyncToken(newToken)

        return SyncResult.Success(newToken)
    }
}

sealed class SyncResult {
    data class Success(val syncToken: String) : SyncResult()
    data object NoNewData : SyncResult()
    data object AuthExpired : SyncResult()
    data class Error(val message: String) : SyncResult()
}
```

**Step 3: Wire SyncViewModel to SyncEngine**

Replace stub HTTP calls with `SyncEngine.sync()`.

**Step 4: Run tests, verify pass**

**Step 5: Commit**

```bash
git commit -m "feat: implement sync with restore parsing and incremental tokens"
```

---

## Task 11: Implement Encrypted Storage

**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/storage/DatabaseDriverFactory.kt` (expect)
- Modify: `app/src/androidMain/kotlin/org/commcare/app/storage/DatabaseDriverFactory.kt` (actual)
- Modify: `app/src/iosMain/kotlin/org/commcare/app/storage/DatabaseDriverFactory.kt` (actual)
- Test: `app/src/commonTest/kotlin/org/commcare/app/storage/EncryptedStorageTest.kt`

**Context:** CommCare encrypts all data at rest with AES-256 using password-derived keys. For Tier 1:
- Android: Use `SQLCipher` (Android SQLCipher driver for SQLDelight)
- iOS: Use iOS Data Protection (file-level encryption via NSFileProtectionComplete) + SQLCipher via native driver

**Step 1: Add SQLCipher dependencies**

Android: `app.cash.sqldelight:android-driver` already supports encrypted databases via `SupportSQLiteOpenHelper` with SQLCipher.

iOS: Use `app.cash.sqldelight:native-driver` with encrypted SQLite via `co.touchlab:sqliter-driver` encryption support.

**Step 2: Update DatabaseDriverFactory to accept encryption key**

```kotlin
// expect
expect class DatabaseDriverFactory {
    fun createDriver(encryptionKey: ByteArray? = null): SqlDriver
}
```

**Step 3: Derive encryption key from user password**

Use `PlatformCrypto` from commcare-core to derive AES key from password using PBKDF2.

**Step 4: Run tests, verify pass**

**Step 5: Commit**

```bash
git commit -m "feat: add encrypted storage with password-derived keys"
```

---

## Task 12: Build Oracle Test Harness

**Files:**
- Create: `app/oracle/FormPlayerOracle.kt`
- Create: `app/oracle/OracleTestRunner.kt`
- Create: `app/oracle/fixtures/` (golden test fixtures)
- Create: `app/src/commonTest/kotlin/org/commcare/app/oracle/OracleComparisonTest.kt`

**Context:** The oracle test harness validates that the KMP engine produces the same output as FormPlayer. For Tier 1, we build the basic framework:
1. A script that runs a test form through FormPlayer's REST API and records the submitted XML
2. A test that runs the same form through our FormEntryController and compares output
3. Start with 5 simple test forms from commcare-core's test fixtures

**Key engine test fixtures:** `commcare-core/src/test/resources/` — contains 162+ XForm fixtures

**Step 1: Create oracle test framework**

```kotlin
class OracleTestRunner {
    /**
     * Load a form from test fixtures, fill it with predetermined answers,
     * serialize the output, and compare against a golden fixture.
     */
    fun compareFormOutput(
        formPath: String,
        answers: Map<String, IAnswerData>,
        expectedXmlPath: String
    ): OracleResult {
        // 1. Load FormDef from XForm XML
        val formDef = XFormParser.parse(formPath)

        // 2. Create controller and fill form
        val model = FormEntryModel(formDef)
        val controller = FormEntryController(model)
        controller.stepToNextEvent()

        // 3. Walk through form, applying answers
        while (model.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
            val index = model.getFormIndex()
            if (model.getEvent() == FormEntryController.EVENT_QUESTION) {
                val key = index.toString()
                answers[key]?.let { controller.answerQuestion(it) }
            }
            controller.stepToNextEvent()
        }

        // 4. Serialize output
        val visitor = XFormSerializingVisitor()
        val actualXml = visitor.serializeInstance(formDef.getInstance())

        // 5. Compare against golden fixture
        val expectedXml = readFixture(expectedXmlPath)
        return if (normalizeXml(actualXml) == normalizeXml(expectedXml)) {
            OracleResult.Pass
        } else {
            OracleResult.Fail(
                expected = expectedXml,
                actual = actualXml,
                diff = computeDiff(expectedXml, actualXml)
            )
        }
    }
}
```

**Step 2: Create 5 golden test fixtures**

Select 5 forms from `commcare-core/src/test/resources/` that cover: text input, integer, select-one, select-multi, and date. Run them through FormPlayer to capture expected output.

**Step 3: Write oracle comparison tests**

```kotlin
class OracleComparisonTest {
    @Test
    fun testBasicTextForm() {
        val result = OracleTestRunner().compareFormOutput(
            formPath = "test_fixtures/basic_text_form.xml",
            answers = mapOf("q1" to StringData("hello")),
            expectedXmlPath = "test_fixtures/basic_text_form_expected.xml"
        )
        assertEquals(OracleResult.Pass, result)
    }
}
```

**Step 4: Run oracle tests**

```bash
cd app && ./gradlew jvmTest --tests "*.OracleComparisonTest"
```

**Step 5: Commit**

```bash
git commit -m "feat: build oracle test harness with 5 golden fixtures"
```

---

## Task 13: End-to-End Validation

**Files:**
- Create: `app/src/commonTest/kotlin/org/commcare/app/e2e/FullJourneyTest.kt`

**Context:** Verify the complete user journey works: login → install → menu → case → form → submit → sync. This is the Tier 1 exit gate.

**Step 1: Write end-to-end test**

```kotlin
class FullJourneyTest {
    @Test
    fun testCompleteUserJourney() {
        // 1. Create test database
        val db = createTestDatabase()
        val sandbox = SqlDelightUserSandbox(db)

        // 2. Simulate login (use cached test restore data)
        val restoreXml = readFixture("test_restore.xml")
        parseRestore(restoreXml, sandbox)

        // 3. Install test app
        val platform = CommCarePlatform(2, 0, 0)
        // ... install from test profile

        // 4. Navigate menu
        val navigator = SessionNavigatorImpl(platform, sandbox)
        assertEquals(NavigationStep.ShowMenu, navigator.getNextStep())

        // 5. Select command (first menu item)
        val menus = platform.getInstalledSuites()
        assertNotNull(menus)

        // 6. Select case
        // ... navigate to case list, select case

        // 7. Fill form
        // ... create FormEntrySession, answer questions

        // 8. Submit form
        // ... serialize and queue

        // 9. Sync
        // ... verify case updates applied
    }
}
```

**Step 2: Run on both platforms**

```bash
cd app && ./gradlew jvmTest --tests "*.FullJourneyTest"
# iOS test requires macOS:
cd app && ./gradlew iosSimulatorArm64Test --tests "*.FullJourneyTest"
```

**Step 3: Verify Android app launches**

```bash
cd app && ./gradlew installDebug
# Launch on emulator, verify login screen appears
```

**Step 4: Commit**

```bash
git commit -m "test: add end-to-end full journey validation"
```

---

## Task 14: Update CLAUDE.md and Create Tier 1 Completion Report

**Files:**
- Modify: `CLAUDE.md`
- Create: `docs/plans/YYYY-MM-DD-phase3-tier1-completion-report.md`

**Step 1: Write completion report**

Document: what was built, test results, oracle test pass rate, both platform status, known issues, Tier 2 readiness.

**Step 2: Update CLAUDE.md**

- Update Current Status section with Phase 3 Tier 1 status
- Add Phase 3 plan to Key Docs
- Update Build Commands if changed
- Update Project Structure if new directories added

**Step 3: Commit and push**

```bash
git add CLAUDE.md docs/plans/
git commit -m "docs: Phase 3 Tier 1 completion report"
git push origin main
```

---

## Execution Notes

**Dependencies between tasks:**
- Tasks 1-2 are independent infrastructure (can run in parallel)
- Task 3 depends on Task 2 (needs SQLDelight schemas)
- Tasks 4-5 can run in parallel (auth + install are independent)
- Task 6 depends on Task 5 (needs installed CommCarePlatform)
- Task 7 depends on Tasks 3 + 6 (needs storage + navigation)
- Task 8 depends on Task 6 (needs session navigator to know which form to load)
- Task 9 depends on Task 8 (needs form completion to submit)
- Task 10 depends on Tasks 3 + 4 (needs storage + auth for sync)
- Task 11 depends on Task 2 (modifies storage layer)
- Task 12 depends on Task 8 (needs FormEntryController working)
- Task 13 depends on all previous tasks
- Task 14 depends on Task 13

**Parallelization opportunities:**
- Tasks 1 + 2 (Android target + SQLDelight) — independent
- Tasks 4 + 5 (Auth + Install) — after Task 3
- Tasks 11 + 12 (Encrypted storage + Oracle) — after core features work

**Estimated total:** 14 tasks, ~50-70 steps, targeting Tier 1 completion before moving to Tier 2.
