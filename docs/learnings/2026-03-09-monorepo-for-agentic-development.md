# Learning: Monorepo Structure for Agentic Development

**Date**: 2026-03-09
**Context**: Restructuring commcare-ios and commcare-core into a single repository
**Status**: Active

## Problem

commcare-core (the engine being ported from Java to Kotlin) and commcare-ios (pipeline, plans, learnings, CLAUDE.md) were separate repositories. This caused a recurring problem: **AI agents lost context every session.**

When an agent worked on commcare-core, it couldn't see:
- `CLAUDE.md` (conversion checklist, PR rules, issue closure rules)
- `docs/learnings/` (conversion pitfalls, degenerify decision, PR discipline)
- `docs/plans/` (phase1 plan with wave details)

Each session started cold. Agents repeated mistakes that were already documented in learnings. The worktree isolation that Claude Code uses made this worse — a worktree only covers one repo.

This is the **third time** we restructured because AI agents need all context in one place:
1. PRs — agents didn't create them because the plan didn't say to
2. Issue closure — agents wrote terse closures because the plan didn't specify evidence requirements
3. Repo structure — agents couldn't read learnings because they were in a different repo

## Decision

**Merge commcare-core into commcare-ios as a `commcare-core/` subdirectory using `git subtree`.**

```bash
# Bring commcare-core into commcare-ios
git subtree add --prefix=commcare-core <url> kotlin-port --squash
```

Now every agent session — regardless of which worktree — has access to CLAUDE.md, all learnings, all plans, AND the source code being converted.

## Recovery Plan for Upstream

When the Kotlin port is ready to upstream to `dimagi/commcare-core`:

```bash
# Extract commcare-core's history into standalone commits
git subtree split --prefix=commcare-core -b upstream-ready

# Push to a clean fork of dimagi/commcare-core
git push <dimagi-fork> upstream-ready:kotlin-port
```

`git subtree split` reconstructs the commit history for just the `commcare-core/` subdirectory, stripping the prefix. The resulting commits can be PR'd against `dimagi/commcare-core` as if they were developed there originally.

## Why Not Submodules?

Git submodules would maintain the repo boundary but make the problem worse for agents:
- Submodules aren't automatically cloned in worktrees
- They add complexity to every git operation
- They don't solve the "agent can't see CLAUDE.md" problem since the submodule is a separate repo

## Key Takeaway

**Optimize repo structure for the primary development workflow.** Right now, that's agentic AI coding. Agents need all context in one directory tree. The cost of a subtree merge is low (one-time setup, clean extraction later). The cost of agents losing context every session is high (repeated mistakes, wasted iterations, debugging problems already solved).

When the primary workflow shifts to human development or upstream contribution, we extract and restructure. But during the agentic development phase, a monorepo is the right call.
