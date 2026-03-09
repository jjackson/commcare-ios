# Learning: PR Discipline for AI-Driven Code Conversion

**Date**: 2026-03-08
**Context**: Waves 0-2 of commcare-core Java-to-Kotlin conversion
**Status**: Resolved

## Problem

We failed to create reviewable PRs **twice** during the Kotlin conversion:

1. **Wave 1 (javarosa-utilities, 115 files):** All conversion + fix commits landed on a single `kotlin-port` branch with no PR created.
2. **Wave 2 (javarosa-model, 82 files):** Same pattern — more commits piled onto the same branch, resulting in a single PR (#1) with ~400 files changed.

The PR was unreviewable. No human could meaningfully review 400 files of Java→Kotlin conversion in one pass.

## Root Cause

The Phase 1 plan's "Step 6: Commit" said:

```bash
git add -A
git commit -m "port: convert <group-name> to Kotlin (<N> files)"
```

That's it. No instructions to:
- Create a branch per wave
- Squash fix commits before creating a PR
- Open a PR with a specific target
- Wait for CI/review before starting the next wave

The pipeline's task descriptions mentioned "create a PR" but didn't specify sizing, branch strategy, or review gates. Without explicit instructions, the AI agent defaulted to committing everything on one branch.

## Fix Applied

Added a **PR Strategy** section to the Phase 1 plan (`docs/plans/2026-03-07-phase1-core-port-plan.md`) that specifies:

- **One PR per wave** — each wave gets its own PR for human review
- **Branch naming**: `kotlin-port/wave-N-<group-name>`
- **Stacked PR targets**: Each PR targets the previous wave's branch
- **Squash fix commits**: All compilation/interop fix commits squashed into one commit per wave
- **PR size guidelines**: ~100-150 files max per PR
- **CI gate**: PR must pass before next wave starts

Retroactively split the single `kotlin-port` branch into 3 stacked PRs:
- PR #2: Wave 0 build setup → `master`
- PR #3: Wave 1 javarosa-utilities (115 files) → Wave 0
- PR #4: Wave 2 javarosa-model (82 files) → Wave 1

## Key Takeaway

**AI agents follow instructions literally.** If the plan says "commit", they commit — they don't infer that a PR should be created, sized appropriately, and reviewed. Every deliverable step must be explicit in the plan, including:
- Branch creation and naming
- PR creation with specific targets
- Squash/cleanup before PR
- Review/CI gates before proceeding

## Related: Issue Closure Discipline

The same pattern — lack of explicit instructions leading the AI agent to skip a step — applied to issue closure comments. We closed issues #2, #3, and #4 with terse one-line comments ("Completed. PR: link") that provided no evidence of completion, despite each issue having a "Tests That Must Pass" section with explicit acceptance criteria.

See [2026-03-08-issue-closure-discipline.md](./2026-03-08-issue-closure-discipline.md) for the full learning and the closure comment template now added to the Phase 1 plan.
