# Phase 7: Full Android Parity

**Date:** 2026-03-22
**Status:** Plan
**Prerequisite:** Phases 1-6 complete. App on TestFlight. 1,162+ tests passing. Code review fixes merged.

## Overview

A systematic comparison of the iOS app against Android CommCare revealed 15 missing features and 22 areas where existing features lack test coverage. This phase closes all gaps to achieve full feature parity with Android CommCare for production field deployments.

### Goals

- Implement all 15 missing features identified in the Android parity gap analysis
- Fill all 22 test coverage gaps for existing implemented features
- Replace the XOR cipher placeholder with production-grade AES-GCM encryption
- Achieve complete select widget appearance support (minimal, compact, quick, combobox, label)
- Implement form chaining / end-of-form navigation for real multi-form workflows
- Add incremental sync for performance with large case loads

### Exit Criteria

- AES-GCM encryption replaces XOR placeholder in UserKeyRecordManager
- All select appearance variants render correctly (minimal, compact, quick, combobox, label, list-nolabel)
- Form end navigation routes to correct destination (menu, case list, chained form)
- Inline multimedia (audio/image) renders within question labels
- Incremental sync skips full re-parse when server data hasn't changed
- Video capture and document upload widgets functional on iOS
- Case detail tabs display with conditional visibility
- All 22 test gaps filled with passing tests
- Total test count ≥ 1,250
- `./gradlew jvmTest` passes for both commcare-core and app
- `compileCommonMainKotlinMetadata` passes

---

## Waves

### Wave 1: AES Encryption at Rest

**Rationale:** The XOR cipher in UserKeyRecordManager is a dev-phase placeholder. This is a security-critical fix that must ship before any production deployment. Blocking all other work until credentials are properly encrypted.

**Files to Read:**
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/UserKeyRecordManager.kt` (lines 150-183 — XOR cipher + PIN hash)
- `commcare-core/src/commonMain/kotlin/org/commcare/core/encryption/PlatformCrypto.kt` (existing crypto abstractions)
- `commcare-core/src/iosMain/kotlin/org/commcare/core/encryption/PlatformCrypto.kt` (iOS crypto implementation)
- `commcare-core/src/jvmMain/kotlin/org/commcare/core/encryption/PlatformCrypto.kt` (JVM crypto implementation)

**Tasks:**

1. **Add AES-GCM encrypt/decrypt to PlatformCrypto** — Extend the existing `expect`/`actual` PlatformCrypto with `aesGcmEncrypt(plaintext: ByteArray, key: ByteArray): ByteArray` and `aesGcmDecrypt(ciphertext: ByteArray, key: ByteArray): ByteArray`. iOS: use CommonCrypto/CryptoKit. JVM: use `javax.crypto.Cipher` with AES/GCM/NoPadding.
   - Files: `commcare-core/src/commonMain/.../PlatformCrypto.kt`, `commcare-core/src/iosMain/.../PlatformCrypto.kt`, `commcare-core/src/jvmMain/.../PlatformCrypto.kt`

2. **Add PBKDF2 key derivation** — Replace the polynomial `hashPin()` with PBKDF2-HMAC-SHA256 (100,000 iterations). Derive a 256-bit AES key from the user's PIN/password + random salt. Store salt alongside encrypted data.
   - File: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/UserKeyRecordManager.kt`

3. **Replace xorEncrypt/xorDecrypt with AES-GCM** — Swap the XOR cipher calls with `PlatformCrypto.aesGcmEncrypt()` / `aesGcmDecrypt()`. Handle migration: if stored data is XOR-encrypted (legacy), decrypt with XOR, re-encrypt with AES on first access.
   - File: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/UserKeyRecordManager.kt`

4. **Add crypto tests** — Test AES-GCM round-trip, PBKDF2 determinism, key derivation with known vectors, migration from XOR to AES.
   - Files: `commcare-core/src/commonTest/.../PlatformCryptoTest.kt` (extend existing), new `app/src/jvmTest/.../UserKeyRecordCryptoTest.kt`

**Acceptance:**
- [ ] `xorEncrypt`/`xorDecrypt` methods removed from UserKeyRecordManager
- [ ] AES-GCM encrypt/decrypt works on both JVM and iOS
- [ ] PBKDF2 key derivation produces deterministic output for same input
- [ ] Migration path: old XOR-encrypted credentials automatically re-encrypted
- [ ] `./gradlew :commcare-core:jvmTest` passes (crypto tests)
- [ ] `./gradlew :app:jvmTest` passes

---

### Wave 2: Select Appearance Variants

**Rationale:** Android CommCare renders select questions differently based on the `appearance` attribute (dropdown, grid, auto-advance, etc.). Our app ignores all appearances except `multiline`/`numeric` on text inputs, causing field workers to see wrong UI for select questions.

**Files to Read:**
- `app/src/commonMain/kotlin/org/commcare/app/ui/FormEntryScreen.kt` (lines 267-487 — QuestionWidget composable, appearance checks at 269-270)
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/FormEntryViewModel.kt` (line 413 — appearance hint retrieval)
- Android reference: `dimagi/commcare-android` widgets directory for behavior spec

