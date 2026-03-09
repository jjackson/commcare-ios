# Learning: Issue Closure Discipline

**Date**: 2026-03-08
**Context**: Closing GitHub issues #2, #3, #4 (Waves 0-2) after merging stacked PRs
**Status**: Resolved

## Problem

We closed GitHub issues #2, #3, and #4 (corresponding to Waves 0, 1, and 2 of the commcare-core Kotlin conversion) with terse one-line comments like:

> Completed. PR: <link>

These comments provided no evidence of completion. Each issue had a "Tests That Must Pass" section with explicit acceptance criteria, but we didn't reference or verify against any of them. The closure comments were functionally useless — they confirmed the issue was closed but gave no indication of WHY we considered the work done.

## Why This Matters

The issue closure comment is part of the project's evidence trail. Future readers — human reviewers, other AI agents working on later phases, auditors reviewing the project — need to understand:

- **What was actually done** (which files, which packages, what changes)
- **Whether the acceptance criteria were met** (not just "tests pass" but which specific criteria from the issue were verified, and how)
- **What technical decisions were made** along the way (interop fixes, design choices, deviations from the plan)

Without this trail, the code is unverifiable. Someone looking at a closed issue with "Completed. PR: link" has to reverse-engineer the PR diff to understand what happened and whether the acceptance criteria were actually met.

## Root Cause

Same pattern as the PR discipline issue: the plan did not explicitly instruct the AI agent to write thorough closure comments. The agent did the minimum — close the issue and link the PR. Without explicit instructions, AI agents default to terse, mechanical completion.

## Fix Applied

1. **Retroactive fix:** Added thorough completion comments to all 3 issues (#2, #3, #4) that include:
   - Summary of what was done
   - Checkbox verification of each item in the issue's "Tests That Must Pass" section, with evidence
   - Notable technical decisions made during the wave
   - Link to the merged PR

2. **Going forward:** Every issue closure must include:
   1. **What was done** — summary of changes made (files converted, packages affected, notable changes)
   2. **Acceptance criteria verification** — checkbox list matching the issue's "Tests That Must Pass" section, with evidence (test output, CI link, compilation proof)
   3. **Notable technical decisions** — anything non-obvious that a reviewer should know
   4. **PR link** — link to the merged PR

3. **Plan updated:** Added an "Issue Closure" subsection to the PR Strategy section of the Phase 1 plan with a template for closure comments.

## Key Takeaway

Evidence is as important as the code. The pipeline produces both code artifacts AND a decision/verification trail. Without the trail, the code is unverifiable. A closed issue without evidence of completion is indistinguishable from an issue that was closed prematurely or incorrectly.
