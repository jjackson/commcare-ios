# Phase 4: Polish — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Bring the feature-complete CommCare iOS app to App Store submission readiness — fill platform feature gaps, add accessibility, validate correctness at 99%+, and prepare for distribution.

**Architecture:** Phase 4 follows the same expect/actual pattern used throughout the project. New platform features (media capture, GPS, barcode) get `expect` declarations in `app/src/commonMain/` with `actual` iOS implementations in `app/src/iosMain/` via cinterop, and JVM stubs in `app/src/jvmMain/`. Oracle tests validate behavior on JVM; cross-platform tests verify iOS parity. The correctness scorecard aggregates all test results into a single pass/fail dashboard.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, iOS cinterop (AVFoundation, CoreLocation, UIKit), xcodegen, XCTest

**Prior art:** Read `docs/learnings/2026-03-12-phase8-wave1-cinterop-learnings.md` and `docs/learnings/2026-03-12-phase8-ios-app-learnings.md` before starting any wave — they document real cinterop pitfalls (staticCFunction capture, NSURLSession sync, dispatch_semaphore patterns).

---

## Dependency Graph

```
Wave 1 (Correctness Scorecard)
    ↓
Wave 2 (Media Capture)  ←  independent of Wave 3
Wave 3 (Location & Barcode)  ←  independent of Wave 2
    ↓
Wave 4 (Accessibility)  ←  after Waves 2-3 so new UI elements are covered
    ↓
Wave 5 (App Store Prep)  ←  after Wave 4 (accessibility must be done for review)
    ↓
Wave 6 (Final Validation)  ←  last — runs scorecard to confirm 99%+
```

## Estimated Scope

~19 tasks across 6 waves. Waves 2 and 3 can run in parallel.

---

## Wave 1: Correctness Scorecard & Integration Testing (3 tasks)

**Goal:** Build the 4-layer verification scorecard from the design doc and validate current test coverage.

### Task 1: Build correctness scorecard reporting

**Files:**
- Create: `app/src/jvmTest/kotlin/org/commcare/app/CorrectnessScorecard.kt`
- Read: `docs/plans/2026-03-07-commcare-ios-design.md` (sections 3.1-3.6 on verification strategy)
- Read: `app/src/jvmTest/kotlin/org/commcare/app/` (all existing test files)

**What to do:**
Create a test that runs all existing test suites and produces a scorecard summary:
```
Unit Tests:      XXX / XXX  (XX%)
Oracle Tests:    XXX / XXX  (XX%)
Cross-Platform:  XXX / XXX  (XX%)
E2E Tests:       XXX / XXX  (XX%)
Overall:         XX%
```

**Step 1:** Write a JUnit test class `CorrectnessScorecard` that programmatically discovers and counts test methods across all test classes in the `org.commcare.app` package.

**Step 2:** Run `cd commcare-core && ./gradlew test` and `cd app && ./gradlew jvmTest` — record pass/fail counts.

**Step 3:** Output scorecard to `build/reports/scorecard.txt` and print to stdout.

**Step 4:** Commit.

**Tests that must pass:** All existing tests continue to pass. Scorecard prints without error.

---

### Task 2: CommCare HQ integration test harness

**Files:**
- Create: `app/src/jvmTest/kotlin/org/commcare/app/integration/HqIntegrationTest.kt`
- Create: `app/src/jvmTest/kotlin/org/commcare/app/integration/HqTestConfig.kt`
- Read: `app/src/commonMain/kotlin/org/commcare/app/AppInstaller.kt`
- Read: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/SyncViewModel.kt`
- Read: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/LoginViewModel.kt`

**What to do:**
Create an integration test harness that can connect to a real CommCare HQ instance (when configured). The test should be `@Ignore`d by default (no HQ credentials in CI) but runnable locally with environment variables.

**Step 1:** Create `HqTestConfig` that reads `COMMCARE_HQ_URL`, `COMMCARE_USERNAME`, `COMMCARE_PASSWORD`, `COMMCARE_APP_ID` from environment variables.

**Step 2:** Create `HqIntegrationTest` with `@Ignore` annotation and these test methods:
- `testLogin()` — authenticate against HQ, verify session token returned
- `testAppInstall()` — download and install a test app
- `testFormSubmission()` — fill a simple form and submit to HQ
- `testSync()` — perform a sync cycle and verify case data returned