**Tasks:**

1. **Implement `minimal` appearance for SELECT_ONE** — Render as a dropdown/spinner instead of radio buttons. Use Compose `DropdownMenu` or `ExposedDropdownMenuBox`.
   - File: `app/src/commonMain/kotlin/org/commcare/app/ui/FormEntryScreen.kt`

2. **Implement `compact` appearance for SELECT_ONE and SELECT_MULTI** — Render choices in a grid layout (2-4 columns based on `compact-N` suffix). Use `LazyVerticalGrid` or `FlowRow`.
   - File: `app/src/commonMain/kotlin/org/commcare/app/ui/FormEntryScreen.kt`

3. **Implement `quick` appearance for SELECT_ONE** — Auto-advance to next question when a selection is made (no explicit "Next" button needed).
   - Files: `app/src/commonMain/kotlin/org/commcare/app/ui/FormEntryScreen.kt`, `FormEntryViewModel.kt`

4. **Implement `label` and `list-nolabel` appearances** — `label`: show only labels (no buttons). `list-nolabel`: show only buttons (no labels). Used for image-based selections.
   - File: `app/src/commonMain/kotlin/org/commcare/app/ui/FormEntryScreen.kt`

5. **Implement `combobox` appearance for SELECT_ONE** — Text input with filterable dropdown. Support `combobox`, `combobox multiword`, `combobox fuzzy` variants.
   - File: `app/src/commonMain/kotlin/org/commcare/app/ui/FormEntryScreen.kt`

6. **Implement collapsible groups** — Render groups with `group-border`, `collapse-open`, `collapse-closed` appearances as expandable/collapsible sections.
   - File: `app/src/commonMain/kotlin/org/commcare/app/ui/FormEntryScreen.kt`

7. **Implement DateTime combined widget** — Combined date + time picker for `dateTime` data type (currently only separate DATE and TIME supported).
   - File: `app/src/commonMain/kotlin/org/commcare/app/ui/FormEntryScreen.kt`

8. **Add appearance tests** — Oracle tests for each appearance variant. Create test forms with minimal, compact, quick, combobox, label, list-nolabel, collapsible groups.
   - Files: New test forms in `app/src/jvmTest/resources/`, new oracle test files in `app/src/jvmTest/`

**Acceptance:**
- [ ] `minimal` SELECT_ONE renders as dropdown
- [ ] `compact-2`, `compact-3` render as 2-column, 3-column grids
- [ ] `quick` auto-advances on selection
- [ ] `label` shows only label text, `list-nolabel` shows only selection controls
- [ ] `combobox` shows filterable text input with dropdown
- [ ] Collapsible groups expand/collapse on tap
- [ ] DateTime widget shows combined date+time picker
- [ ] Oracle tests pass for all variants
- [ ] `./gradlew :app:jvmTest` passes

---

### Wave 3: Form Workflow Completeness

**Rationale:** After form completion, Android routes to configurable destinations (next form, case list, specific module). iOS hardcodes `RETURN_TO_MENU`. This breaks chained form workflows — a common pattern in production CommCare apps. Inline multimedia in question labels is also missing, affecting forms with audio prompts for low-literacy users.

**Files to Read:**
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/FormEntryViewModel.kt` (lines 360-362 — `getPostCompletionDestination()` stub, lines 487-491 — `PostFormDestination` enum)
- `app/src/commonMain/kotlin/org/commcare/app/ui/HomeScreen.kt` (lines 308-347 — form completion handler)
- `app/src/commonMain/kotlin/org/commcare/app/ui/FormEntryScreen.kt` (lines 252-255 — label rendering)
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/FormQueueViewModel.kt` — form queue for auto-send
- `commcare-core/src/commonMain/kotlin/org/commcare/session/` — session stack navigation

