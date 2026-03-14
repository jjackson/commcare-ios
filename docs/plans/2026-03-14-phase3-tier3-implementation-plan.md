# Phase 3 Tier 3: Advanced Features — Implementation Plan

**Date:** 2026-03-14
**Prerequisite:** Phase 3 Tier 2 complete (30/30 tasks), cross-platform validation (102+ tests on JVM & iOS)
**Goal:** Feature-complete CommCare iOS that matches commcare-android for advanced workflows.
**Estimated tasks:** 32, organized into 8 waves

## Scope Prioritization

Tier 3 features are ordered by workflow impact. An advanced deployment needs:
1. Complex case workflows (tiered selection, search/claim, case tiles)
2. Advanced navigation (grid menus, display conditions, session stack)
3. Form linking and chained workflows
4. App lifecycle management (update, offline install, demo mode)
5. Platform integration (biometric auth, background sync)
6. Reporting and visualization (graphing, UCR reports)
7. Localization edge cases (alternative calendars)
8. Support tooling (recovery mode, diagnostics)

Features requiring external service integration (push notifications, third-party biometrics like Simprints) are deferred to Tier 4.

## Wave 1: Case Management — Advanced (4 tasks)

*Extend case workflows with tiles, tiered selection, and configurable actions.*

### Task 1: Case tiles (card-style case list)
**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/ui/CaseTileRow.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/CaseListScreen.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/CaseListViewModel.kt`

**What to do:**
- Parse `<detail>` tile configuration from suite.xml (grid layout, image fields, style attributes)
- Create `CaseTileRow` composable with configurable columns (text, image, date formatting)
- Support `style="address"` (multiline), `style="enum"` (color-coded), image tiles
- Add tile vs. list toggle in CaseListViewModel based on suite detail configuration
- Wire tile field expressions to case properties via XPath evaluation

### Task 2: Persistent case tile (info bar during form entry)
**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/ui/PersistentTileBar.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/FormEntryScreen.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/FormEntryViewModel.kt`

**What to do:**
- Parse `<detail id="m0_case_short">` persistent detail configuration
- Create `PersistentTileBar` composable showing selected case info at top of form
- Pass selected case datum through session to FormEntryViewModel
- Display case name, type, and configured tile fields during form entry
- Support hide/show toggle

### Task 3: Tiered case selection (parent/child flow)
**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/MenuViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/engine/SessionNavigatorImpl.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/CaseListViewModel.kt`

**What to do:**
- Support multi-step case selection: select parent case, then filter child cases by parent_id
- Parse `<session>` datum elements for multi-datum commands (parent + child)
- Filter case list by parent index in CaseListViewModel
- Track parent datum in session state for child case filtering
- Support "Select Parent First" workflow in SessionNavigatorImpl

### Task 4: Case list action buttons and auto-select
**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/CaseListScreen.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/CaseListViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/MenuViewModel.kt`

**What to do:**
- Parse `<action>` elements from suite detail configuration
- Render action buttons (e.g., "Register New Case") above/below case list
- Handle action routing through SessionNavigator (action launches a form without case selection)
- Implement auto-select: when case list has exactly one result, skip list and select automatically
- Add `auto_select="true"` detail attribute parsing

## Wave 2: Menu & Navigation — Advanced (4 tasks)

*Grid menus, display conditions, and shadow modules.*

### Task 5: Grid menus
**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/ui/GridMenuScreen.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/MenuScreen.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/MenuViewModel.kt`

**What to do:**
- Parse `style="grid"` attribute on `<menu>` in suite.xml
- Create `GridMenuScreen` composable with configurable column count (default 3)
- Render menu items as icon+label cards in a grid layout
- Support `<text><xpath>` for dynamic menu text
- Fall back to list menu when `style` is absent or `"list"`

### Task 6: Display conditions (conditional menu/form visibility)
**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/MenuViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/engine/SessionNavigatorImpl.kt`

**What to do:**
- Parse `relevant` attribute on `<menu>` and `<entry>` elements in suite.xml
- Evaluate XPath relevancy expressions against current session context
- Filter menu items in MenuViewModel based on evaluated conditions
- Re-evaluate when session datum changes (e.g., after case selection)
- Support `<display><text><xpath>` dynamic display text

### Task 7: Shadow modules (shared forms)
**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/engine/SessionNavigatorImpl.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/MenuViewModel.kt`

**What to do:**
- Parse shadow module configuration from suite.xml (`<entry>` referencing another module's form)
- Support shared form definitions across multiple menu entries
- Route shadow module entries through same form with different session datums
- Handle independent case lists per shadow module instance

### Task 8: Oracle tests for advanced navigation
**Files:**
- Create: `app/src/jvmTest/kotlin/org/commcare/app/oracle/AdvancedNavigationOracleTest.kt`

**What to do:**
- Test grid menu rendering decisions
- Test display condition evaluation (visible/hidden items)
- Test tiered case selection flow (parent → child)
- Test case list action routing
- Generate golden files for any navigation output that should be cross-platform

## Wave 3: Session Stack & Form Linking (4 tasks)

*Enable chained forms, session push/pop, and dynamic form routing.*

### Task 9: Session stack operations (push/pop)
**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/engine/SessionNavigatorImpl.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/MenuViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/AppState.kt`

