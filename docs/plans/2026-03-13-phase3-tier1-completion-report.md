# Phase 3 Tier 1: Minimum Viable App — Completion Report

**Date:** 2026-03-13
**Tasks completed:** 14/14
**JVM tests:** 16 passing (8 sandbox, 3 oracle, 5 e2e)
**iOS build:** Compiles (native tests require macOS runner)

## Summary

Phase 3 Tier 1 delivers a Minimum Viable App with one complete user journey: login → install → menu → case → form → submit → sync, wired to real CommCare engine APIs. All 14 tasks implemented in a single session.

## What Was Built

### Infrastructure (Tasks 1-3)
- **JVM target** added to app module (no Android SDK available on build server)
- **SQLDelight** schema with 8 tables: cases, case_properties, case_indices, users, fixtures, form_queue, resources, ledgers
- **InMemoryStorage<T>** — cross-platform `IStorageUtilityIndexed` implementation (~280 lines) using HashMap with IMetaData-based indexing
- **SqlDelightUserSandbox** — `UserSandbox` implementation using InMemoryStorage for all engine storage types, SQLDelight for persistence
- **DatabaseDriverFactory** — expect/actual for JVM (JdbcSqliteDriver) and iOS (NativeSqliteDriver), with encryption key support

### Core Engine Wiring (Tasks 4-8)
- **LoginViewModel** rewritten: authenticates, parses restore XML via `ParseUtils.parseIntoSandbox()`, extracts sync token, runs `AppInstaller`
- **AppInstaller** — installs CommCare app from profile URL using `ResourceManager.installAppResources()`, with `createMinimalPlatform()` fallback
- **SessionNavigatorImpl** — wraps `SessionWrapper`, dispatches `getNeededData()` to `NavigationStep` sealed class (ShowMenu, ShowCaseList, StartForm, SyncRequired, Error)
- **MenuViewModel** refactored to use `SessionNavigatorImpl` for session-aware navigation
- **CaseListViewModel** updated with `SessionNavigatorImpl` + sandbox integration, datum-based case type filtering
- **FormEntrySession** — wraps FormDef/FormEntryModel/FormEntryController for simpler ViewModel integration
- **FormEntryViewModel** rewritten with real engine integration: question navigation, answer validation, constraint messages, multi-question display
- **FormEntryScreen** rewritten for list-based question rendering (text, integer, select-one, labels)

### Submission & Sync (Tasks 9-10)
- **FormSerializer** — cross-platform form serialization using `DataModelSerializer` + `PlatformXmlSerializer` (works on both JVM and iOS, no kxml2 dependency)
- **FormQueueViewModel** updated with SQLDelight persistence, form enqueue/submit/status tracking
- **SyncViewModel** rewritten with real `ParseUtils.parseIntoSandbox()` for incremental sync, sync token management, form queue integration

### Storage Security (Task 11)
- **Encrypted driver** support added to DatabaseDriverFactory (PRAGMA key for SQLCipher-compatible drivers on JVM, iOS Data Protection fallback)

### Testing (Tasks 12-13)
- **Oracle test harness** — `OracleTestRunner` loads XForm fixtures, fills with answers, compares cross-platform `FormSerializer` output against JVM-only `XFormSerializingVisitor`
- **End-to-end validation** — 5 tests covering sandbox CRUD, form entry + serialization, form entry with answers, InMemoryStorage operations, SQLDelight form queue persistence

## File Inventory

### New Files (14)
| File | Lines | Purpose |
|------|-------|---------|
| `engine/AppInstaller.kt` | ~60 | App profile installation |
| `engine/FormEntrySession.kt` | 103 | FormDef/Controller wrapper |
| `engine/FormSerializer.kt` | 25 | Cross-platform form XML serialization |
| `engine/SessionNavigatorImpl.kt` | ~80 | Session state machine dispatcher |
| `storage/CommCare.sq` | 210 | SQLDelight schema + queries |
| `storage/DatabaseDriverFactory.kt` (common) | 12 | Expect declaration |
| `storage/DatabaseDriverFactory.kt` (jvm) | 25 | JVM actual with encryption |
| `storage/DatabaseDriverFactory.kt` (ios) | 16 | iOS actual |
| `storage/InMemoryStorage.kt` | ~280 | Cross-platform IStorageUtilityIndexed |
| `storage/SqlDelightUserSandbox.kt` | ~100 | UserSandbox implementation |
| `oracle/OracleTestRunner.kt` | ~120 | Oracle comparison framework |
| `oracle/OracleComparisonTest.kt` | ~95 | Oracle comparison tests |
| `e2e/FullJourneyTest.kt` | ~160 | End-to-end validation tests |
| `storage/SqlDelightUserSandboxTest.kt` | ~80 | Sandbox unit tests |

