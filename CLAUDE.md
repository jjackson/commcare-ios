# CommCare iOS

iOS implementation of CommCare Mobile using Kotlin Multiplatform (KMP) + Compose Multiplatform, built via an autonomous AI development pipeline.

## Repo Architecture

**Monorepo.** commcare-core lives inside this repo as a `git subtree` at `commcare-core/`. This keeps all context (CLAUDE.md, learnings, plans, source code) in one worktree for AI agents. See `docs/learnings/2026-03-09-monorepo-for-agentic-development.md` for rationale.

- **commcare-core/** — Fork of `dimagi/commcare-core`, being ported from Java to Kotlin. Managed via `git subtree`.
- **docs/**, **pipeline/** — Plans, learnings, and autonomous pipeline tooling
- **Issues** are tracked here in commcare-ios (commcare-core fork has issues disabled)
- **Upstream extraction**: When ready to PR against `dimagi/commcare-core`, use `git subtree split --prefix=commcare-core -b upstream-ready`

## Project Structure

```
commcare-ios/
├── commcare-core/           # CommCare engine (git subtree from jjackson/commcare-core)
│   ├── src/commonMain/      # KMP shared code (643 .kt files — platform-agnostic)
│   ├── src/jvmMain/         # JVM platform implementations (100 .kt files)
│   ├── src/iosMain/         # iOS/Native platform implementations (45 .kt files)
│   ├── src/main/java/       # JVM-only: 1 Java compat file + parser specs + resources
│   ├── src/test/java/       # JUnit 4 tests (JVM)
│   ├── src/commonTest/      # Cross-platform tests (run on both JVM and iOS)
│   ├── build.gradle         # KMP Gradle build (jvm + iosSimulatorArm64 targets)
│   └── gradlew              # Gradle wrapper
├── app/                     # iOS Compose Multiplatform app shell
│   ├── src/commonMain/      # Shared app UI
│   └── src/iosMain/         # iOS entry point
├── docs/
│   ├── plans/               # Design docs, phase plans, completion reports
│   └── learnings/           # Post-hoc learnings from mistakes and discoveries
├── pipeline/                # Python pipeline for autonomous AI task orchestration
│   ├── src/pipeline/        # Models, orchestrator, task generator, GitHub client
│   └── tests/               # Pipeline tests
├── .github/workflows/       # CI: kotlin-tests.yml, ios-build.yml
└── CLAUDE.md                # You are here
```

## Current Status

**Phases 1-8 complete.** The Java→Kotlin conversion, KMP multiplatform migration, and iOS app shell are finished. All 65 GitHub issues closed, 8 phases delivered across 187 PRs.

- **commcare-core**: 643 commonMain, 100 jvmMain, 45 iosMain .kt files. 1 Java compat file remains (StorageManagerCompat.java). Gavaggan geodesy replaced with pure Kotlin Vincenty (PR #163).
- **iOS app** (`app/`): Compose Multiplatform shell with login, menus, cases, forms, sync, settings. See `docs/plans/2026-03-12-phase8-completion-report.md`.

**Phase 3: Feature Implementation** — Planned. Full feature parity with commcare-android (90-125 tasks across 4 tiers). Vertical slice approach: Tier 1 (MVP) → Tier 2 (daily field worker) → Tier 3 (advanced) → Tier 4 (Connect, PersonalID). Both Android and iOS targets from day one, SQLDelight storage, oracle test harness. See design and Tier 1 plan in Key Docs.

## Key Docs

**Plans:**
- **Design**: `docs/plans/2026-03-07-commcare-ios-design.md` — full architecture, phasing, verification strategy
- **Phase 3 design**: `docs/plans/2026-03-12-phase3-feature-implementation-design.md` — full parity spec from commcare-android audit (90-125 tasks, 4 tiers)
- **Phase 3 Tier 1 plan**: `docs/plans/2026-03-12-phase3-tier1-implementation-plan.md` — 14 tasks: Android target, SQLDelight, auth, install, menu, form entry, sync, oracle tests
- **Phase 8 completion**: `docs/plans/2026-03-12-phase8-completion-report.md` — iOS app shell: 17 UI/ViewModel files, 5 platform implementations, 9 waves
- **Phase 1-7 completion reports**: `docs/plans/2026-03-1{0,1,2}-phase{1..7}-completion-report.md` — progressive migration from 611 .kt files (Phase 1) to 643 commonMain (Phase 7)
- **Performance testing**: `docs/plans/2026-03-11-performance-testing-design.md` — kotlinx-benchmark infrastructure for JVM/Native comparison

**Learnings (by category):**
- **Kotlin conversion**: `kotlin-conversion-pitfalls`, `wave3-xpath-conversion-learnings`, `wave4-xform-parser-learnings`, `wave5-case-management-learnings`, `wave6-suite-session-learnings`, `wave8-core-services-learnings`, `wave1-collection-replacement-learnings`
- **KMP migration**: `wave6-7-kmp-migration-learnings`, `ios-ci-learnings`, `commonmain-migration-blockers`, `phase4-deep-migration-learnings`, `phase6-deep-migration-learnings`, `phase7-bulk-migration-learnings`, `wave6-xpath-migration-learnings`, `wave7-commonmain-dependency-inversion`, `wave7-commonmain-migration-learnings`
- **Serialization**: `wave4-serialization-framework-learnings`, `phase5-serialization-migration-learnings`, `phase5-wave8-serialization-commonmain-learnings`, `wave7-serialization-migration-learnings`, `ios-xml-serializer-namespace-learnings`
- **Process**: `pr-discipline`, `issue-closure-discipline`, `claude-md-importance`, `monorepo-for-agentic-development`, `phase3-planning-learnings`
- **Architecture**: `abstract-tree-element-degenerify`, `j2k-converter-vs-ai-conversion`, `gavaghan-replacement-learnings`
- **iOS app**: `phase8-ios-app-learnings`, `phase8-wave1-cinterop-learnings` — NSURLSession sync, NSJSONSerialization, cinterop patterns (CommonCrypto, SecureRandom, file system)

All learning files are in `docs/learnings/`.

## Kotlin Conversion Checklist

When converting Java files to Kotlin in commcare-core, check for these **before pushing**:

1. **Nullable parameters**: Scan Java call sites. If any passes `null`, the Kotlin parameter must be `?` type.
2. **Generic raw types**: Java raw types don't exist in Kotlin. If a generic type parameter is consistently bypassed, consider removing it.
3. **Return type invariance**: `Vector<SubType>` is NOT `Vector<SuperType>` in Kotlin. Use `out` variance on interface declarations.
4. **`open` keyword**: Kotlin classes are `final` by default. Check if the class is subclassed anywhere (including commcare-android, FormPlayer) and mark `open`.
5. **`@JvmField` / `@JvmStatic`**: Java subclasses accessing `super.field` need `@JvmField`. Java callers of companion methods need `@JvmStatic`.
6. **Local build first**: Run `./gradlew compileKotlin compileJava` locally before pushing. Run `./gradlew test` for final verification.
7. **KDoc `*/` hazard**: Grep for `*/` inside `/** ... */` block comments — XPath wildcards like `/data/*/to` prematurely close the comment. Escape as `` `*` ``.
8. **Preserve `abstract`**: If the Java class is `abstract`, the Kotlin class must be `abstract` too (not `open`). Reflection-based tests depend on this.
9. **Nullable parameter threading**: Don't add `!!` on nullable params just to call a child method — make the child accept nullable too. Java silently passes null through call chains.
10. **`protected` → `internal`**: Java `protected` = package + subclass access. Kotlin `protected` = subclass only. Use `internal` for same-package non-subclass callers.
11. **Companion method inheritance**: Kotlin companion methods are NOT inherited by subclasses. Call them on the defining class (`DataInstance.unpackReference`), not a subclass (`FormInstance.unpackReference`).
12. **`@JvmField` vs `open`**: `@JvmField` cannot be used on `open` properties. Drop `open` — subclasses access the inherited field directly.
13. **Companion `protected`**: Companion object members cannot be `protected`. Use `internal const val` for constants that subclasses need within the same module.
14. **Smart cast on `var`**: Kotlin won't smart-cast mutable properties after null checks. Capture to a local `val` first: `val el = element; if (el != null) ...`
15. **`const val` auto-inlines**: `const val` in companion objects compiles to `public static final` in Java bytecode automatically. No `@JvmField` needed for String/Int/Long/Boolean constants.
16. **JVM signature clash: `val` vs `fun`**: A constructor `val foo` generates `getFoo()`, which clashes with `override fun getFoo()` from an interface. Fix: rename the constructor param (e.g., `_foo`) and delegate from the override.
17. **JVM signature clash: field vs getter**: A `var foo` field generates `getFoo()`, which clashes with an explicit `fun getFoo()`. Fix: rename the backing field to `_foo`.
18. **Java boxed types in generics**: `Pair<Integer, Integer>` must be `Pair<Int, Int>` in Kotlin. Never use Java boxed types in Kotlin generic type arguments.
19. **Kotlin-to-Kotlin `fun` calls**: When calling Kotlin code that defines `fun getFoo()`, use `getFoo()` not `foo`. Kotlin only synthesizes property access for *Java* getters, not Kotlin `fun` declarations.
20. **`internal` hides from other source sets**: Kotlin `internal` mangles names in bytecode. Java code in separate Gradle source sets (ccapi, cli, test) can't access `internal` properties. Add explicit public getter methods.
21. **Property getter/setter clash**: A `var foo` auto-generates `getFoo()`/`setFoo()`. Don't also define explicit `fun setFoo()` — remove it and let callers use property syntax.
22. **`@Throws` must match exactly in commonMain**: Kotlin/Native (iOS) requires override methods to have **exactly** matching `@Throws` annotations as their parent declarations. On JVM, subsets are allowed, but in commonMain (compiled for both targets), every override must list the same exceptions. Check all levels of the hierarchy (interface → abstract class → concrete class).

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
# - ios-build.yml: runs on PRs touching app/, commcare-core/, gradle files (macOS runner)
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

1. **Write a completion report** for the finished phase → `docs/plans/<date>-phase<N>-completion-report.md`
2. **Write a detailed plan** for the next phase → `docs/plans/<date>-phase<N+1>-<name>-plan.md`
   - Include: dependency analysis, task breakdown with file counts, ordering/dependency graph, acceptance criteria per task, risk mitigations
   - Reference: Phase 1 plan (`docs/plans/2026-03-07-phase1-core-port-plan.md`) and Phase 2 plan (`docs/plans/2026-03-10-phase2-kmp-multiplatform-plan.md`) as examples of the expected detail level
3. **Create GitHub issues** from the plan — one issue per wave/task, following the issue template in the plan
4. **Update CLAUDE.md** — add the new phase's status table and link the plan doc in Key Docs
5. **PR and merge** the plan doc and CLAUDE.md updates (doc PRs, per Doc PR Rules)
6. **Then start Wave 1** of the new phase

Do not skip straight to code. The plan is the first deliverable of every phase.

## AI Agent Guidelines

- **Always `git pull` before starting work** — local main can silently fall behind remote, leading to decisions based on stale project state
- **Check the Phase Transition Checklist** when a phase completes — plan before code
- **For feature parity work**: derive the feature list from the target app's codebase + docs, not from design doc estimates. See `docs/learnings/2026-03-12-phase3-planning-learnings.md`.
- Read the relevant issue's full description before starting work — it contains "Files to Read", "What to Do", and "Tests That Must Pass"
- Read `docs/learnings/` files before starting a conversion wave — they document real failures
- Follow the Kotlin Conversion Checklist above for every file
- Follow PR Rules and Issue Closure Rules exactly — AI agents must not skip deliverable steps
- When in doubt about a technical decision, document it in the PR description
- Never mix documentation changes into code branches — use separate doc PRs (see Doc PR Rules)