**What to do:**
- Implement session frame stack in SessionNavigatorImpl (push current frame, start new)
- Support `<stack>` operations in suite.xml `<entry>` post-form-completion
- Pop session frame after chained form completes (return to previous workflow)
- Track stack depth in AppState for breadcrumb rendering
- Handle stack clear on explicit "home" navigation

### Task 10: Form linking (dynamic routing)
**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/engine/SessionNavigatorImpl.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/FormEntryViewModel.kt`

**What to do:**
- Parse `<post>` element in suite entry for form linking configuration
- After form submission, evaluate `<command>` XPath to determine next form
- Push new session frame with computed datums
- Support `<datum function="uuid()">` for auto-generating case IDs
- Handle circular link detection (max depth)

### Task 11: Chained form end navigation
**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/FormEntryViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/MenuViewModel.kt`

**What to do:**
- After form completion, check for session stack items before returning to menu
- If stack has pending items, navigate to next form automatically
- Show "Completing workflow..." transition between chained forms
- Support "return to same form" (repeat same form for batch data entry)
- Handle form submission failure in chain (retry vs. break chain)

### Task 12: Oracle tests for session stack
**Files:**
- Create: `app/src/jvmTest/kotlin/org/commcare/app/oracle/SessionStackOracleTest.kt`

**What to do:**
- Test session push/pop lifecycle
- Test form linking with datum computation
- Test chained form navigation sequence
- Test stack clear behavior

## Wave 4: Case Search & Claim (4 tasks)

*Remote case search via server queries and extension case creation.*

### Task 13: Case search UI
**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/ui/CaseSearchScreen.kt`
- Create: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/CaseSearchViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/MenuViewModel.kt`

**What to do:**
- Create `CaseSearchScreen` with search fields defined by `<remote-request>` in suite.xml
- Parse `<query>` elements to build search field UI (text inputs, dropdowns)
- Execute HTTP search request to CommCare HQ case search endpoint
- Display search results in case list format (reuse CaseTileRow)
- Support pagination of remote results
- Handle offline fallback (search local cases when server unreachable)

### Task 14: Case claim and extension cases
**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/CaseSearchViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/storage/SqlDelightUserSandbox.kt`

**What to do:**
- Implement case claim: selecting a remote case creates a local copy (extension case)
- Parse claim response and create local case with `owner_id` set to current user
- Support extension case indices (link claimed case to parent)
- Handle claim conflicts (case already claimed by another user)
- Persist claimed cases in SQLDelight storage

### Task 15: Case sharing and ownership
**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/CaseListViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/storage/SqlDelightUserSandbox.kt`

**What to do:**
- Filter case list by owner (current user, sharing group, all)
- Parse sharing group configuration from user restore
- Support `owner_id` filtering in case queries
- Handle case reassignment via form submission (change owner_id)
- Update sync to handle shared case data

### Task 16: Oracle tests for case search
**Files:**
- Create: `app/src/jvmTest/kotlin/org/commcare/app/oracle/CaseSearchOracleTest.kt`

**What to do:**
- Test remote search request construction
- Test claim response parsing
- Test extension case creation
- Test ownership filtering

## Wave 5: App Lifecycle Management (4 tasks)

*App updates, offline install, demo mode.*

### Task 17: App update flow
**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/UpdateViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/SettingsScreen.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/engine/AppInstaller.kt`

**What to do:**
- Check for app updates via profile.ccpr version comparison
- Download and stage updated resources via ResourceManager
- Support staged upgrade: install to staging table, swap on success
- Implement rollback on failed update (restore previous resource table)
- Add "Check for Updates" button in settings
- Show update progress with resource count

### Task 18: Offline .ccz install
**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/InstallScreen.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/engine/AppInstaller.kt`

**What to do:**
- Add file picker for .ccz files (platform expect/actual)
- Parse .ccz archive (ZIP) to extract profile.ccpr and resources
- Install from local file system instead of HTTP download
- Validate .ccz contents before installation
- Support both URL install and file install from InstallScreen

### Task 19: Demo/practice mode
**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/DemoModeManager.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/LoginViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/LoginScreen.kt`

**What to do:**
- Add "Demo Mode" / "Practice Mode" button on login screen
- Create demo_user with isolated data sandbox (separate SQLDelight database or table prefix)
- Populate demo data from practice_user_restore fixture
- Support "Reset Practice Data" to clear and re-populate
- Prevent demo form submissions from reaching server
- Show visual indicator when in demo mode

### Task 20: Full settings parity
**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/SettingsScreen.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/SettingsViewModel.kt`