### Modified Files (11)
| File | Changes |
|------|---------|
| `build.gradle.kts` | JVM target, SQLDelight plugin, dependencies |
| `App.kt` | Accept CommCareDatabase parameter |
| `AppState.kt` | Ready state carries platform, sandbox, server config |
| `FormEntryScreen.kt` | List-based question rendering |
| `CaseListViewModel.kt` | SessionNavigator + sandbox integration |
| `FormEntryViewModel.kt` | Real engine integration, serialization |
| `FormQueueViewModel.kt` | SQLDelight persistence, encryption |
| `LoginViewModel.kt` | Real auth + restore parsing |
| `MenuViewModel.kt` | SessionNavigator integration |
| `SyncViewModel.kt` | Real restore parsing, sync tokens |
| `MainViewController.kt` | Database creation |

## Test Results

```
16 tests, 16 passing, 0 failing

Sandbox tests (8):
  ✓ testCaseStorageCrud
  ✓ testMetaIndexQuery
  ✓ testUserStorage
  ✓ testLedgerStorage
  ✓ testLoggedInUser
  ✓ testSyncToken
  ✓ testFormQueuePersistence
  ✓ testMultipleStorageTypes

Oracle tests (3):
  ✓ testEmptyFormSerialization
  ✓ testFormWithIntegerAnswers
  ✓ testFormFillAndSerialize

E2E tests (5):
  ✓ testSandboxStorageCRUD
  ✓ testFormEntryAndSerialization
  ✓ testFormEntryWithAnswers
  ✓ testInMemoryStorageOperations
  ✓ testSqlDelightFormQueuePersistence
```

## Notable Technical Decisions

1. **JVM target instead of Android** — No Android SDK available on build server. Used `jvm()` target with `JdbcSqliteDriver`. Android target can be added later.

2. **InMemoryStorage in commonMain** — Created a new cross-platform `IStorageUtilityIndexed` implementation rather than using the iOS-only `IosInMemoryStorage`. This ensures identical storage behavior on both platforms.

3. **DataModelSerializer for cross-platform serialization** — XFormSerializingVisitor is JVM-only (kxml2). Created `FormSerializer` using the commonMain `DataModelSerializer` + `PlatformXmlSerializer`, which works on both JVM and iOS.

4. **Oracle tests as JVM tests** — Since XFormParser and XFormSerializingVisitor are JVM-only, the oracle comparison tests live in jvmTest. This is acceptable since the oracle validates our commonMain serializer against the JVM reference implementation.

5. **Encryption infrastructure** — Added `createEncryptedDriver()` to DatabaseDriverFactory with PRAGMA key support. On standard sqlite-jdbc this is a no-op; with SQLCipher JDBC it enables encryption. iOS uses OS-level Data Protection.

## Known Limitations

- **No live server testing** — All tests use local fixtures. Live authentication requires a running CommCare HQ instance.
- **XFormSerializingVisitor vs DataModelSerializer** — The cross-platform serializer may produce slightly different XML (whitespace, namespace handling) compared to XFormSerializingVisitor. Oracle tests validate this.
- **Form types** — Only TEXT, INTEGER, SELECT_ONE are fully rendered in the UI. Date, time, select-multi need additional UI work.
- **No repeat group support** — Form entry navigation skips repeat groups (EVENT_PROMPT_NEW_REPEAT) without expanding them.

## Tier 2 Readiness

The Tier 1 MVP establishes the complete data flow. Tier 2 should focus on:
1. Repeat group handling in form entry
2. Date/time picker UI
3. Select-multi UI
4. Image/audio media capture
5. Offline-first queue reliability
6. Performance testing with large case lists
7. Live server integration testing
