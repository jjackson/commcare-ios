# Phase 8: iOS App Implementation Plan

**Date**: 2026-03-12
**Prerequisite**: Phases 1-7 complete (643 commonMain, 100 jvmMain, 42 iosMain)

## Current State

The KMP engine migration is complete. The iOS app shell launches on simulator and links the engine framework, but displays only a static screen. The 643 commonMain files contain the full CommCare engine (forms, cases, sessions, XPath, serialization, resources) callable from iOS. The 42 iosMain files provide platform adapters — 18 fully implemented, 11 stubs requiring native framework integration.

### What Works on iOS Today
- XML parsing/serialization (pure Kotlin state-machine parser)
- Binary serialization (DataInputStream/DataOutputStream)
- Date/time/calendar (NSDate/NSCalendar interop)
- Localization framework
- XPath evaluation engine
- Form model, case model, session model
- Resource management framework

### What's Stubbed (Throws TODO/NotImplementedError)
- **PlatformCrypto** — SHA-256, MD5, AES encrypt/decrypt, secure random (needs CommonCrypto cinterop)
- **PlatformFiles** — read/write bytes, exists, delete, list, temp files (needs NSFileManager)
- **PlatformHttpClient** — HTTP requests (needs NSURLSession)
- **PlatformJson** — basic string parsing works, TODO for real JSONSerialization

### What's Intentionally Missing on iOS
- kxml2 XML library (replaced by pure Kotlin parser)
- OkHttp/Retrofit networking (replaced by URLSession)
- javax.crypto (replaced by CommonCrypto/CryptoKit)
- SQL database helpers (need iOS SQLite or equivalent)
- Graph rendering (C3.js-based, defer to later)
- Tracing/observability (no-op on iOS)

## Phase 8 Architecture

```
┌─────────────────────────────────────────────┐
│  Compose Multiplatform UI                   │
│  Login, Menus, Form Entry, Case Lists       │
├─────────────────────────────────────────────┤
│  ViewModels (commonMain, pure Kotlin)       │
│  AppState, Navigation, FormEntryVM, SyncVM  │
├─────────────────────────────────────────────┤
│  CommCare Engine (commonMain, 643 files)    │
│  Forms, Cases, XPath, Sessions, Resources   │
├─────────────────────────────────────────────┤
│  Platform Adapters (iosMain / jvmMain)      │
│  Storage, Crypto, Network, Files            │
└─────────────────────────────────────────────┘
```

## Waves

### Wave 1: Platform Adapters — Crypto & Files (~5 files)

**Goal**: Implement real iOS crypto and file system operations so the engine can read/write data.

**Files to implement:**
- `PlatformCrypto.kt` (iosMain) — CommonCrypto cinterop for SHA-256, MD5, AES-GCM, SecRandomCopyBytes
- `PlatformFiles.kt` (iosMain) — NSFileManager for read/write/exists/delete/list/tempFile
- Gradle: add `cinterops` block for CommonCrypto in build.gradle.kts

**What to do:**
1. Configure Kotlin/Native cinterop for CommonCrypto (`cinterops { create("CommonCrypto") { ... } }`)
2. Implement SHA-256 using `CC_SHA256`
3. Implement MD5 using `CC_MD5`
4. Implement AES-GCM using `CCCrypt` with `kCCAlgorithmAES`
5. Implement secure random using `SecRandomCopyBytes`
6. Implement file operations using `NSFileManager.defaultManager()`
7. Map app sandbox paths (`NSSearchPathForDirectoriesInDomains`)

**Tests:**
- [ ] Cross-platform crypto test: encrypt on JVM, decrypt on iOS (and vice versa)
- [ ] File read/write round-trip test in commonTest
- [ ] `compileCommonMainKotlinMetadata` passes
- [ ] `iosSimulatorArm64Test` passes

**Risk**: CommonCrypto cinterop configuration can be tricky. AES-GCM mode may need Security framework instead of CommonCrypto (which only supports CBC/ECB natively). May need to use `SecKeyEncrypt`/`CCCryptorGCM` or CryptoKit via Swift interop.

