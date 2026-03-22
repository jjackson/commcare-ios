# Phase 6: Field Readiness

**Date:** 2026-03-21
**Status:** Plan
**Prerequisite:** App on TestFlight (Phase 4 complete), Android UX Parity (Phase 5 complete), code review fixes merged (PRs #329-#330)

## Overview

The app is on TestFlight but has functional gaps that would block field workers. This phase fixes production blockers, adds missing Connect workflows, hardens platform code with tests, and closes the remaining feature gap with Android.

### Goals

- Fix all functional blockers discovered during code review and TestFlight audit
- Implement the full Connect ID lifecycle (including recovery)
- Align Connect marketplace data models with actual server API responses
- Add iOS platform unit tests to prevent regression
- Implement real update checking

### Exit Criteria

- Signature capture works end-to-end on device
- Update checking compares versions against server
- Connect ID recovery flow handles existing users on new devices
- Connect marketplace parses real server responses correctly
- All iOS platform implementations have direct unit tests
- Form draft save/resume verified with E2E test

## Waves

### Wave 1: Functional Blockers (signature capture + update checking)

**Rationale:** These are the two features that are completely non-functional. Signature capture affects any form with a signature question type. Update checking is required for post-launch bug fix distribution.

**Tasks:**

1. **Implement touch drawing in PlatformSignatureCapture** — Add `touchesBegan`/`touchesMoved`/`touchesEnded` handlers to the UIView subclass. Draw strokes with `UIBezierPath` or `CGContext`. Capture the drawn image as PNG on "Done".
   - File: `app/src/iosMain/kotlin/org/commcare/app/platform/PlatformSignatureCapture.kt`
   - Test: Manual — draw signature on simulator, verify PNG is saved

2. **Implement real update checking in UpdateViewModel** — Compare installed app profile version against server's latest version. Use `ResourceTable` staging pattern (download profile to staging, compare versions).
   - File: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/UpdateViewModel.kt`
   - Test: JVM unit test with mock HTTP returning newer/same/older version

**Acceptance:**
- [ ] Signature canvas responds to touch, draws strokes, saves PNG
- [ ] Update check correctly identifies when server has newer version
- [ ] `./gradlew :app:jvmTest` passes
- [ ] `./gradlew :app:compileKotlinIosSimulatorArm64` passes

---

### Wave 2: Connect ID Recovery Flow

**Rationale:** Users who already have a Connect ID (registered on Android or another device) cannot sign in on iOS. The `check_name` API returns `account_exists: true` but the app doesn't branch to recovery mode.

**Tasks:**

1. **Add recovery flow branching in ConnectIdViewModel** — After `checkName()` returns `account_exists: true`, skip to backup code entry instead of continuing new registration. Handle `confirmBackupCodeRecovery` API response to get credentials.
   - Files: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/ConnectIdViewModel.kt`, relevant `*Step.kt` UI files
   - Reference: `docs/learnings/2026-03-19-connect-id-recovery-flow-gap.md`

2. **Add tests for recovery flow** — Test state transitions for both new-user and existing-user paths.
   - File: New test in `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/ConnectIdViewModelTest.kt`

**Acceptance:**
- [ ] Existing user entering their name sees "Welcome back" → backup code entry
- [ ] New user proceeds through full registration
- [ ] Both paths end with valid credentials stored in Keychain
- [ ] JVM tests cover both branches

---

### Wave 3: Connect Marketplace Data Model Rework

**Rationale:** Current `Opportunity` is a flat 15-field model. Real server responses have nested payment units, learn modules, assessments, deliveries with flags. See `docs/learnings/2026-03-20-connect-marketplace-depth-gap.md` and `docs/plans/2026-03-20-connect-marketplace-rework-plan.md`.

**Tasks:**

1. **Rewrite Opportunity data model** — Add nested types: `PaymentUnit`, `LearnModule`, `Assessment`, `DeliveryRecord`, `PaymentRecord`. Match actual API response fields.
   - Files: `app/src/commonMain/kotlin/org/commcare/app/model/Opportunity.kt` and related model files

2. **Rewrite ConnectMarketplaceApi response parsing** — Parse nested objects, arrays of payment units, delivery records with flags.
   - File: `app/src/commonMain/kotlin/org/commcare/app/network/ConnectMarketplaceApi.kt`

3. **Update opportunity screens** — Show payment unit breakdown, learning progress, delivery status per unit.
   - Files: `app/src/commonMain/kotlin/org/commcare/app/ui/connect/Opportunity*.kt`

4. **Add API version header** — `Accept: application/json; version=1.0` on all Connect API calls.

**Acceptance:**
- [ ] Opportunity list loads with all fields from real server
- [ ] Detail screen shows payment units, learn modules, delivery breakdown
- [ ] `ConnectMarketplaceApiJsonTest` updated to cover nested structures
- [ ] All existing Connect tests pass

---

### Wave 4: iOS Platform Unit Tests

**Rationale:** No `app/src/iosTest/` exists. iOS platform code (location, audio, barcode, image capture, scheduler, crash reporter) is untested directly. Regression risk is high — the code review found multiple silent-failure bugs in platform implementations.

**Tasks:**

1. **Create `app/src/iosTest/` directory** with test infrastructure

2. **Add tests for each platform implementation:**
   - `PlatformLocationProvider` — permission denied returns null, authorized triggers request
   - `PlatformImageCapture` — source unavailable returns null
   - `PlatformBarcodeScanner` — no camera returns null
   - `PlatformAudioCapture` — recorder init failure handled
   - `PlatformScheduler` — schedule/cancel lifecycle
   - `PlatformCrashReporter` — report encoding/decoding round-trip
   - `PlatformKeychainStore` — store/retrieve/delete round-trip via Keychain

3. **Add form draft E2E test** — Save incomplete form to SQLDelight, simulate app restart, resume form, verify data intact.
   - File: New test in `app/src/jvmTest/`

**Acceptance:**
- [ ] `./gradlew :app:iosSimulatorArm64Test` runs platform tests
- [ ] Form draft round-trip test passes on JVM
- [ ] Each platform implementation has at least one direct test

---

### Wave 5: Connect App Download + Learning Flow

**Rationale:** Connect opportunities require downloading separate CommCare apps (learn app, deliver app). Android handles this with SSO auto-login. This was deferred as a Phase 5 non-goal but is needed for the full Connect workflow.

**Tasks:**

1. **Implement app download from opportunity** — When user claims a job, download the learn/deliver app `.ccz` from the URL in the opportunity response. Install using existing `AppInstallViewModel`.
   - Files: `OpportunitiesViewModel.kt`, `OpportunityDetailScreen.kt`

2. **Implement SSO auto-login for Connect apps** — After installing a Connect app, auto-login using the HQ SSO token from `ConnectIdTokenManager.getHqSsoToken()`.

3. **Add learning module progress tracking** — Track which modules are complete, show progress bar, gate assessment on completion.

4. **Add assessment flow** — Launch assessment, record pass/fail, update opportunity status.

**Acceptance:**
- [ ] Tapping "Start Learning" downloads and installs the learn app
- [ ] Learn app auto-logs in with SSO credentials
- [ ] Learning progress shows completion percentage
- [ ] Assessment pass unlocks delivery phase

---

### Wave 6: Quality Polish

**Rationale:** Remaining quality improvements that aren't blocking but improve the production experience.

**Tasks:**

1. **Implement Connect message encryption** — AES-GCM per-channel encryption for messaging. Key exchange via Connect API.
   - Files: Connect messaging code

2. **Add offline sync indicator** — Detect network reachability. Show "Offline — data will sync when connected" instead of cryptic errors.
   - Files: `SyncViewModel.kt`, `HomeScreen.kt`

3. **Background update checking** — Periodic check (e.g., daily) with user notification when update available.
   - Files: `UpdateViewModel.kt`, `PlatformScheduler.kt`

4. **Timestamp fix** — `FormQueueViewModel.currentTimestamp()` returns literal `"now"`. Replace with actual epoch timestamp.
   - File: `FormQueueViewModel.kt`

**Acceptance:**
- [ ] Messages encrypted end-to-end
- [ ] Offline mode shows appropriate UI
- [ ] Background update check fires on schedule
- [ ] Form timestamps are real dates

## Dependency Graph

```
Wave 1 (blockers) ──→ Wave 2 (Connect ID recovery)
                  ──→ Wave 3 (marketplace models) ──→ Wave 5 (app download + learning)
                  ──→ Wave 4 (platform tests)
                  ──→ Wave 6 (quality polish)
```

Waves 2, 3, 4 can run in parallel after Wave 1. Wave 5 depends on Wave 3 (correct data models). Wave 6 can run anytime.

## Risk Mitigations

- **Connect API response shapes unknown**: Curl real endpoints with a test token before implementing parsers. Don't guess from spec.
- **iOS Keychain in tests**: Keychain APIs may not work in iOS simulator test environment. Use conditional test execution (`Assume.assumeTrue`).
- **Signature drawing on K/N**: UIBezierPath and CGContext are available via cinterop but may need `@OptIn(ExperimentalForeignApi::class)`. Test on real device — simulator touch events differ.
- **SSO token exchange**: HQ OAuth endpoint may reject tokens from new client apps. Test with real credentials against staging server.