**Step 3:** Document in the test file how to run manually: set env vars, remove `@Ignore`, run `./gradlew jvmTest --tests "*HqIntegrationTest*"`.

**Step 4:** Commit.

**Tests that must pass:** Test class compiles. When `@Ignore`d, it's skipped in CI. When env vars are set and `@Ignore` removed, tests pass against a real HQ instance.

---

### Task 3: iOS simulator parity validation

**Files:**
- Create: `commcare-core/src/commonTest/kotlin/org/commcare/core/parity/SimulatorParityTest.kt`
- Read: `app/src/jvmTest/kotlin/org/commcare/app/OracleTestRunner.kt`
- Read: `app/src/jvmTest/kotlin/org/commcare/app/GoldenFileGenerator.kt`
- Read: `commcare-core/src/commonTest/kotlin/` (existing cross-platform tests)

**What to do:**
Add cross-platform tests that run the same form entry scenarios on both JVM and iOS and compare serialized XML output. This extends the golden file pattern to cover more form types.

**Step 1:** Create `SimulatorParityTest` in commonTest with 5 test cases covering: simple text form, date question, select question, repeat group, calculated field.

**Step 2:** Each test loads an XForm from test resources, fills it with predetermined answers, serializes to XML, and compares against a golden file.

**Step 3:** Generate golden files by running on JVM first: `cd commcare-core && ./gradlew test --tests "*SimulatorParityTest*"`.

**Step 4:** Verify golden files exist, then run iOS tests (CI will do this on macOS): `./gradlew iosSimulatorArm64Test --tests "*SimulatorParityTest*"`.

**Step 5:** Commit.

**Tests that must pass:** All 5 parity tests pass on both JVM and iOS simulator (CI validates).

---

## Wave 2: Media Capture Platform Abstractions (4 tasks)

**Goal:** Add camera, audio, and signature capture — the most-used CommCare field features missing from the iOS app.

**Read first:** `docs/learnings/2026-03-12-phase8-wave1-cinterop-learnings.md` (cinterop patterns)

### Task 4: PlatformImageCapture expect/actual

**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/platform/PlatformImageCapture.kt`
- Create: `app/src/iosMain/kotlin/org/commcare/app/platform/PlatformImageCapture.kt`
- Create: `app/src/jvmMain/kotlin/org/commcare/app/platform/PlatformImageCapture.kt`
- Create: `app/src/jvmTest/kotlin/org/commcare/app/platform/ImageCaptureOracleTest.kt`

**What to do:**
Create the platform abstraction for image capture (camera + photo library).

**Step 1: Define expect declaration (commonMain)**
```kotlin
expect class PlatformImageCapture {
    suspend fun capturePhoto(): ByteArray?
    suspend fun pickFromGallery(): ByteArray?
    fun isAvailable(): Boolean
}
```

**Step 2: JVM stub (jvmMain)**
Returns null/false — JVM is not a camera. Just enough to compile.

**Step 3: iOS actual (iosMain)**
Use `UIImagePickerController` via cinterop:
- `capturePhoto()`: Present camera picker, await delegate callback via `suspendCancellableCoroutine`
- `pickFromGallery()`: Present photo library picker
- `isAvailable()`: Check `UIImagePickerController.isSourceTypeAvailable(.camera)`
- Convert `UIImage` to JPEG `ByteArray` via `UIImageJPEGRepresentation`

**Step 4: Oracle test (jvmTest)**
Test that the expect class compiles and JVM stub returns expected defaults.

**Step 5:** Commit.

**Tests that must pass:** Compilation on all targets. Oracle test passes on JVM. iOS CI builds successfully.

---

### Task 5: PlatformAudioCapture expect/actual

**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/platform/PlatformAudioCapture.kt`
- Create: `app/src/iosMain/kotlin/org/commcare/app/platform/PlatformAudioCapture.kt`
- Create: `app/src/jvmMain/kotlin/org/commcare/app/platform/PlatformAudioCapture.kt`
- Create: `app/src/jvmTest/kotlin/org/commcare/app/platform/AudioCaptureOracleTest.kt`

**What to do:**
Create audio recording abstraction.

**Step 1: Define expect declaration (commonMain)**
```kotlin
expect class PlatformAudioCapture {
    suspend fun startRecording(): Boolean
    suspend fun stopRecording(): ByteArray?
    fun isRecording(): Boolean
    fun isAvailable(): Boolean
}
```

**Step 2: JVM stub** — returns false/null.