---

### Wave 2: Platform Adapters — Networking & JSON (~3 files)

**Goal**: Real HTTP client so the app can communicate with CommCare HQ.

**Files to implement:**
- `PlatformHttpClient.kt` (iosMain) — NSURLSession-based HTTP client
- `PlatformJson.kt` (iosMain) — NSJSONSerialization for proper JSON parsing
- `PlatformUrl.kt` (iosMain) — verify/fix NSURL-based URL handling

**What to do:**
1. Implement `IosHttpClient.execute()` using `NSURLSession.sharedSession()` with `dataTaskWithRequest`
2. Handle request headers, body, timeouts, SSL
3. Map HTTP response codes and body to engine's response model
4. Replace regex-based JSON parsing with `NSJSONSerialization`
5. Ensure URL encoding/decoding matches JVM behavior

**Tests:**
- [ ] HTTP GET/POST against mock server (or integration test against CommCare HQ staging)
- [ ] JSON parsing matches JVM output for sample payloads
- [ ] `iosSimulatorArm64Test` passes

---

### Wave 3: Storage Layer (~8-10 new files)

**Goal**: SQLite-based persistent storage on iOS, implementing `IStorageUtilityIndexed` and related interfaces.

**What to build:**
- `IosSqliteHelper.kt` — SQLite C API wrapper via cinterop (or use SQLDelight/SQLiter library)
- `IosSqliteStorage.kt` — implements `IStorageUtilityIndexed<T>` for arbitrary Persistable types
- `IosUserSandbox.kt` — implements `UserSandbox` interface (user-scoped storage)
- `IosStorageFactory.kt` — implements `IStorageIndexedFactory` for creating storage instances
- Storage migration support (schema versioning)

**Architecture decision**: Use raw SQLite C API via Kotlin/Native cinterop, OR use a library:
- **SQLDelight** — type-safe SQL, KMP-native, widely used. Adds dependency but is well-maintained.
- **SQLiter** — minimal SQLite wrapper for Kotlin/Native. Lighter but less featured.
- **Raw cinterop** — no dependencies, but more boilerplate.

**Recommendation**: SQLDelight for production quality, with schema matching JVM's DatabaseHelper/TableBuilder output.

**Tests:**
- [ ] Storage CRUD operations (write, read, iterate, remove)
- [ ] Index queries match JVM behavior
- [ ] Fixture storage round-trip
- [ ] `iosSimulatorArm64Test` passes

---

### Wave 4: App Foundation — Login & App Install (~10-12 new files)

**Goal**: User can launch the app, enter HQ credentials, and install a CommCare application.

**What to build:**

*ViewModels (commonMain):*
- `LoginViewModel.kt` — manages login state, credential validation, auth token storage
- `AppInstallViewModel.kt` — manages app download, resource parsing, installation progress
- `AppState.kt` — top-level app state machine (LoggedOut → Installing → Ready → InSession)

*UI (app/commonMain, Compose):*
- `LoginScreen.kt` — username/password fields, server URL, login button
- `AppInstallScreen.kt` — progress indicator, resource download status
- `Navigation.kt` — screen routing based on AppState

*iOS Platform:*
- `IosCredentialStorage.kt` — Keychain-backed credential storage
- Integrate `PlatformHttpClient` for HQ API calls

**Flow:**
1. App launches → LoginScreen
2. User enters HQ URL + credentials → POST to `/a/{domain}/receiver/` (or login endpoint)
3. On success → store auth token in Keychain
4. Fetch app profile → parse resource table → download resources
5. Install resources (forms, fixtures, multimedia) to local storage
6. Navigate to menu screen

**Tests:**
- [ ] Login flow with mock HQ server
- [ ] App install with test .ccz bundle
- [ ] Resource parsing and storage
- [ ] State machine transitions

---

### Wave 5: Menu Navigation & Session (~6-8 new files)

**Goal**: User can navigate CommCare module menus and start form/case sessions.

