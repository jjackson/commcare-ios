# Learning: Introduce HQ Integration Tests at MVP, Not Polish

**Date:** 2026-03-17
**Phase:** Phase 4 (Polish)
**Category:** Testing strategy

## What Happened

The HQ integration test harness was created in Phase 4 Wave 1 (Correctness Scorecard). It contains HTTP endpoint checks against CommCare HQ but has never been run with real credentials. The tests verify individual HTTP calls, not full app engine round-trips (login, sync, form fill, submit, verify on server).

Meanwhile, the app has had login, sync, form entry, and submission wired up since Phase 3 Tier 1 — three full tiers and one phase ago.

## Why It Was Wrong

Our test coverage through Phases 1-3 relied on:
- **Unit tests** — verify individual components in isolation
- **Oracle tests** — compare our serializer output to the JVM reference implementation
- **Cross-platform tests** — verify commonMain code runs identically on JVM and iOS

These validate correctness of individual components but not the **full mobile-to-server contract**. None of them exercise:
- Auth flow against real HQ (token exchange, session management)
- Restore/sync parsing of production payloads
- Form submission XML format as HQ expects it
- Sync protocol sequencing (initial sync, incremental sync, purge)

Any of these could have drifted from what HQ expects without detection until manual testing.

## What Should Have Been Done

At Phase 3 Tier 1 (MVP), when the app first had end-to-end functionality, we should have added:

1. **A dedicated test app on CommCare HQ** — a simple app with one module, one form, a few question types, and a case list
2. **A CI job (nightly or weekly)** that runs the full round trip:
   - Log in with test credentials (stored as CI secrets)
   - Perform initial sync, verify case list populated
   - Fill and submit a form
   - Re-sync and verify the submission appears
3. **Incremental expansion** — as Tier 2 and Tier 3 added features (case management, multimedia, lookups), add corresponding forms to the test app

This would have cost a few hours to set up and would have caught contract mismatches immediately rather than at the end of the project.

## The Cost of Doing It Late

- **Debugging is harder.** When a full-stack integration bug surfaces now, the entire stack (auth, sync, form engine, serialization, submission) is complete. The bug could be anywhere. If caught at Tier 1, only auth+sync+basic forms existed — far fewer places to look.
- **Confidence gap.** We have 800+ JVM tests and 100+ cross-platform tests, but zero evidence that the app works against the real server. All correctness claims are relative to the oracle, not to HQ.
- **Late surprises.** If form submission format is wrong, fixing it now could cascade through serialization, form engine, and UI layers that were built assuming the current format is correct.

## Lesson

Integration tests against the real backend should be introduced at the earliest point where end-to-end functionality exists — not deferred to a polish/validation phase. Unit tests and oracle tests verify internal consistency; only real server round-trips verify external correctness.

**Rule of thumb:** If you can manually test a flow against the server, you should have a CI job testing it too. The manual test proves it's possible; the CI job proves it stays possible.