**What to do:**
- Add app settings: fuzzy search threshold, auto-update frequency, locale override
- Add device settings: storage location, log level, auto-sync interval
- Add developer settings: developer mode toggle, show form hierarchy, XPath tester
- Persist all settings in SQLDelight or platform preferences
- Support settings restore from server profile
- Group settings by category with expandable sections

## Wave 6: Authentication & Platform (4 tasks)

*Biometric login, connection diagnostics, heartbeat.*

### Task 21: Biometric login (Face ID / Touch ID)
**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/platform/PlatformBiometricAuth.kt` (expect)
- Create: `app/src/jvmMain/kotlin/org/commcare/app/platform/PlatformBiometricAuth.kt` (actual stub)
- Create: `app/src/iosMain/kotlin/org/commcare/app/platform/PlatformBiometricAuth.kt` (actual - LocalAuthentication)
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/LoginViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/LoginScreen.kt`

**What to do:**
- Create `PlatformBiometricAuth` expect/actual (canAuthenticate, authenticate)
- iOS actual: use `LocalAuthentication.LAContext` via cinterop
- JVM actual: stub that always returns false for canAuthenticate
- Store encrypted credentials after first successful password login
- Show biometric button on LoginScreen when available
- Fall back to password on biometric failure

### Task 22: Heartbeat (server check-in)
**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/SyncViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/engine/AppInstaller.kt`

**What to do:**
- Periodic check-in to CommCare HQ heartbeat endpoint
- Parse response for: app version status (up-to-date, update available, force update)
- Show update prompt when server indicates newer version
- Block app usage on force update
- Include device info in heartbeat (app version, last sync time, form queue size)

### Task 23: Connection diagnostics
**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/ui/DiagnosticsScreen.kt`
- Create: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/DiagnosticsViewModel.kt`

**What to do:**
- Ping CommCare HQ and report latency
- Check SSL certificate validity
- Test authentication credentials (username/password still valid)
- Report last successful sync time
- Show network type and signal strength (platform API)
- Add "Run Diagnostics" button in settings

### Task 24: Oracle tests for auth and platform
**Files:**
- Create: `app/src/jvmTest/kotlin/org/commcare/app/oracle/PlatformFeatureOracleTest.kt`

**What to do:**
- Test heartbeat response parsing
- Test update version comparison logic
- Test biometric auth flow (mock)
- Test diagnostics data collection

## Wave 7: Background & Reporting (4 tasks)

*Background sync, graphing, report modules.*

### Task 25: Background sync
**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/platform/PlatformScheduler.kt` (expect)
- Create: `app/src/jvmMain/kotlin/org/commcare/app/platform/PlatformScheduler.kt` (actual - timer)
- Create: `app/src/iosMain/kotlin/org/commcare/app/platform/PlatformScheduler.kt` (actual - BGTaskScheduler)
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/SyncViewModel.kt`

**What to do:**
- Create `PlatformScheduler` expect/actual for periodic background tasks
- iOS: register `BGAppRefreshTask` and `BGProcessingTask` via BGTaskScheduler
- JVM: use `java.util.Timer` for periodic sync
- Schedule auto-sync based on settings interval (15min, 30min, 1hr, manual)
- Submit pending forms in background before sync
- Handle background sync completion notification

### Task 26: Graphing (WebView charts)
**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/ui/GraphScreen.kt`
- Create: `app/src/commonMain/kotlin/org/commcare/app/platform/PlatformWebView.kt` (expect)
- Create: `app/src/iosMain/kotlin/org/commcare/app/platform/PlatformWebView.kt` (actual - WKWebView)
- Create: `app/src/jvmMain/kotlin/org/commcare/app/platform/PlatformWebView.kt` (actual - stub)

**What to do:**
- Parse graph configuration from case detail `<graph>` elements
- Generate HTML/JavaScript using C3.js or similar charting library
- Render in platform WebView (iOS: WKWebView via cinterop)
- Support chart types: bar, line, pie, bubble
- Inject case data into chart via JavaScript bridge
- Display in case detail tabs

### Task 27: Report modules (UCR)
**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/ui/ReportScreen.kt`
- Create: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/ReportViewModel.kt`

**What to do:**
- Parse report module configuration from suite.xml
- Fetch UCR report data from CommCare HQ endpoint
- Render report in WebView (HTML table format)
- Support report filters (date range, case type)
- Cache report data for offline viewing
- Add report modules to menu navigation

### Task 28: Oracle tests for reporting
**Files:**
- Create: `app/src/jvmTest/kotlin/org/commcare/app/oracle/ReportingOracleTest.kt`

