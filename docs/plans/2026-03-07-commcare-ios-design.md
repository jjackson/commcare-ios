# CommCare iOS: Design Document

**Date**: 2026-03-07
**Author**: Jonathan Jackson (CEO, Dimagi) + Claude
**Status**: Approved

## 1. Vision

Build a complete iOS implementation of CommCare Mobile with full feature parity with Android, using an autonomous AI development pipeline that is repeatable, measurable, and improvable across runs.

### Goals
- Full CommCare feature parity on iOS (forms, cases, sync, offline, multimedia)
- Provably correct via automated verification (no human judgment required for correctness)
- Autonomous AI pipeline that humans can review but don't need to drive
- Pipeline designed as a first-class artifact: run, learn, improve, re-run
- Architecture that unifies Android and iOS codebases long-term

### Non-Goals (for now)
- Replacing the existing Android app (it continues unchanged)
- Changing FormPlayer or CommCare HQ
- Consumer-app-quality iOS-native polish (functional parity first)

## 2. Architecture

### 2.1 Kotlin Multiplatform + Compose Multiplatform

A single Kotlin codebase produces both the Android and iOS apps:

```
commcare-mobile (single Kotlin codebase)
├── engine/          commcare-core (ported from Java)
├── viewmodels/      Shared UI state and logic (pure Kotlin)
├── ui/              Compose Multiplatform (renders on both platforms)
└── platform/        expect/actual for storage, network, files, etc.

Compilation targets:
├── Android APK
├── iOS IPA
└── JVM .jar (engine only, for FormPlayer)
```

### 2.2 Three-Layer Architecture

```
┌──────────────────────────────────────────────┐
│  Layer 1: Engine (KMP, shared)               │
│  commcare-core ported to Kotlin              │
│  XForms, XPath, cases, sessions, sync, etc.  │
├──────────────────────────────────────────────┤
│  Layer 2: ViewModels (KMP, shared)           │
│  UI state, navigation logic, data binding    │
│  Pure Kotlin, no rendering framework deps    │
│  (enables future SwiftUI UI if desired)      │
├──────────────────────────────────────────────┤
│  Layer 3: UI (Compose Multiplatform)         │
│  Renders on Android and iOS                  │
│  Swappable: could add SwiftUI alternative    │
└──────────────────────────────────────────────┘
```

### 2.3 Platform-Specific Implementations (expect/actual)

| Concern | iOS Implementation | Android Implementation |
|---------|-------------------|----------------------|
| Storage | SQLite via native driver | SQLite via Android SDK |
| Networking | URLSession | OkHttp |
| File System | iOS file manager, app sandbox | Android file providers |
| Encryption | iOS Keychain + CommonCrypto | Android Keystore + AES |
| Camera/Media | AVFoundation, PhotosUI | Camera/MediaStore |
| GPS | CoreLocation | LocationManager |
| Push Notifications | APNs + FCM | FCM |
| Background Tasks | BGTaskScheduler | WorkManager |
| Biometrics | Face ID / Touch ID | BiometricPrompt |

### 2.4 Impact on Existing Systems

- **commcare-core**: Evolves from Java to Kotlin. JVM .jar output is backward-compatible. FormPlayer and existing Android continue to work unchanged.
- **commcare-android**: No changes required initially. Could eventually adopt Compose Multiplatform UI from this project.
- **FormPlayer**: No changes. Continues to consume the JVM .jar from commcare-core.
- **CommCare HQ**: No changes.

## 3. Verification Strategy

Four layers of automated verification ensure correctness without human judgment:

### 3.1 Layer 1: Unit Tests (Engine Correctness)

- commcare-core's existing 1,000+ unit tests, ported to Kotlin alongside the code
- Must maintain 100% pass rate throughout the port
- Run on every PR via CI (Linux runners, free for public repos)

### 3.2 Layer 2: Oracle Tests (FormPlayer Comparison)

FormPlayer is a trusted reference implementation. A test harness:
1. Drives FormPlayer with real CommCare test apps (50-100 apps from existing test suites)
2. Records all inputs and outputs as golden fixtures
3. Runs the same inputs through the KMP engine
4. Compares outputs — any difference is a test failure

**Existing test apps available**:
- commcare-core: 20+ mini test apps, 162+ XForm fixtures, form entry record/replay system
- commcare-android: 25+ .ccz test bundles including ccqa.ccz (comprehensive QA app)
- formplayer: 35+ test archives, MockMvc test framework driving form entry via REST

### 3.3 Layer 3: Android Parity Tests (Side-by-Side)

For critical paths, run identical scenarios on both Android and iOS:
- Install same app, sync same data, fill same forms
- Compare: submitted XML, case transactions, sync state hash
- Targeted at: form submission, case operations, sync protocol

### 3.4 Layer 4: E2E Integration Tests

