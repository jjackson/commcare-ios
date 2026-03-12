# Phase 8 Completion Report: iOS App Implementation

**Date**: 2026-03-12
**Status**: Complete — all 9 waves merged

## Summary

Phase 8 built the complete iOS app shell with Compose Multiplatform, implementing the full user-facing feature set: authentication, app installation, menu navigation, case management, form entry, data sync, and settings. All 9 waves passed CI and merged to main.

## Wave Summary

| Wave | Description | PR | Files | Status |
|------|-------------|-----|-------|--------|
| 1 | Platform abstractions | #178 | 4 new | Merged |
| 2 | Networking & JSON | #179 | 6 new/mod | Merged |
| 3 | Storage layer | #180 | 4 new | Merged |
| 4 | Login & app state | #181 | 5 new | Merged |
| 5 | Menu navigation | #182 | 2 new | Merged |
| 6 | Case management | #183 | 3 new | Merged |
| 7 | Form entry | #184 | 2 new | Merged |
| 8 | Sync & offline queue | #185 | 3 new | Merged |
| 9 | Settings & debug | #186 | 3 new | Merged |

## File Inventory

### ViewModels (commonMain)
- `LoginViewModel.kt` — HQ authentication, Basic Auth, app state management
- `MenuViewModel.kt` — menu navigation from CommCarePlatform suites
- `CaseListViewModel.kt` — case loading, search/filter, selection
- `FormEntryViewModel.kt` — question state, validation, navigation stubs
- `SyncViewModel.kt` — HQ data restore with incremental sync tokens
- `FormQueueViewModel.kt` — offline form submission queue with retry
- `SettingsViewModel.kt` — server URL, sync frequency, developer mode

### UI Screens (commonMain)
- `LoginScreen.kt` — Material3 login form
- `InstallScreen.kt` — installation progress indicator
- `HomeScreen.kt` — post-install ready screen
- `MenuScreen.kt` — menu list with Card-based items
- `CaseListScreen.kt` — case list with search
- `CaseDetailScreen.kt` — case property display
- `FormEntryScreen.kt` — question display with input fields
- `SyncScreen.kt` — sync status and unsent form queue
- `SettingsScreen.kt` — app settings form
- `DebugInfoScreen.kt` — developer diagnostics

### State Management (commonMain)
- `AppState.kt` — sealed class state machine (LoggedOut → LoggingIn → Installing → Ready)

### Platform Layer (iosMain)
- `IosInMemoryStorage.kt` — full IStorageUtilityIndexed implementation
- `IosStorageFactory.kt` — IStorageIndexedFactory using PrototypeFactory
- `IosUserSandbox.kt` — UserSandbox with case/ledger/user/fixture storage
- `IosStorageIterator.kt` — HashMap-based iterator
- `PlatformJson.kt` — NSJSONSerialization-based JSON parser (upgraded from regex)

### Cross-Platform Tests
- `PlatformJsonTest.kt` — 7 JSON parsing tests
- `PlatformUrlTest.kt` — 4 URL parsing tests

## Key Technical Decisions

1. **NSURLSession with dispatch_semaphore**: PlatformHttpClient requires synchronous `execute()`. iOS implementation blocks with semaphore to match interface contract.

2. **NSJSONSerialization over regex**: Upgraded iOS JSON parser from regex-based to Foundation's NSJSONSerialization for correctness with nested objects and special characters.

3. **IosInMemoryStorage pattern**: Created separate iOS storage implementation instead of moving DummyIndexedStorageUtility to commonMain (blocked by `Class<T>` constructor used by 10+ Java callers).

4. **KClass + factory lambda for iOS**: PrototypeFactory on iOS uses registered factory lambdas since JVM reflection isn't available. Storage created via `PrototypeFactory.createInstance(KClass)`.

5. **Text.evaluate() without EvaluationContext**: Menu text can be evaluated without an EvaluationContext for flat/locale text strings, avoiding the need to construct a full evaluation environment for menu display.

6. **Nullable Case API**: `Case.getCaseId()` and `Case.getTypeId()` return `String?` in KMP commonMain. All callers use null-safety operators.

## Issues Encountered

- **Wave 2**: Accidentally committed to main instead of branch (working directory was in commcare-core/ subdirectory, branch checkout didn't stick)
- **Wave 3**: IndexedFixtureIdentifier constructor takes `(String, String, ByteArray?)`, not `(String, String, TreeElement)` — fixed by passing `null` for rootAttributes
- **Wave 4**: Complex git rebase needed after squash merge created conflicts
- **Wave 6**: CI failure from nullable `getCaseId()`/`getTypeId()` — fixed with `?: ""` operators

## What's Next

Phase 8 completes the app shell. The remaining work falls into:

1. **Phase 5 (Serialization Framework Refactor)**: Replace `Class<*>` with `KClass<*>` in ExtUtil/ExtWrap* to unblock bulk migration of ~300+ files to commonMain
2. **Deep engine integration**: Connect scaffold ViewModels to real CommCare engine APIs (FormEntryController, SessionNavigator, etc.)
3. **Oracle testing**: Compare iOS engine output with FormPlayer fixtures
4. **Platform implementations**: Real SQLite storage, background sync via BGTaskScheduler, file system access