**Step 3: iOS actual** — use `AVAudioRecorder` via cinterop:
- Request microphone permission via `AVAudioSession`
- Record to temp file, read bytes on stop
- Return audio data as ByteArray

**Step 4: Oracle test** on JVM.

**Step 5:** Commit.

**Tests that must pass:** Compilation on all targets. Oracle test on JVM. iOS CI builds.

---

### Task 6: PlatformSignatureCapture expect/actual

**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/platform/PlatformSignatureCapture.kt`
- Create: `app/src/iosMain/kotlin/org/commcare/app/platform/PlatformSignatureCapture.kt`
- Create: `app/src/jvmMain/kotlin/org/commcare/app/platform/PlatformSignatureCapture.kt`
- Create: `app/src/jvmTest/kotlin/org/commcare/app/platform/SignatureCaptureOracleTest.kt`

**What to do:**
Create signature drawing capture abstraction.

**Step 1: Define expect declaration (commonMain)**
```kotlin
expect class PlatformSignatureCapture {
    suspend fun captureSignature(): ByteArray?  // Returns PNG image data
    fun isAvailable(): Boolean
}
```

**Step 2: JVM stub** — returns null/true (signature is always "available" since it's a drawing widget).

**Step 3: iOS actual** — use a custom `UIView` subclass with touch handling:
- Track touch points, draw on `CGContext`
- Render to PNG via `UIGraphicsImageRenderer`
- Present as modal view controller
- Return PNG ByteArray on completion

**Step 4: Oracle test** on JVM.

**Step 5:** Commit.

**Tests that must pass:** Compilation on all targets. Oracle test on JVM. iOS CI builds.

---

### Task 7: Wire media capture into FormEntryScreen

**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/FormEntryScreen.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/FormEntryViewModel.kt`
- Read: `app/src/jvmTest/kotlin/org/commcare/app/AllQuestionTypesOracleTest.kt`
- Create: `app/src/jvmTest/kotlin/org/commcare/app/MediaCaptureOracleTest.kt`

**What to do:**
Wire the three media capture abstractions into the form entry screen so image, audio, and signature question types trigger the appropriate platform capture.

**Step 1:** In `FormEntryViewModel`, add methods: `captureImage()`, `captureAudio()`, `captureSignature()` that delegate to the platform abstractions and store the result as a form attachment.

**Step 2:** In `FormEntryScreen`, add UI buttons for image/audio/signature question types that call the ViewModel methods.

**Step 3:** Create `MediaCaptureOracleTest` that loads a form with image/audio/signature questions and verifies the ViewModel correctly identifies which capture type is needed for each question.

**Step 4:** Commit.

**Tests that must pass:** `MediaCaptureOracleTest` passes. All existing oracle tests still pass. iOS CI builds.

---

## Wave 3: Location & Barcode Scanning (3 tasks)

**Goal:** Add GPS location capture and barcode/QR scanning — commonly used CommCare question types.

### Task 8: PlatformLocationProvider expect/actual

**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/platform/PlatformLocationProvider.kt`
- Create: `app/src/iosMain/kotlin/org/commcare/app/platform/PlatformLocationProvider.kt`
- Create: `app/src/jvmMain/kotlin/org/commcare/app/platform/PlatformLocationProvider.kt`
- Create: `app/src/jvmTest/kotlin/org/commcare/app/platform/LocationProviderOracleTest.kt`

**What to do:**

**Step 1: Define expect declaration (commonMain)**
```kotlin
data class GeoPoint(val latitude: Double, val longitude: Double, val altitude: Double, val accuracy: Float)

expect class PlatformLocationProvider {
    suspend fun getCurrentLocation(): GeoPoint?
    fun isAvailable(): Boolean
    suspend fun requestPermission(): Boolean
}
```

**Step 2: JVM stub** — returns null/false.

**Step 3: iOS actual** — use `CLLocationManager` via cinterop:
- `requestWhenInUseAuthorization()`
- `requestLocation()` with delegate callback via `suspendCancellableCoroutine`
- Extract lat/lng/alt/accuracy from `CLLocation`
- Requires `NSLocationWhenInUseUsageDescription` in Info.plist

**Step 4:** Add `NSLocationWhenInUseUsageDescription` to `app/iosApp/iosApp/Info.plist`.

**Step 5: Oracle test** on JVM.

**Step 6:** Commit.

**Tests that must pass:** Compilation on all targets. Oracle test on JVM. iOS CI builds. Info.plist has location usage description.

---

### Task 9: PlatformBarcodeScanner expect/actual

**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/platform/PlatformBarcodeScanner.kt`
- Create: `app/src/iosMain/kotlin/org/commcare/app/platform/PlatformBarcodeScanner.kt`
- Create: `app/src/jvmMain/kotlin/org/commcare/app/platform/PlatformBarcodeScanner.kt`
- Create: `app/src/jvmTest/kotlin/org/commcare/app/platform/BarcodeScannerOracleTest.kt`

