# Learning: Don't Skip the Issue/PR Workflow

**Date:** 2026-03-13
**Phase:** Phase 3 Tier 1
**Category:** Process

## What Happened

During Phase 3 Tier 1 implementation (14 tasks, MVP with real engine integration), all work was committed directly to `main` in a single commit without:
- Creating GitHub issues for each task/wave
- Creating feature branches per wave
- Creating PRs for review
- Following the PR Rules in CLAUDE.md ("One PR per wave", branch naming `kotlin-port/wave-N-<group-name>`)

## Why It Was Wrong

CLAUDE.md's Phase Transition Checklist explicitly requires:
1. Write a completion report (done)
2. Write a detailed plan (done)
3. **Create GitHub issues from the plan** (skipped)
4. Update CLAUDE.md (done)
5. Then start Wave 1

And PR Rules require:
- One PR per wave
- Branch naming: `kotlin-port/wave-N-<group-name>`
- CI gate: PR must pass before next wave starts

## Impact

- No reviewable PR history for Tier 1 changes
- No issue tracking for individual tasks
- No CI validation between waves (all 14 tasks landed at once)
- Harder to bisect if something breaks
- Lost the traceability that issue closure rules provide (evidence, acceptance criteria)

## Lesson

Even when working fast, always follow the full workflow:
1. Plan → Issues → Branch → PR → CI → Merge per wave
2. Never commit directly to main for feature work
3. The overhead is small compared to the traceability it provides
4. This applies regardless of whether a human or AI agent is doing the work

## Corrective Action

Starting with Tier 2, follow the proper process:
- Create GitHub issues (one per wave)
- Branch per wave (`phase3-tier2/wave-N-<name>`)
- PR per wave with tests passing
- Issue closure with evidence