Full workflow tests on the actual iOS app:
- Install real CommCare app, login, navigate, fill forms, submit, sync
- Verify data appears correctly in CommCare HQ
- XCTest/XCUITest automation on iOS simulator

### 3.5 Behavior Specification Catalog

A structured catalog documents known behavioral differences between Web Apps (FormPlayer) and mobile (Android/iOS):

```yaml
- behavior: "auto_advance_single_select"
  oracle: android          # Android is the reference, not FormPlayer
  category: mobile_specific
  known_difference: "Mobile auto-advances after single select; web shows Next button"
  test_mode: android_parity

- behavior: "xpath_evaluation"
  oracle: formplayer        # FormPlayer and mobile should match
  category: engine
  test_mode: output_match
```

For each behavior:
- Which oracle to use (FormPlayer or Android)
- Whether differences are expected and why
- How to test (exact match, parity, or spec conformance)

### 3.6 Correctness Scorecard

Every CI run produces a scorecard:
```
Unit Tests:      1,247 / 1,247  (100%)
Oracle Tests:      892 / 1,000  ( 89%)
Parity Tests:       34 /    40  ( 85%)
E2E Tests:          12 /    15  ( 80%)
Overall Parity:  89% | Delta: +4%
```

## 4. Autonomous AI Pipeline

### 4.1 Execution Model

The pipeline runs locally on the developer's machine using Claude Code with a MAX subscription:

```
Local Machine (Claude Code MAX)
├── Orchestrator script
│   ├── Reads task queue (GitHub Issues)
│   ├── Spawns 3-5 parallel Claude Code sessions
│   ├── Each session works in isolated git worktree
│   └── Monitors completion, failures, metrics
│
├── Git worktrees (isolated copies)
│   ├── .claude/worktrees/task-a/
│   ├── .claude/worktrees/task-b/
│   └── .claude/worktrees/task-c/
│
└── GitHub Actions CI
    ├── Runs verification gates on every PR
    ├── Auto-merges if all gates pass
    └── Reports results to correctness scorecard
```

### 4.2 Task Queue (GitHub Issues)

Each task is a GitHub Issue with structured content:
- Description of what to implement
- Files to read for context
- Tests that must pass (explicit acceptance criteria)
- Dependencies (which issues must merge first)
- Labels: phase, wave, risk level

### 4.3 Parallelism & Conflict Prevention

Tasks declare which files they modify. The orchestrator enforces:
1. Never schedule two tasks modifying the same files simultaneously
2. Tasks only start after dependencies have merged
3. Each agent rebases on main before creating PR

Work is organized in waves — each wave contains independent tasks that can run in parallel. A new wave starts when the previous wave's PRs have merged.

### 4.4 Human Touchpoints

Humans CAN review at any point but are only REQUIRED at:
- Phase transitions (approve Phase 0 → 1, etc.)
- Architecture decisions flagged as ambiguous
- PRs that fail verification and the agent can't self-fix after 2 attempts

Everything else flows: task → implement → test → merge.

### 4.5 Pipeline as Code

The entire pipeline is versioned:
- Task templates (how tasks are structured)
- Orchestrator workflow (how tasks are assigned and monitored)
- Verification gates (what must pass)
- Behavior catalog (known differences)

Each run produces a run report: what worked, what failed, where humans intervened, and why. This feeds into improving the next run.

## 5. Phasing

### Phase 0: Scaffold (~15-20 tasks, 1-2 weeks)

Build pipeline infrastructure before any product code:
- KMP project structure in commcare-core (JVM + iOS targets)
- Compose Multiplatform project structure
- CI pipeline (GitHub Actions: Linux for Kotlin, macOS for iOS)
- Oracle test harness (drives FormPlayer, records responses)
- Comparison framework (diffs engine output vs oracle)
- Task generator script (scans codebase, produces GitHub Issues)
- Orchestrator script (spawns parallel Claude Code sessions)
- Behavior catalog (documents known web/mobile differences)
- Metrics dashboard (aggregates test results into scorecard)

### Phase 1: Core Port (~40-50 tasks, 2-4 weeks)

Port commcare-core from Java to Kotlin, organized in dependency waves:

**Wave 1** (foundational, no dependencies):
- org.javarosa.core.util, org.javarosa.core.io
- org.javarosa.core.model.data (answer types)
- org.javarosa.core.services.storage (interfaces)
- org.javarosa.core.services.locale
- org.commcare.core.encryption

**Wave 2** (depends on Wave 1):
- org.javarosa.core.model (FormDef, bindings)
- org.javarosa.core.model.instance (TreeElement, etc.)
- org.javarosa.xpath (XPath engine)
- org.commcare.cases.model
- org.commcare.suite.model

**Wave 3** (depends on Wave 2):
- org.javarosa.xform.parse (XForm parser)
- org.javarosa.form.api (FormEntry API)
- org.commcare.session (session engine)
- org.commcare.xml (parsers)