**What to do:**

**Step 1: Define expect declaration (commonMain)**
```kotlin
expect class PlatformBarcodeScanner {
    suspend fun scan(): String?  // Returns scanned barcode/QR content
    fun isAvailable(): Boolean
}
```

**Step 2: JVM stub** — returns null/false.

**Step 3: iOS actual** — use `AVCaptureSession` with `AVCaptureMetadataOutput`:
- Configure camera session for barcode detection
- Support types: `.qr`, `.ean13`, `.ean8`, `.code128`, `.code39`
- Present as full-screen camera view
- Return scanned string on detection
- Requires `NSCameraUsageDescription` in Info.plist

**Step 4:** Ensure `NSCameraUsageDescription` is in Info.plist (may already exist from Task 4).

**Step 5: Oracle test** on JVM.

**Step 6:** Commit.

**Tests that must pass:** Compilation on all targets. Oracle test on JVM. iOS CI builds. Info.plist has camera usage description.

---

### Task 10: Wire location & barcode into FormEntryScreen

**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/FormEntryScreen.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/FormEntryViewModel.kt`
- Create: `app/src/jvmTest/kotlin/org/commcare/app/LocationBarcodeOracleTest.kt`

**What to do:**

**Step 1:** In `FormEntryViewModel`, add methods: `captureLocation()`, `scanBarcode()` that delegate to platform abstractions and store results as form answers.

**Step 2:** In `FormEntryScreen`, add UI for GPS question type (show lat/lng/accuracy, "Get Location" button) and barcode question type ("Scan" button with result display).

**Step 3:** Create `LocationBarcodeOracleTest` that loads a form with geo and barcode questions and verifies the ViewModel correctly identifies the question types.

**Step 4:** Commit.

**Tests that must pass:** `LocationBarcodeOracleTest` passes. All existing oracle tests still pass. iOS CI builds.

---

## Wave 4: Accessibility (3 tasks)

**Goal:** Meet Apple's App Store accessibility requirements — VoiceOver and Dynamic Type support.

### Task 11: VoiceOver semantic descriptions

**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/FormEntryScreen.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/CaseListScreen.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/MenuScreen.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/LoginScreen.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/HomeScreen.kt`
- Read: All other `*Screen.kt` files in `app/src/commonMain/kotlin/org/commcare/app/ui/`

**What to do:**
Add Compose Multiplatform accessibility semantics to all interactive UI elements.

**Step 1:** Audit all Screen composables. For every `Button`, `TextField`, `IconButton`, `Image`, clickable element, and list item, add `Modifier.semantics { contentDescription = "..." }` or `contentDescription` parameter.

**Step 2:** For form entry questions, use the question label text as the content description. For navigation elements, use descriptive labels ("Navigate to [module name]", "Submit form", "Go back").

**Step 3:** For images and icons, add meaningful descriptions or mark as decorative with `Modifier.semantics { invisibleToUser() }`.

**Step 4:** Add `Modifier.semantics { heading() }` to screen titles and section headers.

**Step 5:** Commit.

**Tests that must pass:** All existing tests pass. Manual verification: enable VoiceOver on iOS simulator, navigate through all major screens.

---

### Task 12: Dynamic Type support

