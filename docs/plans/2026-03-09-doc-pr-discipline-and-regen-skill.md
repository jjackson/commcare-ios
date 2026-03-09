# Doc PR Discipline + Doc Regeneration Skill — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Separate documentation from code PRs, create a lean doc-regeneration skill, and validate it with a dry-run against commcare-ios.

**Architecture:** Three independent-then-sequential tasks: (1) add doc PR convention to CLAUDE.md, (2) create a lean doc-regeneration skill in canopy-skills, (3) run the skill in dry-run mode and review output. Tasks 1 and 2 are independent. Task 3 depends on Task 2.

**Tech Stack:** Markdown, canopy-skills SKILL.md format, `gh` CLI for GitHub data.

---

## Task 1: Add Doc PR Convention to CLAUDE.md and Phase 1 Plan

**Files:**
- Modify: `CLAUDE.md:68-77` (after PR Rules section)
- Modify: `CLAUDE.md:120-127` (AI Agent Guidelines section)
- Modify: `docs/plans/2026-03-07-phase1-core-port-plan.md:126-189` (porting process)

**Step 1: Add "Doc PR Rules" section to CLAUDE.md after "PR Rules"**

Insert after line 77 (after the PR Rules section, before Issue Closure Rules):

```markdown
## Doc PR Rules

- **Doc changes get their own PRs** — changes to CLAUDE.md, `docs/plans/`, and `docs/learnings/` must not be mixed into code PRs
- **Branch naming**: `docs/<short-description>`
- **Self-merge after CI**: Doc PRs can be merged by the agent without human review, unless they change architectural decisions
- **During a wave**: Note learnings in the code PR description, then create a separate doc PR after the code PR merges
```

**Step 2: Add guideline to AI Agent Guidelines in CLAUDE.md**

Add a new bullet to the AI Agent Guidelines section (after line 126):

```markdown
- Never mix documentation changes into code branches — use separate doc PRs (see Doc PR Rules)
```

**Step 3: Add Step 7 to Porting Process in Phase 1 plan**

In `docs/plans/2026-03-07-phase1-core-port-plan.md`, after "Step 6: Commit and Create PR" (line 183-189), add:

```markdown
**Step 7: Create doc PR for learnings**

After the code PR is merged, create a separate PR for any documentation updates:
- New learnings discovered during the wave → `docs/learnings/`
- CLAUDE.md status table updates
- Phase plan corrections or clarifications

Branch: `docs/wave-N-learnings`, target: `main`. See CLAUDE.md "Doc PR Rules".
```

**Step 4: Add `docs/.dry-run/` to .gitignore**

Append to `.gitignore`:

```
docs/.dry-run/
```

**Step 5: Verify changes read correctly**

Read CLAUDE.md and confirm:
- "Doc PR Rules" section appears between "PR Rules" and "Issue Closure Rules"
- AI Agent Guidelines has the new bullet
- .gitignore has the dry-run exclusion

**Step 6: Commit**

```bash
git add CLAUDE.md docs/plans/2026-03-07-phase1-core-port-plan.md .gitignore
git commit -m "docs: add doc PR convention and dry-run gitignore"
```

---

## Task 2: Create Lean Doc Regeneration Skill

**Files:**
- Create: `~/.claude/plugins/marketplaces/canopy-skills/plugins/canopy/skills/doc-regeneration/SKILL.md`

**Step 1: Create the skill directory**

```bash
mkdir -p ~/.claude/plugins/marketplaces/canopy-skills/plugins/canopy/skills/doc-regeneration
```

**Step 2: Write SKILL.md**

Create `~/.claude/plugins/marketplaces/canopy-skills/plugins/canopy/skills/doc-regeneration/SKILL.md` with the following content:

