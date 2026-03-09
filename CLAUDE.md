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
| 0 | Build setup | — | Done (PR #2) |
| 1 | javarosa-utilities | 115 | Done (PR #3) |
| 2 | javarosa-model | 82 | Done (PR #4) |
| 3 | xpath-engine | 134 | Open (Issue #5) |
| 4 | xform-parser | 27 | Open (Issue #6) |
| 5 | case-management | 66 | Open (Issue #7) |
| 6 | suite-and-session | 93 | Open (Issue #8) |
| 7 | resources | 28 | Open (Issue #9) |
| 8 | commcare-core-services | 71 | Open (Issue #10) |

After Phase 1: KMP multiplatform targets (Issue #11), then final verification (Issue #12).

## Key Docs

- **Design**: `docs/plans/2026-03-07-commcare-ios-design.md` — full architecture, phasing, verification strategy
- **Phase 1 plan**: `docs/plans/2026-03-07-phase1-core-port-plan.md` — wave details, PR strategy, issue closure template
- **Conversion pitfalls**: `docs/learnings/2026-03-08-kotlin-conversion-pitfalls.md` — 6 recurring issues with fixes

## Kotlin Conversion Checklist

When converting Java files to Kotlin in commcare-core, check for these **before pushing**:

1. **Nullable parameters**: Scan Java call sites. If any passes `null`, the Kotlin parameter must be `?` type.
2. **Generic raw types**: Java raw types don't exist in Kotlin. If a generic type parameter is consistently bypassed, consider removing it.
3. **Return type invariance**: `Vector<SubType>` is NOT `Vector<SuperType>` in Kotlin. Use `out` variance on interface declarations.
4. **`open` keyword**: Kotlin classes are `final` by default. Check if the class is subclassed anywhere (including commcare-android, FormPlayer) and mark `open`.
5. **`@JvmField` / `@JvmStatic`**: Java subclasses accessing `super.field` need `@JvmField`. Java callers of companion methods need `@JvmStatic`.
6. **Local build first**: Run `./gradlew compileKotlin compileJava` locally before pushing. Run `./gradlew test` for final verification.

## PR Rules

- **One PR per wave** — each wave gets its own PR
- **Branch naming**: `kotlin-port/wave-N-<group-name>`
- **Stacked targets**: Each PR targets the previous wave's branch (not master)
- **Squash fix commits**: All compilation/interop fixes squashed into one commit per wave
- **Size**: ~100-150 files max per PR
- **CI gate**: PR must pass before next wave starts

## Issue Closure Rules

Every issue closure must include:

1. **What was done** — summary of changes (files, packages, notable changes)
2. **Acceptance criteria verification** — checkbox list matching the issue's "Tests That Must Pass", with evidence (test output, CI link)
3. **Notable technical decisions** — non-obvious choices made during the wave
4. **PR link** — link to the merged PR

Terse closures like "Completed. PR: link" are not acceptable. Evidence is as important as code.

## Build Commands

```bash
# From repo root — commcare-core is a subdirectory:
cd commcare-core
./gradlew compileKotlin compileJava    # Quick compilation check
./gradlew test                          # Full test suite

# Future KMP:
./gradlew :commcare-core:jvmTest       # KMP JVM tests (once KMP targets added)

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

## AI Agent Guidelines

- Read the relevant issue's full description before starting work — it contains "Files to Read", "What to Do", and "Tests That Must Pass"
- Read `docs/learnings/` files before starting a conversion wave — they document real failures
- Follow the Kotlin Conversion Checklist above for every file
- Follow PR Rules and Issue Closure Rules exactly — AI agents must not skip deliverable steps
- When in doubt about a technical decision, document it in the PR description
