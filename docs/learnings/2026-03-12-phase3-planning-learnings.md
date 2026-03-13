# Learning: Phase 3 Planning — Feature Parity Requires Systematic Audit

**Date**: 2026-03-12
**Context**: Planning Phase 3 (Feature Implementation) for CommCare cross-platform app
**Status**: Active

## Problem

The original design doc estimated Phase 3 at 55-85 tasks across 10 feature groups. When we began planning, we initially proposed a wave breakdown based on those estimates. The user correctly pointed out that we can't guess at features — we need to be 100% sure of feature parity with the current Android app.

## Root Cause

The design doc's Phase 3 feature list was written at a high level before the project started. It missed:

- **30+ form widget types** (including Ethiopian/Nepali calendars, combobox with fuzzy search, signature, document upload)
- **13+ external app integrations** via Android Intents (Simprints biometrics, ABHA, RDT, NFC)
- **CommCare Connect** — an entire sub-application (jobs, learning, delivery, payments, messaging)
- **PersonalID** — identity verification system (phone, biometric, photo)
- **Graphing** — D3/C3 charts in case details
- **Content Provider API** — external apps querying case/fixture data
- **Report modules** — server-defined reports on mobile
- **Remote case search and claim** — server-side ElasticSearch queries

The real scope is 90-125+ tasks — roughly double the original estimate.

## Fix / Key Takeaway

**Never derive a feature list from a design doc alone.** For feature parity projects, the spec must come from:

1. **Codebase audit of the target app** — crawl all Activities, Fragments, widgets, services, receivers, and manifest declarations. Use `gh api repos/{owner}/{repo}/git/trees/{branch}?recursive=1` to get the full file tree without cloning.
2. **Documentation audit** — cross-reference official user docs for features that may be configured server-side rather than visible in code.
3. **Both sources together** — code shows what's implemented; docs show what users expect. Neither alone is sufficient.

The resulting inventory becomes the **definitive parity checklist** — no feature ships without a corresponding item, no item is added without evidence.

## Additional Learnings

### Local repo can silently fall behind remote

The local `main` was 106 commits behind `origin/main`. CLAUDE.md on disk was frozen at Wave 3 of Phase 1 while the remote had completed all 8 phases. Always run `git pull` before starting any planning or analysis session. An agent that reads stale CLAUDE.md will make decisions based on wrong project state.

### Design doc phasing doesn't map 1:1 to execution

The design doc defined 5 phases (0-4). Actual execution used 8 phases (1-8) that covered the design doc's Phases 0-2. The mapping:
- Design Phase 0 (Scaffold) → Execution Phase 0
- Design Phase 1 (Core Port) → Execution Phases 1-7
- Design Phase 2 (App Shell) → Execution Phase 8
- Design Phase 3 (Feature Implementation) → Not started
- Design Phase 4 (Polish) → Not started

This is fine — plans should adapt. But the design doc should be updated to reflect the actual phase numbering, or future agents will be confused by the mismatch.

### Skipped Phase 0 deliverables propagate as gaps

Phase 0 called for an oracle test harness (FormPlayer comparison framework) that was never built. This gap propagated — Phase 3's exit criteria requires "all oracle tests pass" but the infrastructure doesn't exist. Skipping "nice to have" infrastructure in early phases creates hard blockers in later phases. The oracle harness is now a Tier 1 task in Phase 3.

### The app module was iOS-only despite the design doc

The design doc says "a single Kotlin codebase produces both the Android and iOS apps," but `app/build.gradle.kts` only configured iOS targets. This gap should have been caught during Phase 8 (iOS App Implementation) — the Phase Transition Checklist should verify that architectural invariants from the design doc are maintained. Adding a "verify design doc invariants" step to the checklist would prevent this.

### Android-only patterns need cross-platform alternatives

Android Intent callouts (13+ integrations) have no direct iOS equivalent. These need to be mapped to iOS alternatives:
- Android Intents → iOS URL schemes / Universal Links / SDK integrations
- Content Providers → iOS App Groups (or drop)
- WorkManager → BGTaskScheduler
- FCM push → APNs
- Android print framework → iOS UIPrintInteractionController

This mapping should be done once and documented, not rediscovered per-feature.