**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/ui/theme/AppTypography.kt` (if not exists)
- Modify: All `*Screen.kt` files that use hardcoded font sizes
- Read: `app/src/commonMain/kotlin/org/commcare/app/ui/App.kt` (theme setup)

**What to do:**
Ensure all text scales with the system font size setting.

**Step 1:** Audit all `Text()` composables and `fontSize` parameters. Replace any hardcoded `sp` values with `MaterialTheme.typography.*` styles.

**Step 2:** Create `AppTypography.kt` with a theme that uses relative sizing (if the app doesn't already have one).

**Step 3:** Ensure no text is clipped by fixed-height containers. Replace any `height(X.dp)` on text containers with `minHeight(X.dp)` or remove height constraints.

**Step 4:** Test by changing text size in iOS Settings → Accessibility → Display & Text Size → Larger Text.

**Step 5:** Commit.

**Tests that must pass:** All existing tests pass. Visual verification: text scales proportionally at all Dynamic Type sizes without clipping.

---

### Task 13: Accessibility audit & fixes

**Files:**
- Modify: Various `*Screen.kt` and `*Widget.kt` files as needed
- Create: `docs/plans/2026-03-XX-accessibility-audit.md`

**What to do:**
Run a systematic accessibility audit and fix issues.

**Step 1:** Run Xcode's Accessibility Inspector on the iOS simulator app. Document all warnings and errors.

**Step 2:** Fix all "missing label" and "insufficient contrast" warnings.

**Step 3:** Verify touch targets are at least 44x44 points (Apple Human Interface Guidelines minimum). Fix any undersized buttons.

**Step 4:** Test tab order / focus order makes logical sense when swiping through with VoiceOver.

**Step 5:** Write audit results to `docs/plans/2026-03-XX-accessibility-audit.md` with pass/fail for each screen.

**Step 6:** Commit.

**Tests that must pass:** Accessibility Inspector reports zero critical warnings. All existing tests pass.

---

## Wave 5: App Store Preparation (3 tasks)

**Goal:** Prepare all metadata, assets, and configuration needed for App Store submission.

### Task 14: App icons and launch screen

**Files:**
- Create: `app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/` (icon files)
- Modify: `app/iosApp/iosApp/Info.plist`
- Modify: `app/iosApp/project.yml` (if needed for asset catalog)

**What to do:**

**Step 1:** Create a CommCare app icon at required sizes (1024x1024 for App Store, plus @2x/@3x for device). Use the CommCare brand (blue gradient with white "C" or CommCare logo).

**Step 2:** Create `Contents.json` for the asset catalog with all required sizes.

**Step 3:** Add a launch screen storyboard or SwiftUI launch screen with CommCare branding (logo + "CommCare" text on brand-colored background).

**Step 4:** Update `project.yml` to reference the asset catalog.

**Step 5:** Commit.

**Tests that must pass:** iOS CI builds successfully with new assets. App launches with correct icon on simulator.

---

### Task 15: Privacy & bundle configuration

**Files:**
- Modify: `app/iosApp/iosApp/Info.plist`
- Modify: `app/iosApp/project.yml`
- Create: `app/iosApp/PrivacyInfo.xcprivacy`

**What to do:**

**Step 1:** Set bundle ID to `org.commcare.ios`, display name to "CommCare", version to `1.0.0`, build number to `1`.

**Step 2:** Add all required `NSUsageDescription` keys to Info.plist:
- `NSCameraUsageDescription` — "CommCare needs camera access to capture photos and scan barcodes"
- `NSMicrophoneUsageDescription` — "CommCare needs microphone access to record audio responses"
- `NSLocationWhenInUseUsageDescription` — "CommCare needs location access to capture GPS coordinates"
- `NSFaceIDUsageDescription` — "CommCare uses Face ID for secure authentication"

**Step 3:** Create `PrivacyInfo.xcprivacy` with Apple's required privacy manifest:
- Declare data types collected: name, location, health data (form responses may contain these)
- Declare no tracking
- Declare data linked to user identity

**Step 4:** Configure required device capabilities in Info.plist.

**Step 5:** Commit.

**Tests that must pass:** iOS CI builds. Info.plist is valid. Privacy manifest validates.

---

### Task 16: TestFlight distribution setup

**Files:**
- Create: `docs/plans/2026-03-XX-testflight-setup.md`
- Modify: `app/iosApp/project.yml` (signing configuration)

**What to do:**

**Step 1:** Document the steps to set up TestFlight distribution:
- Apple Developer account setup
- App ID registration (org.commcare.ios)
- Provisioning profile creation
- Distribution certificate
- App Store Connect app record creation

**Step 2:** Configure code signing in `project.yml`:
- Development team ID
- Automatic signing for debug
- Manual signing placeholders for release

**Step 3:** Add a `Fastlane` or manual archive + upload process to `docs/plans/2026-03-XX-testflight-setup.md`.

**Step 4:** Commit.

**Tests that must pass:** Documentation is complete. `project.yml` builds with signing configured (may need team ID from developer).

---

## Wave 6: Performance & Final Validation (3 tasks)

**Goal:** Profile on real iOS device, fix bottlenecks, and achieve 99%+ on the correctness scorecard.

### Task 17: iOS device performance benchmarks

**Files:**
- Create: `docs/plans/2026-03-XX-ios-performance-benchmarks.md`
- Read: `docs/plans/2026-03-15-java-vs-kotlin-benchmark-comparison.md`

**What to do:**

**Step 1:** Profile app launch time on iOS simulator using Instruments (Time Profiler). Target: <3 seconds cold launch.

**Step 2:** Profile form loading time — load a complex form (50+ questions) and measure time to first question displayed. Target: <1 second.

**Step 3:** Profile case list rendering — load 1000+ cases and measure scroll performance. Target: 60fps smooth scrolling.

**Step 4:** Profile sync performance — sync with HQ and measure time. Compare with Android benchmarks if available.

**Step 5:** Document results in `docs/plans/2026-03-XX-ios-performance-benchmarks.md`.

**Step 6:** Fix any bottlenecks found (lazy loading, pagination, caching).

**Step 7:** Commit.

**Tests that must pass:** All existing tests pass. Benchmark document completed. No performance regressions.

---

### Task 18: Memory & launch time profiling

**Files:**
- Modify: Various files as needed for optimization
- Update: `docs/plans/2026-03-XX-ios-performance-benchmarks.md`

**What to do:**

**Step 1:** Profile memory usage with Instruments (Allocations). Check for leaks and excessive allocations during form entry and sync.

**Step 2:** Profile Kotlin/Native memory model — check for frozen object issues or excessive copying.

**Step 3:** Optimize any memory hotspots: add object pooling for frequently-allocated types, reduce unnecessary copying in serialization.

**Step 4:** Profile app size — check the final IPA size. Target: <50MB.

**Step 5:** Update benchmark document with memory and size findings.

**Step 6:** Commit.

**Tests that must pass:** All existing tests pass. No memory leaks detected. App size within target.

---

### Task 19: Final correctness scorecard & release validation

**Files:**
- Modify: `app/src/jvmTest/kotlin/org/commcare/app/CorrectnessScorecard.kt` (from Task 1)
- Create: `docs/plans/2026-03-16-phase4-completion-report.md`

**What to do:**

**Step 1:** Run the full correctness scorecard (Task 1). Target: 99%+ across all layers.

**Step 2:** If any tests fail, fix the underlying issues.

**Step 3:** Run the iOS CI pipeline end-to-end one final time. Confirm: framework builds, Xcode project generates, app builds, app launches, cross-platform tests pass.

**Step 4:** Write Phase 4 completion report covering:
- Scorecard results (all 4 layers)
- Performance benchmark summary
- Accessibility audit summary
- Platform feature coverage
- Remaining gaps (Tier 4 items: push notifications, Simprints, maps)
- App Store submission readiness assessment

**Step 5:** Update CLAUDE.md with Phase 4 completion status.

**Step 6:** Commit.

**Tests that must pass:** Correctness scorecard at 99%+. All CI green. Completion report written.

---

## Exit Criteria (from original design doc)

- [ ] Correctness scorecard at 99%+
- [ ] All oracle tests pass on both JVM and iOS
- [ ] Accessibility audit passes (VoiceOver + Dynamic Type)
- [ ] App icons, launch screen, and branding in place
- [ ] Privacy manifest and usage descriptions configured
- [ ] Performance benchmarks documented (no regressions)
- [ ] App builds and launches on iOS simulator via CI
- [ ] TestFlight distribution process documented
- [ ] Phase 4 completion report written

## Known Deferrals (Tier 4 / Future)

These are explicitly out of scope for Phase 4 (per original design doc, Tier 4 is "optional and separable"):
- Push notifications (APNs integration)
- Simprints biometric integration
- Mapbox map integration
- Intent/app callout equivalents
- CommCare Sense Mode (low-literacy)
- Video capture and playback
- Multimedia prompts (audio/image inline with questions)

---

## Issue Template

Each wave should have a GitHub issue following this template:

```markdown
## Phase 4 Wave N: [Title]

### Files to Read
- [List of files to read before starting]

### What to Do
- [Numbered list of tasks]

### Tests That Must Pass
- [ ] [Specific test assertions]
- [ ] All existing tests continue to pass
- [ ] iOS CI builds successfully

### Dependencies
- Depends on: [Wave X] (if any)
- Blocks: [Wave Y] (if any)
```