```markdown
---
name: doc-regeneration
description: Audit project documentation for staleness and coverage gaps, then regenerate CLAUDE.md and learnings. Use when docs may be out of sync with actual project state.
version: 0.1.0
---

# Doc Regeneration — Lean v1

## Purpose

Audit CLAUDE.md and docs/ against the actual project state (GitHub issues, PRs, learnings), then produce a corrected version. The goal is ensuring an AI agent starting a new wave has accurate, complete context — no stale status, no missing learnings, no confusing contradictions.

## Modes

**Dry-run (default):** Generate all output into `docs/.dry-run/`. Nothing is committed or branched. Review the output and decide what to apply.

**Apply:** Create a `docs/regen-YYYY-MM-DD` branch, write the regenerated files, and create a PR.

To select mode, the invoker specifies `--dry-run` or `--apply` when calling the skill. Default is `--dry-run`.

## Process

### Phase 1: Read Everything (no output yet)

Read these inputs. Do NOT produce any output until all inputs are gathered:

1. **CLAUDE.md** — read the full file
2. **docs/learnings/** — read every file, note each learning's key takeaway
3. **docs/plans/** — read headers and status sections only (skip large plan bodies like Phase 0's 2000+ lines — just read the first 50 lines for context)
4. **GitHub issues** — run `gh issue list --state all --limit 50` to get current state
5. **GitHub PRs** — run `gh pr list --state all --limit 50` to get merged/open PRs

### Phase 2: Analyze (two checks)

**Check 1 — Staleness:**
Compare CLAUDE.md's status table against GitHub issue states. For each wave:
- Is the status in CLAUDE.md correct? (Open vs Done, issue number, file count)
- Does the PR link match?
- Are any completed waves still marked as Open?

**Check 2 — Coverage:**
For each learning in `docs/learnings/`:
- Is the learning's key takeaway reflected somewhere in CLAUDE.md? (Checklist item, guideline, key doc reference, etc.)
- If not, what section of CLAUDE.md should it be added to?

Also check:
- Are there patterns from merged PRs or closed issues that should be learnings but aren't?
- Are any docs/plans referenced in CLAUDE.md that don't exist or are obsolete?

### Phase 3: Produce Output

Generate these files:

**`review-report.md`** — The core deliverable. Contains:
1. **Staleness findings** — table of what's wrong in the status table, with corrections
2. **Coverage findings** — table of each learning and whether it's reflected in CLAUDE.md
3. **Opinionated assessment** — "If I were an agent starting Wave N today, here's what would confuse me and what I'd need." Be specific and direct.
4. **Recommended changes** — bullet list of what the regenerated CLAUDE.md changes

**`CLAUDE.md`** — The regenerated version. Rules:
- Preserve the existing structure and section order exactly
- Update the status table to match GitHub reality
- Add missing learning references to Key Docs or relevant sections
- Do NOT add new sections unless a learning explicitly calls for one
- Do NOT remove or rewrite content that is already correct
- Mark completed/obsolete plans appropriately in Key Docs

**`learnings/<name>.md`** (only if gaps found) — New learning docs for patterns discovered in PRs/issues that aren't captured yet. Use the existing learning format:
```
# Learning: <Title>

**Date**: YYYY-MM-DD
**Context**: <where this came from>
**Status**: <Resolved/Active>

## Problem
<what went wrong or was discovered>

## Root Cause
<why>

## Fix / Key Takeaway
<what to do differently>
```

### Phase 4: Deliver

**If dry-run (default):**
1. Create `docs/.dry-run/` directory
2. Write `docs/.dry-run/review-report.md`
3. Write `docs/.dry-run/CLAUDE.md`
4. Write any new learnings to `docs/.dry-run/learnings/`
5. Present the review report to the user
6. Suggest: "Review `docs/.dry-run/` and compare against current docs. When ready, re-run with `--apply` to create a PR."

**If apply:**
1. Create branch `docs/regen-YYYY-MM-DD`
2. Write regenerated CLAUDE.md to repo root
3. Write any new learnings to `docs/learnings/`
4. Commit with message `docs: regenerate documentation (staleness + coverage fixes)`
5. Create PR targeting `main`

## Key Principles

- **Read before write.** Gather ALL inputs before producing ANY output.
- **Preserve structure.** CLAUDE.md's section order is intentional. Don't reorganize.
- **Be opinionated.** The assessment should say what would confuse a new agent, not just list facts.
- **Minimal changes.** Only change what's wrong or missing. Don't rewrite correct content.
- **Evidence-based.** Every finding must cite the source (issue number, learning filename, PR number).
```

**Step 3: Verify skill loads**

```bash
ls -la ~/.claude/plugins/marketplaces/canopy-skills/plugins/canopy/skills/doc-regeneration/SKILL.md
```

Confirm the file exists and has content.

**Step 4: Commit the skill to canopy-skills**

```bash
cd ~/.claude/plugins/marketplaces/canopy-skills
git add plugins/canopy/skills/doc-regeneration/SKILL.md
git commit -m "feat: add doc-regeneration skill (lean v1)"
```

---

## Task 3: Run Dry-Run and Review

**Depends on:** Task 2

**Step 1: Invoke the doc-regeneration skill in dry-run mode**

From the commcare-ios repo, invoke the doc-regeneration skill. It will:
1. Read CLAUDE.md, all learnings, plan headers, GitHub issues, GitHub PRs
2. Run staleness and coverage checks
3. Generate output to `docs/.dry-run/`

**Step 2: Review the dry-run output**

Read `docs/.dry-run/review-report.md` and evaluate:
- Are the staleness findings accurate?
- Are the coverage findings accurate?
- Is the opinionated assessment useful — would it actually help a Wave 4 agent?

Read `docs/.dry-run/CLAUDE.md` and compare key sections against current CLAUDE.md:
- Status table corrections
- Key Docs additions
- Any new guidelines

**Step 3: Discuss with user**

Present findings and get feedback before applying.

---

## Execution Order

Tasks 1 and 2 are independent — run in parallel.
Task 3 depends on Task 2.

```
┌──────┬─────────────────────────────┬───────────────┬────────────┐
│ Step │         What                │     Where     │ Depends On │
├──────┼─────────────────────────────┼───────────────┼────────────┤
│ 1    │ Doc PR convention           │ commcare-ios  │ —          │
├──────┼─────────────────────────────┼───────────────┼────────────┤
│ 2    │ Doc-regen skill (lean v1)   │ canopy-skills │ —          │
├──────┼─────────────────────────────┼───────────────┼────────────┤
│ 3    │ Dry-run + review            │ commcare-ios  │ Step 2     │
└──────┴─────────────────────────────┴───────────────┴────────────┘
```
