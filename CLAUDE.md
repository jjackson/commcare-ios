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
├── commcare-core/      # CommCare engine (git subtree from jjackson/commcare-core)
│   ├── src/main/java/  # Kotlin + Java source (being converted wave by wave)
│   ├── src/test/java/  # JUnit 4 tests (remain in Java)
│   ├── build.gradle    # Gradle build with Kotlin JVM plugin
│   └── gradlew         # Gradle wrapper
├── docs/
│   ├── plans/          # Design docs and phase implementation plans
│   └── learnings/      # Post-hoc learnings from mistakes and discoveries
├── pipeline/           # Python pipeline for autonomous AI task orchestration
│   ├── src/pipeline/   # Models, orchestrator, task generator, GitHub client
│   └── tests/          # Pipeline tests
├── .github/workflows/  # CI: kotlin-tests.yml, ios-build.yml
└── CLAUDE.md           # You are here
```

## Current Status

**Phase 1: Core Port** — Converting commcare-core from Java to Kotlin (642 files across 8 waves).

| Wave | Group | Files | Status |
|------|-------|-------|--------|
| 0 | Build setup | — | Done (commcare-core PR #2) |
| 1 | javarosa-utilities | 115 | Done (commcare-core PR #3) |
| 2 | javarosa-model | 82 | Done (commcare-core PR #4) |
| 3 | xpath-engine | 134 | Done (PR #13 merged) |
| 4 | xform-parser | 27 | Done (PR #21) |
| 5 | case-management | 60 | Done (PR #24) |
| 6 | suite-and-session | 93 | Done (PR #26) |
| 7 | resources | 28 | Done (PR #28) |
| 8 | commcare-core-services | 71 | Done (PR #29) |

**Phase 1 Complete.** KMP multiplatform targets added (PR #31). Final verification passed (Issue #12): 710 tests, 611 .kt + 32 .java, 5 JARs verified. See `docs/plans/2026-03-10-phase1-completion-report.md`.

**Phase 2: KMP Multiplatform & App Shell** — Move code to commonMain with expect/actual abstractions, build iOS app shell.

| Wave | Group | Files | Issue | Status |
|------|-------|-------|-------|--------|
| 1 | Replace Guava/joda-time | ~15 | #34 | Done (PR #45) |
| 2 | Serialization abstraction | ~10 new | #35 | Open |
| 3 | XML parsing abstraction | ~10 new | #36 | Open |
| 4 | Crypto/net/file/JSON abstractions | ~20 new, ~19 mod | #37 | Open |
| 5 | Move pure Kotlin to commonMain | ~270 moved | #38 | Open |
| 6 | Migrate serialization consumers | ~215 mod+moved | #39 | Open |
| 7 | Migrate XML consumers | ~60 mod+moved | #40 | Open |
| 8 | iOS app shell | ~15 new | #41 | Open |
| 9 | E2E validation | ~5 new | #42 | Open |

**Dependency graph:** Waves 1-4 create abstractions (can partially overlap). Waves 5-7 move files (depend on respective abstraction waves). Wave 8 needs macOS. Wave 9 is final validation.

## Key Docs

**Plans:**
- **Design**: `docs/plans/2026-03-07-commcare-ios-design.md` — full architecture, phasing, verification strategy
- **Phase 2 plan**: `docs/plans/2026-03-10-phase2-kmp-multiplatform-plan.md` — wave details, dependency analysis, expect/actual strategy
- **Phase 1 plan**: `docs/plans/2026-03-07-phase1-core-port-plan.md` — wave details, PR strategy, issue closure template
- **Phase 0 plan**: `docs/plans/2026-03-07-phase0-scaffold-plan.md` — completed infrastructure setup (pipeline, CI, task generator). **Skip unless debugging pipeline issues.**
- **Degenerify design**: `docs/plans/2026-03-08-abstract-tree-element-degenerify-design.md` — removing AbstractTreeElement type parameter (completed in Wave 2)

**Learnings:**
- **Conversion pitfalls**: `docs/learnings/2026-03-08-kotlin-conversion-pitfalls.md` — 6 recurring issues with fixes (source for Kotlin Conversion Checklist below)
- **PR discipline**: `docs/learnings/2026-03-08-pr-discipline.md` — why every deliverable step must be explicit in the plan (source for PR Rules below)
- **Issue closure discipline**: `docs/learnings/2026-03-08-issue-closure-discipline.md` — why evidence is as important as code (source for Issue Closure Rules below)
- **CLAUDE.md importance**: `docs/learnings/2026-03-08-claude-md-importance.md` — why CLAUDE.md must exist early and integrate learnings
- **Degenerify**: `docs/learnings/2026-03-08-abstract-tree-element-degenerify.md` — removing type parameter from AbstractTreeElement, with rationale
- **Monorepo for agents**: `docs/learnings/2026-03-09-monorepo-for-agentic-development.md` — why all context must be in one directory tree for AI agents
- **Wave 3 XPath learnings**: `docs/learnings/2026-03-09-wave3-xpath-conversion-learnings.md` — KDoc `*/` hazard, abstract preservation, nullable threading, protected→internal
- **Wave 4 XForm parser learnings**: `docs/learnings/2026-03-09-wave4-xform-parser-learnings.md` — companion method inheritance, `@JvmField` vs `open`, companion `protected` limitation, smart cast on `var`, `const val` auto-inline
- **J2K vs AI conversion**: `docs/learnings/2026-03-09-j2k-converter-vs-ai-conversion.md` — why we chose AI-driven conversion over IntelliJ's J2K converter
- **Wave 5 case-management learnings**: `docs/learnings/2026-03-09-wave5-case-management-learnings.md` — JVM signature clashes (constructor `val` vs interface method, field vs getter), Java boxed types in generics, Kotlin-to-Kotlin method calls
- **Wave 6 suite-session learnings**: `docs/learnings/2026-03-10-wave6-suite-session-learnings.md` — `internal` hides from Java in other source sets, property getter/setter clashes, nullable return types Java silently allowed
- **Wave 8 core-services learnings**: `docs/learnings/2026-03-10-wave8-core-services-learnings.md` — `@JvmField protected` for cross-source-set Java subclasses, OkHttp 4/Okio 2 API migration, `const val` requires compile-time constants

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

## PR Rules

- **All PRs in commcare-ios** — no PRs on the separate commcare-core repo during development
- **One PR per wave** — each wave gets its own PR
- **Branch naming**: `kotlin-port/wave-N-<group-name>`
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
./gradlew compileKotlin compileJava    # Quick compilation check
./gradlew test                          # Full test suite
cd ..                                   # Back to repo root for git

# Future KMP:
cd commcare-core && ./gradlew :commcare-core:jvmTest  # KMP JVM tests (once KMP targets added)

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

- **Check the Phase Transition Checklist** when a phase completes — plan before code
- Read the relevant issue's full description before starting work — it contains "Files to Read", "What to Do", and "Tests That Must Pass"
- Read `docs/learnings/` files before starting a conversion wave — they document real failures
- Follow the Kotlin Conversion Checklist above for every file
- Follow PR Rules and Issue Closure Rules exactly — AI agents must not skip deliverable steps
- When in doubt about a technical decision, document it in the PR description
- Never mix documentation changes into code branches — use separate doc PRs (see Doc PR Rules)