**What to build:**

*ViewModels (commonMain):*
- `MenuViewModel.kt` — loads menu structure from CommCarePlatform, handles selection
- `SessionViewModel.kt` — manages CommCareSession state, datum requirements

*UI (app/commonMain, Compose):*
- `MenuScreen.kt` — list of modules/forms with icons and text
- `SessionNavigator.kt` — handles session steps (menu → datum → form)

**Flow:**
1. CommCarePlatform loads installed suite.xml
2. MenuScreen displays top-level entries from suite
3. User selects entry → SessionViewModel evaluates session requirements
4. If datum needed → show case list (Wave 6)
5. If form needed → launch form entry (Wave 7)

**Tests:**
- [ ] Menu loads from test app suite.xml
- [ ] Session navigation produces correct datum requests
- [ ] Breadcrumb/back navigation works

---

### Wave 6: Case Management (~8-10 new files)

**Goal**: Case lists, case details, case selection, case search.

**What to build:**

*ViewModels (commonMain):*
- `CaseListViewModel.kt` — loads cases from storage, applies filters/sort
- `CaseDetailViewModel.kt` — loads case detail fields

*UI (app/commonMain, Compose):*
- `CaseListScreen.kt` — scrollable list with search, sort headers
- `CaseDetailScreen.kt` — key-value detail display
- `CaseTileLayout.kt` — configurable tile display (grid/list)

**Flow:**
1. Session requires a case datum
2. CaseListViewModel queries storage for matching cases
3. User selects case → datum fulfilled → session continues
4. Optional: case search against HQ remote search API

**Tests:**
- [ ] Case list loads from test fixture data
- [ ] Case selection fulfills session datum
- [ ] Search/filter produces correct results

---

### Wave 7: Form Entry (~15-20 new files)

**Goal**: Full form entry with all question types, validation, and submission. This is the largest wave.

**What to build:**

*ViewModels (commonMain):*
- `FormEntryViewModel.kt` — wraps FormEntryController/FormEntryModel, manages navigation
- `QuestionViewModel.kt` — per-question state (value, constraint, required, relevant)

*UI (app/commonMain, Compose):*
- `FormEntryScreen.kt` — question display with next/back navigation
- `QuestionWidget.kt` — base composable for question rendering
- Question type widgets:
  - `TextWidget.kt` — text/numeric/phone input
  - `SelectOneWidget.kt` — radio buttons / dropdown
  - `SelectMultiWidget.kt` — checkboxes
  - `DateWidget.kt` — date picker
  - `LabelWidget.kt` — read-only display
  - `GeoWidget.kt` — location capture (defer GPS to later)
  - `GroupWidget.kt` — field-list group rendering
  - `RepeatWidget.kt` — repeat group management (add/delete)
- `FormSubmissionScreen.kt` — submission confirmation, retry on failure

**Flow:**
1. Session provides form path → FormEntryViewModel loads XForm
2. Navigate through questions (next/back/jump)
3. Each question evaluates: relevant, required, constraint, calculate
4. On completion → serialize form XML → POST to HQ
5. Process case transactions from form submission

**Tests:**
- [ ] Basic form load and navigation
- [ ] Each question type renders and accepts input
- [ ] Skip logic (relevant) works
- [ ] Constraints validate correctly
- [ ] Form XML output matches expected format
- [ ] Oracle test: compare form submission XML with FormPlayer output

---

### Wave 8: Sync & Offline (~6-8 new files)

**Goal**: Full data sync with CommCare HQ, offline form queue.

**What to build:**

*ViewModels (commonMain):*
- `SyncViewModel.kt` — manages sync state, progress, errors
- `FormQueueViewModel.kt` — tracks unsent forms, retry logic

*Platform (iosMain):*
- `IosSyncScheduler.kt` — BGTaskScheduler for background sync (iOS 13+)

*Sync implementation:*
- Restore: GET user data (cases, fixtures) from HQ → parse → store
- Submit: POST queued forms → process responses → update case state
- Incremental sync: state hash comparison, delta updates

