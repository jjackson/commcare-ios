# CommCare iOS

iOS implementation of CommCare Mobile using Kotlin Multiplatform (KMP) + Compose Multiplatform, built via an autonomous AI development pipeline.

## Repo Architecture

**Monorepo.** commcare-core lives inside this repo as a `git subtree` at `commcare-core/`. This keeps all context (CLAUDE.md, learnings, plans, source code) in one worktree for AI agents. See `docs/learnings/2026-03-09-monorepo-for-agentic-development.md` for rationale.

- **commcare-core/** — Fork of `dimagi/commcare-core`, fully ported from Java to Kotlin. Managed via `git subtree`.
- **docs/**, **pipeline/** — Plans, learnings, and autonomous pipeline tooling
- **Issues** are tracked here in commcare-ios (commcare-core fork has issues disabled)
- **Upstream extraction**: When ready to PR against `dimagi/commcare-core`, use `git subtree split --prefix=commcare-core -b upstream-ready`

## Project Structure

```
commcare-ios/
├── commcare-core/           # CommCare engine (git subtree from jjackson/commcare-core)
│   ├── src/commonMain/      # KMP shared code (656 .kt files — platform-agnostic)
│   ├── src/jvmMain/         # JVM platform implementations (97 .kt files)
│   ├── src/iosMain/         # iOS/Native platform implementations (47 .kt files)
│   ├── src/main/java/       # JVM-only: parser specs + resources (zero Java source files)
│   ├── src/test/java/       # JUnit 4 tests — 129 .kt + 4 .java (JVM)
│   ├── src/commonTest/      # Cross-platform tests — 23 .kt (run on both JVM and iOS)
│   ├── build.gradle         # KMP Gradle build (jvm + iosSimulatorArm64 targets)
│   └── gradlew              # Gradle wrapper
├── app/                     # Compose Multiplatform app (JVM + iOS)
│   ├── src/commonMain/      # Shared app code (51 .kt — UI, ViewModels, engine wiring, storage)
│   ├── src/jvmMain/         # JVM platform (5 .kt)
│   ├── src/iosMain/         # iOS platform (6 .kt)
│   ├── src/jvmTest/         # JVM tests (20 .kt — sandbox, oracle, e2e)
│   └── src/commonMain/sqldelight/  # SQLDelight schema
├── docs/
│   ├── plans/               # Design docs, phase plans, completion reports
│   └── learnings/           # Post-hoc learnings from mistakes and discoveries
├── pipeline/                # Python pipeline for autonomous AI task orchestration
│   ├── src/pipeline/        # Models, orchestrator, task generator, GitHub client
│   └── tests/               # Pipeline tests
├── .github/workflows/       # CI: kotlin-tests.yml, ios-build.yml
└── CLAUDE.md                # You are here
```

## Current Status & What's Next

**Phases 1-3 complete.** All Kotlin conversion, KMP migration, and feature implementation done. 800+ JVM tests, 100+ cross-platform tests, 113 app-level tests. Feature-complete CommCare iOS app. See `docs/plans/2026-03-15-phase3-tier3-completion-report.md` for details.

**Phase 4: Polish — NOT YET PLANNED.** The Phase Transition Checklist requires a detailed plan before any code. Write `docs/plans/2026-03-16-phase4-polish-plan.md` as the first task. The original design doc (`docs/plans/2026-03-07-commcare-ios-design.md`, Phase 4 section) outlines:
- Fix remaining parity failures
- Performance profiling and optimization
- Accessibility (VoiceOver, Dynamic Type)
- App Store metadata and submission prep
- Final E2E test suite validation
- Exit criteria: correctness scorecard at 99%+, App Store submission ready

**If you are an agent starting a session:** Your first job is to check whether the Phase 4 plan exists. If not, write it following the Phase Transition Checklist. If it does, pick up the next open issue.

## Key Docs

**Active:**
- **Original design**: `docs/plans/2026-03-07-commcare-ios-design.md` — full architecture, phasing, verification strategy, **Phase 4 outline**
- **Xcode project setup**: `docs/plans/2026-03-15-xcode-project-setup.md` — steps to create Xcode project embedding KMP framework
- **iOS cinterop plan**: `docs/plans/2026-03-15-ios-cinterop-implementation-plan.md` — replacing 4 iOS stubs with real implementations (DONE)
- **Java vs Kotlin benchmarks**: `docs/plans/2026-03-15-java-vs-kotlin-benchmark-comparison.md` — 14 benchmarks, Kotlin equal or faster

**Completion reports (historical):**
- Phase 3 Tier 3: `docs/plans/2026-03-15-phase3-tier3-completion-report.md` — 32 tasks, advanced features
- Phase 3 Tier 2: `docs/plans/2026-03-14-phase3-tier2-completion-report.md` — 30 tasks, daily field worker features
- Phase 3 Tier 1: `docs/plans/2026-03-13-phase3-tier1-completion-report.md` — MVP engine integration
- Foundation hardening: `docs/plans/2026-03-13-foundation-hardening-test-migration-plan.md`
- Phase 8: `docs/plans/2026-03-12-phase8-completion-report.md` — iOS app shell
- Phases 1-7: `docs/plans/2026-03-1{0,1,2}-phase{1..7}-completion-report.md`

**Learnings (by category):**
- **Kotlin conversion**: `kotlin-conversion-pitfalls`, `wave3-xpath-conversion-learnings`, `wave4-xform-parser-learnings`, `wave5-case-management-learnings`, `wave6-suite-session-learnings`, `wave8-core-services-learnings`, `wave1-collection-replacement-learnings`
- **KMP migration**: `wave6-7-kmp-migration-learnings`, `ios-ci-learnings`, `commonmain-migration-blockers`, `phase4-deep-migration-learnings`, `phase6-deep-migration-learnings`, `phase7-bulk-migration-learnings`, `wave6-xpath-migration-learnings`, `wave7-commonmain-dependency-inversion`, `wave7-commonmain-migration-learnings`
- **Serialization**: `wave4-serialization-framework-learnings`, `phase5-serialization-migration-learnings`, `phase5-wave8-serialization-commonmain-learnings`, `wave7-serialization-migration-learnings`, `ios-xml-serializer-namespace-learnings`
- **Foundation hardening**: `foundation-hardening-learnings` — test migration, serialization bugs, property conversion
- **Cross-platform validation**: `xformparser-port-learnings` — XFormParser port, golden file testing, classpath collision traps
- **Process**: `pr-discipline`, `issue-closure-discipline`, `claude-md-importance`, `monorepo-for-agentic-development`, `tier1-process-skip-learnings`
- **Architecture**: `abstract-tree-element-degenerify`, `j2k-converter-vs-ai-conversion`, `gavaghan-replacement-learnings`
- **iOS app**: `phase8-ios-app-learnings`, `phase8-wave1-cinterop-learnings` — cinterop patterns, NSURLSession, LAContext biometrics, UIPrintInteractionController, NSTimer, NSUserDefaults

All learning files are in `docs/learnings/`.

## Kotlin Conversion Checklist

Java→Kotlin conversion is complete (zero Java source files remain). The full 22-item checklist is preserved in `docs/kotlin-conversion-checklist.md` for reference if converting remaining test .java files or future upstream Java code.

## PR Rules

- **All PRs in commcare-ios** — no PRs on the separate commcare-core repo during development
- **One PR per wave** — each wave gets its own PR
- **Branch naming**: `<phase-or-feature>/<description>` (e.g., `phase8/wave1-crypto-files`, `fix/ios-platform-timezone`)
- **Target**: `main` branch of commcare-ios
- **Squash fix commits**: All compilation/interop fixes squashed into one commit per wave
- **Size**: ~100-150 files max per PR
- **CI gate**: PR must pass before next wave starts

## Doc PR Rules

- **Doc changes get their own PRs** — changes to CLAUDE.md, `docs/plans/`, and `docs/learnings/` must not be mixed into code PRs
- **Branch naming**: `docs/<short-description>`
- **Self-merge after CI**: Doc PRs can be merged by the agent without human review, unless they change architectural decisions
- **During a wave**: Note learnings in the code PR description, then create a separate doc PR after the code PR merges

## Issue Closure Rules

Every issue closure must include:

1. **What was done** — summary of changes (files, packages, notable changes)
2. **Acceptance criteria verification** — checkbox list matching the issue's "Tests That Must Pass", with evidence (test output, CI link)
3. **Notable technical decisions** — non-obvious choices made during the wave
4. **PR link** — link to the merged PR

Terse closures like "Completed. PR: link" are not acceptable. Evidence is as important as code.

## Build Commands

```bash
# JDK is bundled with Android Studio — set JAVA_HOME before Gradle commands:
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"

# Build from commcare-core/ (return to repo root for git commands):
cd commcare-core
./gradlew compileKotlin compileJava                    # JVM compilation check
./gradlew compileCommonMainKotlinMetadata               # KMP common code check (stricter than compileKotlin)
./gradlew test                                          # JVM test suite
./gradlew iosSimulatorArm64Test                         # iOS simulator tests (macOS only)
cd ..                                                   # Back to repo root for git

# CI workflows:
# - kotlin-tests.yml: runs on PRs touching commcare-core/, gradle files
# - ios-build.yml: builds Kotlin framework + Swift app via xcodegen, launches on iOS simulator (macos-15 runner)
```

## Subtree Management

commcare-core is managed via `git subtree`. Key commands:

```bash
# Pull latest changes from jjackson/commcare-core:
git subtree pull --prefix=commcare-core https://github.com/jjackson/commcare-core.git master --squash

# Push changes back to jjackson/commcare-core:
git subtree push --prefix=commcare-core https://github.com/jjackson/commcare-core.git <branch-name>

# Extract clean history for upstream PR to dimagi/commcare-core:
git subtree split --prefix=commcare-core -b upstream-ready
```

## Phase Transition Checklist

When all issues for a phase are closed, follow these steps **before writing any code for the next phase**:

1. **Close all open issues** for the finished phase with proper closure notes (per Issue Closure Rules)
2. **Write a completion report** for the finished phase → `docs/plans/<date>-phase<N>-completion-report.md`
3. **Write a detailed plan** for the next phase → `docs/plans/<date>-phase<N+1>-<name>-plan.md`
   - Include: dependency analysis, task breakdown with file counts, ordering/dependency graph, acceptance criteria per task, risk mitigations
   - Reference: Phase 1 plan (`docs/plans/2026-03-07-phase1-core-port-plan.md`) and Phase 2 plan (`docs/plans/2026-03-10-phase2-kmp-multiplatform-plan.md`) as examples of the expected detail level
4. **Create GitHub issues** from the plan — one issue per wave/task, following the issue template in the plan
5. **Update CLAUDE.md** — add the new phase's status and link the plan doc in Key Docs
6. **PR and merge** the plan doc and CLAUDE.md updates (doc PRs, per Doc PR Rules)
7. **Verify**: Confirm new phase plan exists, issues are created, CLAUDE.md is updated. If any are missing, that is the first task.
8. **Then start Wave 1** of the new phase

Do not skip straight to code. The plan is the first deliverable of every phase.

## AI Agent Guidelines

- **Check the Phase Transition Checklist** when a phase completes — plan before code
- **Always follow full workflow** regardless of speed — branch, PR, CI, merge per wave. Skipping steps (as happened in Tier 1) loses reviewability and traceability. See `docs/learnings/2026-03-13-tier1-process-skip-learnings.md`.
- **First task of any session**: Check "Current Status & What's Next" above. If no plan exists for the current phase, writing the plan IS your task.
- Read the relevant issue's full description before starting work — it contains "Files to Read", "What to Do", and "Tests That Must Pass"
- Read `docs/learnings/` files before starting a conversion wave — they document real failures
- Follow the Kotlin Conversion Checklist (in `docs/kotlin-conversion-checklist.md`) for any Java→Kotlin work
- Follow PR Rules and Issue Closure Rules exactly — AI agents must not skip deliverable steps
- When in doubt about a technical decision, document it in the PR description
- Never mix documentation changes into code branches — use separate doc PRs (see Doc PR Rules)