**Tasks:**

1. **Implement form end navigation** — Parse `post_form_workflow` from suite.xml entry config. Map to `PostFormDestination` enum values. Wire into HomeScreen's `onComplete` callback to navigate to the correct destination.
   - Files: `FormEntryViewModel.kt` (parse destination), `HomeScreen.kt` (route based on destination)

2. **Implement inline multimedia in question labels** — Parse `itext` media references (image/audio) from question label text. Render images inline within the label composable. Add audio play buttons for audio references.
   - Files: `FormEntryScreen.kt` (new `MediaLabel` composable), `FormEntryViewModel.kt` (expose media references from `FormEntryPrompt`)

3. **Implement auto-send for queued forms** — When connectivity is restored during app usage, automatically attempt to submit queued forms. Use existing `FormQueueViewModel.submitAll()` triggered by connectivity state change.
   - Files: `FormQueueViewModel.kt` (add connectivity observer), `SyncViewModel.kt` (hook auto-send into sync lifecycle)

4. **Add workflow tests** — Test form chaining (A→B→C), post-form destination routing, auto-send on connectivity change.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/e2e/FormWorkflowTest.kt` (new)

**Acceptance:**
- [ ] `getPostCompletionDestination()` returns correct destination based on suite config
- [ ] After form completion, navigation routes to case list / menu / next form as configured
- [ ] Question labels with `<output>` image references display inline images
- [ ] Question labels with audio references show play button
- [ ] Queued forms auto-submit when connectivity is detected
- [ ] `./gradlew :app:jvmTest` passes

---

### Wave 4: Sync & Update Hardening

**Rationale:** Currently, every sync does a full restore parse regardless of whether data has changed (412 response is handled but partial sync is not). With large case loads (1,000+ cases), this is slow. Staged upgrade with rollback protects against broken app updates.

**Files to Read:**
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/SyncViewModel.kt` (lines 57-132 — sync flow, line 105 — full parse)
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/UpdateViewModel.kt` (lines 119-141 — install, lines 148-160 — periodic check)
- `commcare-core/src/commonMain/kotlin/org/commcare/resources/` — ResourceTable, ResourceManager if they exist

**Tasks:**

1. **Implement incremental sync with hash comparison** — After restore response, compute hash of response body. Store hash alongside sync token. On next sync, compare response hash — if identical, skip full re-parse. This leverages the existing 412 "no new data" response but adds client-side hash verification for cases where server returns 200 with unchanged data.
   - Files: `SyncViewModel.kt`, `SqlDelightUserSandbox.kt` (persist hash)

2. **Implement staged upgrade with rollback** — Before installing an update, snapshot the current app state (profile version, resource versions). Download new resources to a staging area. If install succeeds, promote. If it fails, restore snapshot. Report error to user.
   - Files: `UpdateViewModel.kt`, potentially new `StagedUpdateManager.kt`

3. **Add sync and update tests** — Test incremental sync skip, hash mismatch triggers full parse, upgrade success path, upgrade rollback on failure.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/SyncUpdateTest.kt` (new)

**Acceptance:**
- [ ] Second sync with unchanged data skips full re-parse (verified by test)
- [ ] Sync with changed data does full re-parse
- [ ] Failed app update restores previous version
- [ ] Successful app update promotes new version
- [ ] `./gradlew :app:jvmTest` passes

---

### Wave 5: Case Management Completeness

**Rationale:** Android CommCare supports tabbed case detail views (properties grouped by category with conditional visibility) and image rendering in case list tiles. iOS shows a flat property list and placeholder text for images.

**Files to Read:**
- `app/src/commonMain/kotlin/org/commcare/app/ui/CaseDetailScreen.kt` (lines 23-77 — flat detail view)
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/CaseDetailViewModel.kt` (lines 11-49 — detail row building)
- `app/src/commonMain/kotlin/org/commcare/app/ui/CaseTileRow.kt` (line 114-115 — image placeholder)
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/CaseListViewModel.kt` (line 116 — isImage flag)
- `commcare-core/src/commonMain/kotlin/org/commcare/suite/model/Detail.kt` — detail configuration with tabs

**Tasks:**

1. **Implement case detail tabs** — Parse tab configuration from `Detail` (suite model). Group detail fields by tab. Render using Compose `TabRow` + `HorizontalPager`. Support conditional tab visibility via XPath display conditions.
   - Files: `CaseDetailScreen.kt`, `CaseDetailViewModel.kt`