**Wave 4** (depends on Wave 3):
- org.commcare.resources (resource management)
- org.commcare.core.parse (restore parsing)
- org.commcare.core.network

**Wave 5** (integration):
- KMP expect/actual stubs for platform-specific code
- Verify JVM backward compatibility (FormPlayer + Android still work)
- Verify iOS Native compilation

**Exit criteria**: 100% of existing commcare-core tests pass in Kotlin. KMP compiles for both JVM and iOS Native.

### Phase 2: App Shell (~10-15 tasks, 1 week)

Compose Multiplatform app that launches and integrates KMP engine:
- Project setup (Gradle, dependencies, signing)
- App lifecycle, navigation framework
- Theme and design system
- KMP engine integration (verify APIs callable)
- Platform adapters: SQLite, networking, file system, encryption (iOS)

**Exit criteria**: App launches on iOS simulator, loads KMP framework, can call core engine APIs.

### Phase 3: Feature Implementation (~55-85 tasks, 4-8 weeks)

Full feature parity, organized by feature group:

| Feature Group | ~Tasks | Key Capabilities |
|---------------|--------|-----------------|
| Auth | 3-5 | Login, token management, user switching, demo mode |
| App Install | 5-8 | Download, parse resources, install, update, offline install |
| Menu Navigation | 3-5 | Module menus, session management, breadcrumbs |
| Form Entry | 15-25 | All question types, groups, repeats, skip logic, calculations, constraints, field-list, multimedia questions |
| Case Management | 5-8 | Case lists, case details, case selection, case search, tiered selection, case tiles |
| Sync | 5-8 | Full sync, incremental sync, background sync, conflict handling, state hash verification |
| Offline Support | 3-5 | Saved forms, incomplete forms, offline form queue, auto-save |
| Settings | 3-5 | App settings, user settings, developer settings |
| Multimedia | 5-8 | Photo, audio, video capture/playback, signatures, image annotation |
| Advanced | 5-10 | Case sharing, practice mode, demo mode, printing, lookup tables |

**Exit criteria**: All oracle tests pass. All parity tests pass. Correctness scorecard at 95%+.

### Phase 4: Polish (~10-20 tasks, 1-2 weeks)

- Fix remaining parity failures
- Performance profiling and optimization
- Accessibility (VoiceOver, Dynamic Type)
- App Store metadata and submission prep
- Final E2E test suite validation

**Exit criteria**: Correctness scorecard at 99%+. App Store submission ready.

### Total Estimate

~130-190 tasks across all phases. With 3-5 parallel AI agents: approximately 9-17 weeks wall-clock time. First run will be slower; subsequent runs faster as the pipeline improves.

## 6. Iteration Model

### Run → Measure → Learn → Improve → Re-run

Each pipeline run produces:
- **Metrics**: tasks completed, tests passing, parity %, human interventions
- **Failure analysis**: which tasks failed, why, what the agent tried
- **Pattern detection**: common failure modes, recurring blockers
- **Process improvements**: updated task templates, better test fixtures, refined orchestrator logic

These learnings are captured in a `run-reports/` directory and used to improve the pipeline definition before the next run.

### What "re-run" means

You could re-run the entire pipeline on a fresh codebase (e.g., to benchmark improvements) or re-run specific phases (e.g., re-do Phase 3 after improving the oracle test suite). The pipeline is idempotent — running it again on the same inputs produces the same outputs.

## 7. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| KMP Kotlin/Native has performance issues on iOS | Slow form entry | Profile early (Phase 2), optimize hot paths |
| commcare-core has deep Java-isms that don't port cleanly | Blocking tasks in Phase 1 | Use Java interop initially, clean up incrementally |
| Compose Multiplatform iOS rendering bugs | UI glitches | Test on multiple iOS versions, fall back to UIKit for specific components |
| Oracle tests have false negatives (expected differences) | Noisy test results | Behavior catalog documents all expected differences |
| AI agents produce subtly wrong code that passes tests | Correctness gaps | Multi-layer verification reduces this; parity tests catch engine-level issues |
| FormPlayer API changes break oracle tests | Test infrastructure breaks | Pin FormPlayer version for testing, update periodically |
| Parallel agent merge conflicts | Blocked PRs | File-level task ownership prevents simultaneous modification |

## 8. Open Questions

1. **Repo structure**: Should this be a monorepo (commcare-core + app in one repo) or separate repos?
2. **FormPlayer hosting for CI**: Run FormPlayer in Docker in CI, or use a shared test instance?
3. **Android migration timeline**: When (if ever) does commcare-android adopt the Compose Multiplatform UI?
4. **App distribution**: TestFlight for beta, then App Store? Or enterprise distribution?
5. **CommCare HQ changes**: Any API changes needed for iOS-specific features (APNs push, etc.)?