**What to do:**
- Test graph configuration parsing
- Test chart HTML generation
- Test report data fetch and caching
- Test report filter application

## Wave 8: Localization, Printing & Support (4 tasks)

*Alternative calendars, printing, recovery mode, crash logging.*

### Task 29: Alternative calendars (Ethiopian, Nepali)
**Files:**
- Create: `commcare-core/src/commonMain/kotlin/org/javarosa/core/model/utils/EthiopianCalendar.kt`
- Create: `commcare-core/src/commonMain/kotlin/org/javarosa/core/model/utils/NepaliCalendar.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/FormEntryScreen.kt`

**What to do:**
- Implement Ethiopian calendar conversion (Ge'ez calendar, 13 months)
- Implement Nepali calendar conversion (Bikram Sambat)
- Check `appearance="ethiopian"` or `appearance="nepali"` on date widgets
- Format date display using alternative calendar
- Convert to/from Gregorian for storage (engine uses Gregorian internally)

### Task 30: Printing support
**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/platform/PlatformPrinting.kt` (expect)
- Create: `app/src/iosMain/kotlin/org/commcare/app/platform/PlatformPrinting.kt` (actual - UIPrintInteractionController)
- Create: `app/src/jvmMain/kotlin/org/commcare/app/platform/PlatformPrinting.kt` (actual - stub)
- Create: `app/src/commonMain/kotlin/org/commcare/app/engine/PrintTemplateEngine.kt`

**What to do:**
- Parse print templates from suite configuration
- Implement HTML template engine with case/form data substitution
- Generate printable HTML from template + data
- iOS: render HTML to PDF and present via UIPrintInteractionController
- Support ZPL label printing (raw text to Zebra printers via Bluetooth)
- Add "Print" action in case detail and form completion

### Task 31: Recovery mode
**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/ui/RecoveryScreen.kt`
- Create: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/RecoveryViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/SettingsScreen.kt`

**What to do:**
- Add "Recovery Mode" entry point in settings
- List unsent forms with option to force-submit or delete
- View application logs (last N entries)
- "Clear User Data" — wipe local data and force fresh sync
- "Submit Logs" — send device logs to CommCare HQ for debugging
- Export form data as XML for manual recovery

### Task 32: Crash logging and device reporting
**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/platform/PlatformCrashReporter.kt` (expect)
- Create: `app/src/iosMain/kotlin/org/commcare/app/platform/PlatformCrashReporter.kt` (actual)
- Create: `app/src/jvmMain/kotlin/org/commcare/app/platform/PlatformCrashReporter.kt` (actual)

**What to do:**
- Capture uncaught exceptions and write to local log file
- Include device info: OS version, app version, free storage, last sync time
- Queue crash reports for upload on next sync
- iOS: use `NSSetUncaughtExceptionHandler` + signal handlers
- Structured log format for server-side analysis
- Integrate with recovery mode log viewer

## Dependency Graph

```
Wave 1 (Case Advanced)      Wave 2 (Menu Advanced)
    │                            │
    ├─ Task 1,2 ──────────┐     ├─ Task 5,6,7
    ├─ Task 3 (tiered)     │     │
    └─ Task 4 (actions)    │     │
                           │     │
                     Wave 3 (Session Stack) ←── depends on menu + case
                           │
                     Wave 4 (Case Search) ←── depends on case tiles
                           │
                     Wave 5 (App Lifecycle) ←── independent
                           │
                     Wave 6 (Auth & Platform) ←── independent
                           │
                     Wave 7 (Background & Reports) ←── depends on sync
                           │
                     Wave 8 (Localization & Support) ←── independent
```

Waves 1-2 can run in parallel. Waves 5-6 are independent of 3-4.

## Risk Mitigations

1. **Platform-specific APIs**: Biometric, BGTaskScheduler, WKWebView, printing require iOS cinterop. Start with expect/actual stubs, fill in iOS implementation iteratively.
2. **Case search requires server**: Use mock HTTP responses in oracle tests. Real integration tested manually.
3. **WebView for graphing**: WKWebView cinterop is complex. Start with static HTML generation and test rendering manually.
4. **Session stack complexity**: CommCareSession state machine is intricate. Study existing FormPlayer tests for edge cases.
5. **Background sync on iOS**: BGTaskScheduler has strict execution limits (30s for refresh, 5min for processing). Design sync to be incremental and resumable.

## Exit Criteria

- All 32 tasks implemented with oracle tests per wave
- Case search & claim workflow end-to-end
- Session stack with form linking (chained forms)
- Grid menus, display conditions, shadow modules
- Biometric login on iOS
- Background sync on iOS
- Correctness scorecard 95%+ (cross-platform tests)
- All existing 800+ JVM tests still pass
- All 100+ cross-platform tests still pass