2. **Implement case list image loading** — Load and display images referenced in case tile fields. Use Compose image loading (platform-appropriate: `coil` on JVM, native on iOS via expect/actual). Handle missing images gracefully with placeholder.
   - Files: `CaseTileRow.kt`, potentially new `PlatformImageLoader.kt` expect/actual

3. **Add case detail and image tests** — Test tab rendering with multiple tabs, conditional tab visibility, image field rendering.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/CaseDetailTabsTest.kt` (new)

**Acceptance:**
- [ ] Case detail screen shows tabs when configured in suite
- [ ] Tab switching displays correct fields per tab
- [ ] Conditional tabs hidden when display condition is false
- [ ] Case list tiles display images from case data
- [ ] Missing images show placeholder gracefully
- [ ] `./gradlew :app:jvmTest` passes

---

### Wave 6: Multimedia Capture

**Rationale:** Android supports video capture, document upload, and map-based geopoint selection. iOS is missing all three. These follow the established `expect`/`actual` platform pattern used for audio, image, signature, and barcode capture.

**Files to Read:**
- `app/src/commonMain/kotlin/org/commcare/app/platform/PlatformImageCapture.kt` (expect declaration — pattern to follow)
- `app/src/iosMain/kotlin/org/commcare/app/platform/PlatformAudioCapture.kt` (iOS actual — AVFoundation pattern)
- `app/src/jvmMain/kotlin/org/commcare/app/platform/` (JVM stubs pattern)
- `app/src/commonMain/kotlin/org/commcare/app/ui/FormEntryScreen.kt` (where to add new widget types)

**Tasks:**

1. **Implement video capture widget** — Add `PlatformVideoCapture` expect/actual. iOS: use `UIImagePickerController` with `.camera` source and `.movie` media type. Save to temp file, return path. JVM: stub returning null. Add VIDEO case to FormEntryScreen's question widget renderer.
   - Files: New `PlatformVideoCapture.kt` in commonMain/iosMain/jvmMain, `FormEntryScreen.kt`

2. **Implement document upload widget** — Add `PlatformDocumentPicker` expect/actual. iOS: use `UIDocumentPickerViewController` with supported UTTypes (PDF, Word, Excel, plain text). Return file path. JVM: stub. Add UPLOAD case to FormEntryScreen.
   - Files: New `PlatformDocumentPicker.kt` in commonMain/iosMain/jvmMain, `FormEntryScreen.kt`

3. **Implement map-based geopoint display** — Add `PlatformMapView` expect/actual. iOS: use MapKit `MKMapView` for point selection. JVM: stub with text coordinates. Add map option to geopoint widget alongside existing "Capture GPS" button.
   - Files: New `PlatformMapView.kt` in commonMain/iosMain/jvmMain, `FormEntryScreen.kt`

4. **Add multimedia tests** — Verify widget rendering for video, document, and map question types. Test file path handling.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/e2e/MultimediaCaptureTest.kt` (new)

**Acceptance:**
- [ ] Video capture widget launches camera on iOS, returns video file path
- [ ] Document picker shows file browser on iOS, returns selected file path
- [ ] Geopoint widget shows map option for point selection on iOS
- [ ] All three have JVM stubs that return null/empty gracefully
- [ ] FormEntryScreen renders all three widget types
- [ ] `./gradlew :app:jvmTest` passes
- [ ] `compileKotlinIosSimulatorArm64` passes

---

### Wave 7: Connect Messaging Refinement

**Rationale:** Android's Connect messaging uses per-channel consent (subscribe/unsubscribe individually), while iOS only has global consent. This is a small but important UX difference for field workers who want to manage notification preferences.

**Files to Read:**
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/MessagingViewModel.kt` (lines 233-261 — consent logic)
- `app/src/commonMain/kotlin/org/commcare/app/network/ConnectMarketplaceApi.kt` — consent endpoint
- `app/src/commonMain/kotlin/org/commcare/app/ui/connect/MessagingScreen.kt` — thread list UI

**Tasks:**

1. **Implement per-channel consent** — Add consent toggle per channel/thread in the messaging list. Call `updateConsent(channelId)` instead of global `updateConsent()`. Update MessagingViewModel to track per-channel consent state.
   - Files: `MessagingViewModel.kt`, `ConnectMarketplaceApi.kt`, `MessagingScreen.kt`

2. **Add consent management tests** — Test per-channel subscribe/unsubscribe, verify correct API calls per channel.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/network/ConnectMessagingConsentTest.kt` (new)

