# Learning: CLAUDE.md Must Be Created Early

**Date**: 2026-03-08
**Context**: Reviewing project documentation approach after completing Phase 0 and Waves 0-2
**Status**: Resolved

## Problem

We built extensive documentation — a design doc, two phase plans, four learning docs, a pipeline with structured issue templates — but had **no CLAUDE.md file**. Every new Claude Code session started cold with no awareness of:

- Project identity and architecture
- The two-repo structure (commcare-ios for planning/app, commcare-core for engine port)
- Branch/PR conventions learned through failure
- The Kotlin conversion checklist distilled from two waves of pitfalls
- Issue closure requirements learned through failure
- Build commands and CI expectations

All of this information existed in `docs/` but nothing directed an agent to read it. Agents had to either be told explicitly or stumble upon the right files.

## Why This Matters

CLAUDE.md is loaded automatically at the start of every session. It's the difference between:

- **With CLAUDE.md**: Agent starts with project context, knows the conventions, follows the checklists
- **Without CLAUDE.md**: Agent starts blank, may repeat mistakes we already documented, may skip PR/closure steps we already learned are critical

The docs in `docs/learnings/` captured important failures, but those learnings were inert — they only helped if an agent happened to read them. CLAUDE.md makes the critical subset of learnings **always active**.

## Fix Applied

Created `CLAUDE.md` at repo root containing:

1. Project identity and repo architecture (commcare-ios vs commcare-core)
2. Current status (phase, wave progress table)
3. Links to key docs (design, phase plan, conversion pitfalls)
4. Kotlin Conversion Checklist (6 items, extracted from conversion pitfalls learning)
5. PR Rules (extracted from PR discipline learning)
6. Issue Closure Rules (extracted from issue closure discipline learning)
7. Build commands
8. AI Agent Guidelines

## Key Takeaway

**CLAUDE.md is the operational checklist; docs/ is the detailed reference.** Create CLAUDE.md early — ideally at project inception — and update it as learnings accumulate. The pattern is:

1. Encounter a problem → write a learning doc in `docs/learnings/`
2. Extract the actionable checklist from the learning → add to CLAUDE.md
3. Now every future session benefits without needing to discover the learning doc

Learnings without CLAUDE.md integration are documentation. Learnings with CLAUDE.md integration are process improvement.