**Tests:**
- [ ] Full sync with test HQ instance
- [ ] Offline form queue persists across app restart
- [ ] Incremental sync correctly identifies changes
- [ ] Conflict handling (server wins)

---

### Wave 9: Settings, Polish & Verification (~5-8 new files)

**Goal**: App settings, developer tools, and verification infrastructure.

**What to build:**
- Settings screen (server URL, sync frequency, developer options)
- Crash reporting integration
- Oracle test harness (compare iOS output with FormPlayer)
- Performance profiling on real device

**Verification (per design doc):**
- Layer 2 (Oracle): Load test forms, run through engine, compare XML output with FormPlayer fixtures
- Layer 3 (Parity): Side-by-side comparison with Android output for same test apps
- Correctness scorecard CI reporting

**Exit criteria:**
- [ ] Oracle tests pass for 20+ test forms
- [ ] All 800+ JVM tests still pass
- [ ] iOS simulator tests pass
- [ ] App can: login, install app, navigate menus, fill forms, submit, sync

## Dependencies & Ordering

```
Wave 1 (Crypto/Files) ──┐
                         ├── Wave 3 (Storage) ── Wave 4 (Login/Install) ── Wave 5 (Menus)
Wave 2 (Network/JSON) ──┘                                                       │
                                                                    ┌────────────┤
                                                                    ↓            ↓
                                                              Wave 6 (Cases)  Wave 7 (Forms)
                                                                    │            │
                                                                    └────┬───────┘
                                                                         ↓
                                                                   Wave 8 (Sync)
                                                                         ↓
                                                                   Wave 9 (Polish)
```

Waves 1-2 can run in parallel. Wave 3 depends on both. Waves 4-5 are sequential. Waves 6-7 can partially overlap. Wave 8 depends on all prior. Wave 9 is final.

## Estimated Scope

| Wave | New Files | Modified Files | Complexity |
|------|-----------|---------------|------------|
| 1. Crypto & Files | ~5 | ~2 (gradle) | Medium (cinterop) |
| 2. Network & JSON | ~3 | ~1 | Medium |
| 3. Storage | ~8-10 | ~2 | High (SQLite) |
| 4. Login & Install | ~10-12 | ~3 | High |
| 5. Menus & Session | ~6-8 | ~2 | Medium |
| 6. Cases | ~8-10 | ~2 | Medium |
| 7. Form Entry | ~15-20 | ~5 | Very High |
| 8. Sync & Offline | ~6-8 | ~3 | High |
| 9. Polish & Verify | ~5-8 | ~5 | Medium |
| **Total** | **~65-80** | **~25** | |

## Key Technical Decisions Needed

1. **SQLite library**: SQLDelight vs SQLiter vs raw cinterop? Recommend SQLDelight for type safety and KMP support.

2. **AES-GCM on iOS**: CommonCrypto doesn't natively support GCM mode. Options:
   - CryptoKit (iOS 13+, Swift-only — needs Swift/Kotlin bridge)
   - `CCCryptorGCM` (available but poorly documented)
   - OpenSSL via cinterop (complex but complete)

3. **Background sync**: BGTaskScheduler (iOS 13+) or silent push notifications? BGTaskScheduler is simpler.

4. **Media capture**: AVFoundation (native) or Compose camera? Defer to later wave.

5. **Navigation framework**: Compose Navigation (experimental on iOS) or custom state machine? Recommend custom AppState-driven navigation for stability.

## Risk Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| CommonCrypto cinterop complexity | Blocks Wave 1 | Start with SHA-256/MD5 (simple), defer AES-GCM |
| SQLite schema mismatch with JVM | Blocks sync | Generate schema from same TableBuilder definitions |
| Compose Multiplatform iOS rendering bugs | UI glitches | Test on multiple iOS versions, fall back to UIKit |
| Form entry complexity (all question types) | Wave 7 takes too long | Start with text/select/date, add types incrementally |
| HQ API compatibility | Blocks login/sync | Test against staging HQ, use same API as Android |