**Acceptance:**
- [ ] Each channel shows subscribe/unsubscribe toggle
- [ ] Toggling consent sends API call for that specific channel
- [ ] Channel consent state persists across app restarts
- [ ] `./gradlew :app:jvmTest` passes

---

### Wave 8: Test Coverage — Form Entry & Navigation

**Rationale:** Seven existing features have no test coverage. These are the form entry and navigation features most likely to regress.

**Files to Read:**
- `app/src/commonMain/kotlin/org/commcare/app/ui/FormEntryScreen.kt` (swipe gestures, field-list rendering)
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/FormEntryViewModel.kt` (draft save, repeat groups)
- `app/src/commonMain/kotlin/org/commcare/app/ui/GridMenuScreen.kt` (grid menu)
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/LanguageViewModel.kt` (language switching)
- `app/src/commonMain/kotlin/org/commcare/app/ui/CalendarWidget.kt` (alternative calendars)

**Tasks:**

1. **Swipe navigation test** — Verify swipe left triggers `nextQuestion()`, swipe right triggers `previousQuestion()`.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/FormNavigationTest.kt` (new)

2. **Form draft save/resume test** — Save a partially completed form, create new ViewModel, load the draft, verify answers are preserved.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/e2e/FormDraftTest.kt` (new)

3. **Repeat group add/delete test** — Add 3 repeat instances, delete the 2nd, verify remaining instances have correct data and indices.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/RepeatGroupTest.kt` (new)

4. **Field-list group test** — Load form with field-list group, verify `getQuestions()` returns multiple prompts for the group.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/FieldListGroupTest.kt` (new)

5. **Grid menu display test** — Verify grid menu renders with correct column count from configuration.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/GridMenuTest.kt` (new)

6. **Language switching mid-form test** — Switch language during form entry, verify question text updates to new locale.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/LanguageSwitchTest.kt` (new)

7. **Ethiopian/Nepali calendar test** — Verify month names, date formatting, and calendar detection for both alternative calendar systems.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/ui/CalendarWidgetTest.kt` (new)

**Acceptance:**
- [ ] All 7 tests written and passing
- [ ] Tests cover both happy path and edge cases
- [ ] `./gradlew :app:jvmTest` passes

---

### Wave 9: Test Coverage — Auth & Multi-App

**Rationale:** PIN login, biometric auth, multi-app switching, demo mode, tiered case selection, and form chaining are all implemented but untested. These are core user flows.

**Files to Read:**
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/UserKeyRecordManager.kt` (PIN flow)
- `app/src/commonMain/kotlin/org/commcare/app/platform/PlatformBiometricAuth.kt` (biometric)
- `app/src/commonMain/kotlin/org/commcare/app/storage/AppRecordRepository.kt` (multi-app)
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/DrawerViewModel.kt` (app switching)
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/DemoModeManager.kt` (demo mode)
- `app/src/commonMain/kotlin/org/commcare/app/ui/HomeScreen.kt` (tiered case selection, form chaining)

**Tasks:**

1. **PIN login flow test** — Create PIN, verify PIN matches, verify wrong PIN rejects, verify PIN unlocks credentials.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/PinLoginTest.kt` (new)

2. **Biometric auth flow test** — Verify biometric availability check, verify fallback to PIN when unavailable.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/BiometricAuthTest.kt` (new)

3. **Multi-app switching test** — Seat app A, verify sandbox is A's. Switch to app B, verify sandbox switches. Verify drawer shows both apps with correct highlighting.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/MultiAppSwitchTest.kt` (new)

4. **Demo mode blocking test** — Enter demo mode, verify sync is blocked, verify form submission is blocked, verify demo data is isolated.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/DemoModeBlockingTest.kt` (new)

5. **Tiered case selection test** — Navigate parent case list → select parent → child case list appears → select child → form entry with correct case context.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/e2e/TieredCaseSelectionTest.kt` (new)

