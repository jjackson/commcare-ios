# Phase Split vs Extend: When a Blocked Tail Should Become a New Phase

**Date:** 2026-04-08
**Trigger:** Phase 8 (Production Readiness) had 8 of 11 code tasks shipped. The remaining 3 (Connect live integration tests + CI workflow) were blocked on creating real test users in production. A separate initiative (Phase 9: E2E UI testing) was being designed to solve that exact problem. Decision: close Phase 8, relocate the blocked tail to Phase 9 Wave 0, rather than keep Phase 8 open indefinitely or duplicate the solution across both phases.

## The situation

Phase 8's original plan had four parallel streams:
1. Integration tests (mock + live)
2. App Store submission
3. Performance benchmarks
4. Hardening (thread safety, crash upload, edge cases)

Streams 2, 3, and 4 landed cleanly. Stream 1's mock tests also landed. But Stream 1's live tests (Tasks 2, 3, 4) required an authenticated Connect user in production, and there was no sanctioned way to create one — creating real users for automated testing was blocked by SMS OTP delivery.

During Phase 8 execution, we discovered that connect-id has a `TEST_NUMBER_PREFIX = "+7426"` that suppresses SMS for test phone numbers and a `/users/generate_manual_otp` endpoint that returns the OTP token via OAuth2 client credentials. This was exactly the missing piece.

Simultaneously, the team realized that UI-level E2E test coverage was near-zero (2 passing Maestro flows, no Connect ID, no marketplace, no forms), and that the same `+7426` infrastructure would unblock UI testing too.

Three options appeared:

**(A) Extend Phase 8** — just finish Tasks 2-4 inside Phase 8 using the `+7426` unlock. Phase 8 stays "in progress" until done. UI testing is a separate later effort.

**(B) Split out a new Phase 9** for all E2E UI testing, leaving Phase 8 Tasks 2-4 to languish inside Phase 8 forever.

**(C) Close Phase 8 now, relocate Tasks 2-4 to Phase 9 Wave 0** — Phase 9 builds the `+7426` infrastructure once, and both the API-level live tests (formerly Phase 8) and the UI-level flows (new) share that infrastructure.

We chose (C).

## The decision heuristic

A phase should be split (or its blocked tail relocated to a successor) when **all four** of the following hold:

1. **A majority of the original phase's tasks have shipped.** If fewer than ~70% of tasks are done, keep the phase open and push to finish. Splitting a half-done phase fragments accountability.

2. **The remaining tasks share a blocker with a new, distinct initiative.** The signal isn't "these tasks are hard" — it's "these tasks and the new work both depend on the same missing capability, and building that capability twice would waste effort."

3. **The new initiative has enough scope to justify its own phase.** A one-PR cleanup doesn't deserve a phase number. Phase 9 is 11 waves of work; that's a phase. If the new initiative is small, absorb the blocked tail into a different existing phase or a standalone task.

4. **Keeping the old phase open has a real cost.** Either it clouds status reporting ("Phase 8 is still in progress" when 8/11 tasks have been done for weeks), or it creates a dead branch that onboarding agents have to understand and ignore, or it blocks a phase transition checklist that has meaningful downstream work.

If any of those four are not true, don't split. Just finish the phase as planned.

## Why (A) and (B) were wrong

**(A) Extend Phase 8:** would have required Phase 8 to duplicate the credential plumbing, fixture user, and CI workflow that Phase 9 was already going to build. The resulting Phase 8 would carry API-level live tests but no shared helpers, so Phase 9 would have to re-implement all the same plumbing a week later. This violates DRY at the phase level.

**(B) Leave Tasks 2-4 in a stale Phase 8:** would have made Phase 8 an indefinitely-open phase. Every agent starting a session would have to read Phase 8's plan, realize Tasks 2-4 were still open, try to make progress, hit the same blocker, and give up. The open phase becomes a trap.

**(C) Close + relocate** lets Phase 8 have a clean completion report with a clear audit of what shipped and what moved. Phase 9 inherits three ready-to-use test files as a bonus; they land during Wave 0 alongside the other infrastructure for near-zero marginal cost.

## What to write in the completion report

A relocation decision needs explicit documentation. The Phase 8 completion report includes:

1. A clear table of which tasks shipped and which relocated.
2. The reason for relocation (shared infrastructure with Phase 9).
3. A forward pointer to where the relocated tasks now live (Phase 9 Wave 0 §5.1).
4. The acceptance criteria table — with the relocated tasks marked "relocated" rather than "failed" or "skipped." This keeps the audit trail honest.
5. A note in the "notable technical decisions" section explaining the split-vs-extend choice.

## What to write in the successor phase's spec

The Phase 9 spec explicitly names the relocation:

> "API-level live Connect tests (relocated from Phase 8 Tasks 2-4)"

This gives future readers one pointer to find the story. They don't have to reconstruct "why does Phase 9 Wave 0 contain API-level tests alongside UI infrastructure?" — the spec answers that question inline.

## Anti-patterns to avoid

- **Silent relocation.** Moving tasks to a new phase without a completion report for the old phase leaves a gap in the history. Someone looking at "which phases are complete" sees Phase 8 frozen in time.
- **Double-counting.** Don't leave the relocated tasks listed as "in progress" in the old phase AND "in scope" in the new phase. One canonical location, forward-referenced from the other.
- **Phase bloat as avoidance.** Don't extend a phase just to avoid the paperwork of closing it. Closing a phase with a completion report is cheaper than carrying around an open phase for weeks.
- **Infinite phases.** If a phase has been open for more than ~4 weeks and a majority of its tasks are done, audit the tail. Either finish it this week or split it.

## Link to CLAUDE.md phase transition checklist

CLAUDE.md documents a "Phase Transition Checklist" that agents must follow when closing a phase. This learning extends that checklist with a specific case: what to do when the tail is blocked but the rest of the phase is complete.

The checklist now implicitly includes a sub-rule:

> If all but a few tasks of a phase are complete, and the remaining tasks depend on infrastructure that a new initiative is already planning to build, relocate those tasks to the new initiative's first wave. Write the completion report, update CLAUDE.md, and forward-reference the relocation target. Do not leave the phase open.

Agents encountering this pattern should invoke this heuristic, not re-litigate the decision.