6. **Form chaining via session stack test** — Complete form A, verify session stack pops to form B, complete form B, verify return to landing.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/e2e/FormChainingTest.kt` (new)

**Acceptance:**
- [ ] All 6 tests written and passing
- [ ] PIN test covers create, match, reject, unlock
- [ ] Demo mode test verifies isolation
- [ ] Multi-app test verifies complete sandbox switch
- [ ] `./gradlew :app:jvmTest` passes

---

### Wave 10: Test Coverage — Connect & Infrastructure

**Rationale:** Nine infrastructure features lack tests: Connect SSO, heartbeat, auto-update, offline sync, messaging polling, diagnostics, recovery actions, form quarantine, and remote case search. These are the remaining test gaps.

**Files to Read:**
- `app/src/commonMain/kotlin/org/commcare/app/network/ConnectIdTokenManager.kt` (SSO)
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/HeartbeatManager.kt` (heartbeat)
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/UpdateViewModel.kt` (auto-update)
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/SyncViewModel.kt` (offline behavior)
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/MessagingViewModel.kt` (polling)
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/DiagnosticsViewModel.kt` (diagnostics)
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/RecoveryViewModel.kt` (recovery)
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/CaseSearchViewModel.kt` (remote search)
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/FormQueueViewModel.kt` (quarantine)

**Tasks:**

1. **Connect SSO auto-login test** — Verify ConnectID token → HQ SSO token exchange, verify auto-login after Connect app install.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/network/ConnectSsoTest.kt` (new)

2. **Heartbeat manager test** — Verify heartbeat sends correct device info, parses force_update response, triggers update prompt.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/HeartbeatManagerTest.kt` (new)

3. **Auto-update checking test** — Verify periodic check scheduling, version comparison logic, update prompt trigger.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/AutoUpdateTest.kt` (new)

4. **Offline sync behavior test** — Attempt sync without connectivity, verify appropriate error state and message.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/OfflineSyncTest.kt` (new)

5. **Connect messaging polling test** — Start polling, inject new message via mock, verify UI state updates.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/MessagingPollingTest.kt` (new)

6. **Diagnostics checks test** — Verify server ping, auth validation, and result reporting.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/DiagnosticsTest.kt` (new)

7. **Recovery screen actions test** — Test retry unsent forms, clear data flow, form export.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/RecoveryActionsTest.kt` (new)

8. **Form quarantine test** — Verify corrupted forms are isolated from send queue, not retried.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/FormQuarantineTest.kt` (new)

9. **Remote case search test** — Verify remote query request, response parsing, result display, case selection from remote results.
   - File: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/RemoteCaseSearchTest.kt` (new)

**Acceptance:**
- [ ] All 9 tests written and passing
- [ ] SSO test verifies token exchange chain
- [ ] Heartbeat test verifies force_update detection
- [ ] Offline sync test verifies graceful error handling
- [ ] `./gradlew :app:jvmTest` passes
- [ ] Total test count ≥ 1,250

---

## Dependency Graph

```
Wave 1 (AES Encryption) — no dependencies, start first
   ↓
Wave 2 (Select Appearances) — independent of Wave 1
Wave 3 (Form Workflow) — independent of Wave 1
   ↓
Wave 4 (Sync & Update) — independent
Wave 5 (Case Management) — independent
   ↓
Wave 6 (Multimedia Capture) — independent
Wave 7 (Connect Messaging) — independent
   ↓
Wave 8 (Tests: Form Entry) — after Waves 2-3 (tests may cover new features)
Wave 9 (Tests: Auth & Multi-App) — after Wave 1 (AES changes PIN flow)
Wave 10 (Tests: Connect & Infra) — after Wave 7 (tests may cover new consent)
```

Waves 1-7 can largely run in parallel (no cross-dependencies). Waves 8-10 should follow the feature waves they test.

## Risk Mitigations

| Risk | Mitigation |
|------|------------|
| AES migration breaks existing credentials | Migration path: detect XOR format, re-encrypt on first access. Rollback plan: keep XOR decrypt as fallback reader. |
| Select appearance count explosion | Start with most common (minimal, compact, quick). Add combobox and label variants incrementally. |
| Incremental sync hash collisions | Use SHA-256 for response hashing. False negatives (hash mismatch → full parse) are safe; false positives (hash match → skip) are astronomically unlikely with SHA-256. |
| iOS-specific multimedia APIs brittle | Follow established pattern from PlatformAudioCapture (working). Test on simulator first. |
| Form chaining breaks existing navigation | Gate behind feature detection: only route via `PostFormDestination` if suite config specifies `post_form_workflow`. Default remains `RETURN_TO_MENU`. |
