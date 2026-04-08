# Phase 8: Production Readiness — Completion Report

**Date:** 2026-04-08
**Duration:** ~2 weeks (plan to close)
**PRs:** #372-#381 (10 PRs, all merged to `main`)
**Plan doc:** `docs/plans/2026-03-24-phase8-production-readiness-plan.md`
**Status:** COMPLETE — 8 of 11 original code tasks shipped. Tasks 2-4 (Connect live integration tests + CI workflow) relocated to Phase 9 Wave 0, which shares the infrastructure they require.

## Summary

Phase 8 was the "bring CommCare iOS from TestFlight to App-Store-ready" phase. Four parallel streams: expanded integration tests, App Store submission prep, performance profiling, and hardening (thread safety, crash upload, edge cases).

The hardening and performance streams landed cleanly. The mock-based Connect API tests landed (Task 1). The App Store stream landed code-side (metadata, Info.plist, encryption compliance); remaining App Store work is manual operational work outside the code plan (privacy labels in App Store Connect, iPad screenshot, actual submission).

The live Connect integration test tasks (2, 3, 4) were blocked on creating real test users in production — a capability we did not have when the Phase 8 plan was written. During Phase 8 execution we learned that connect-id has a `TEST_NUMBER_PREFIX = "+7426"` that bypasses SMS delivery for test phone numbers and a `/users/generate_manual_otp` endpoint that retrieves OTPs for those numbers via OAuth2 client credentials. Together these unblock real-backend testing. Because this same infrastructure is the foundation Phase 9 (E2E UI Testing) needs, Tasks 2-4 naturally fold into Phase 9 Wave 0 rather than requiring a duplicate build-out inside Phase 8.

## What Shipped

### Stream 1: Integration Tests (1 of 4 tasks)

| Task | PR | Description |
|---|---|---|
| 1 — Connect API mock tests | #373 | `ConnectApiRequestTest.kt` + `ConnectTestConfig.kt`. MockHttpClient-based tests for request URL, header, JSON parsing, error handling. No credentials required. |
| 2 — ConnectID live integration tests | — | **Relocated to Phase 9 Wave 0.** |
| 3 — Marketplace + Messaging live integration tests | — | **Relocated to Phase 9 Wave 0.** |
| 4 — CI workflow for Connect live tests | — | **Relocated to Phase 9 Wave 0.** |

### Stream 2: App Store Submission (all tasks shipped)

| Task | PR | Description |
|---|---|---|
| 5 — Submission checklist (doc) | #378 | `docs/plans/app-store-submission-checklist.md` — full end-to-end App Store Connect flow |
| 5b — Metadata files | #379 | `app/iosApp/metadata/en-US/` — description, keywords, subtitle, privacy URL |
| 6 — Info.plist audit + encryption compliance | #379, #380 | Privacy descriptions, `ITSAppUsesNonExemptEncryption`, `ITSEncryptionExportComplianceCode`, iosArm64 PBKDF2 build fix |

Remaining App Store work (manual operational steps, not part of the code plan):
- Privacy labels in App Store Connect dashboard
- iPad screenshot capture and upload
- Actual submission action

### Stream 3: Performance (both tasks shipped)

| Task | PR | Description |
|---|---|---|
| 7 — Restore + case list benchmarks | #377 | `RestoreBenchmark.kt`, `CaseListBenchmark.kt` — baselines captured |
| 8 — Crypto + form entry benchmarks | #377 | `CryptoBenchmark.kt`, `FormEntryBenchmark.kt` — AES and PBKDF2 timing, form load/serialize |

### Stream 4: Hardening (all tasks shipped)

| Task | PR | Description |
|---|---|---|
| 9 — Thread safety audit + tests | #374 | `SyncViewModel`, `MessagingViewModel` synchronized, `ThreadSafetyTest.kt` concurrent access tests |
| 10 — Crash report upload | #376 | `PlatformCrashUploader.kt`, integrated into sync flow, `CrashUploaderTest.kt` |
| 11 — Edge case hardening | #375 | `EdgeCaseTest.kt` — empty forms, double submit, invalid indices, cleared sessions |

### Bonus work (not in the original plan but landed during Phase 8)

| PR | Description |
|---|---|
| #381 | Maestro CI smoke test infrastructure — restore and form golden tests, the foundation Phase 9 builds on |

## Acceptance Criteria Verification

| Criterion | Status | Evidence |
|---|---|---|
| `ConnectApiRequestTest` passes without creds | ✅ | #373 green CI |
| App Store metadata files exist at `app/iosApp/metadata/en-US/` | ✅ | #379 |
| `xcodebuild build` succeeds with updated Info.plist | ✅ | #379, #380 |
| Performance baselines recorded | ✅ | #377 — benchmark outputs in PR |
| `ThreadSafetyTest` passes | ✅ | #374 |
| `CrashUploaderTest` passes | ✅ | #376 |
| `EdgeCaseTest` passes | ✅ | #375 |
| `ConnectIdIntegrationTest` passes or skips | ❌ (relocated) | Deferred to Phase 9 Wave 0 |
| `ConnectMarketplaceIntegrationTest` passes or skips | ❌ (relocated) | Deferred to Phase 9 Wave 0 |
| `ConnectMessagingIntegrationTest` passes or skips | ❌ (relocated) | Deferred to Phase 9 Wave 0 |
| CI workflow parses without YAML errors (hq-integration.yml) | ❌ (relocated) | Deferred to Phase 9 Wave 0 |

**8 of 11 criteria met. 3 relocated to Phase 9 Wave 0.**

## Notable Technical Decisions

1. **Mock-based Connect API tests first, live tests deferred.** Task 1 deliberately exercises request construction and JSON parsing without hitting real servers, because (a) it has the highest bug-catch density per unit of work, and (b) it does not create a hard external dependency on Connect creds. The live-test tasks were always going to be the slower-to-land half of Stream 1.

2. **`ITSAppUsesNonExemptEncryption = YES` + compliance code.** Phase 7 Wave 1 added real AES encryption, which triggers App Store encryption export compliance requirements. The compliance code is committed in Info.plist as part of #380 and does not require per-build manual attestation through App Store Connect.

3. **PBKDF2 build fix for iosArm64.** The iOS implementation used `CCKeyDerivationPBKDF` which is available on simulator (x86_64, arm64) but required additional linkage setup for device (arm64). Fix landed in #380 alongside encryption compliance.

4. **Crash upload is synchronous with sync.** `PlatformCrashUploader.uploadPendingReports()` is called at the start of `SyncViewModel.sync()` rather than as a background scheduled task. Rationale: crash reports are bounded in size, upload failures should not block sync (non-fatal), and a second scheduler would add complexity for no user-visible benefit.

5. **Thread safety focused on ViewModels with coroutine-main boundary crossing.** Audit identified `SyncViewModel`, `MessagingViewModel`, and `FormQueueViewModel` as the three candidates. `FormQueueViewModel` was already synchronized pre-Phase 8; the other two received guards in #374. Other ViewModels use Compose `mutableStateOf` which is single-thread-safe and did not need additional synchronization.

## The Phase 8 → Phase 9 split decision

During Phase 8 execution, two observations changed the planning calculus:

1. **Connect live integration tests require a real test user in production**, which required either (a) a staging connect-id (does not exist) or (b) a way to create users in prod without SMS delivery (did not exist at plan time).
2. **The Maestro smoke test infrastructure that landed in #381** revealed how thin UI-level test coverage actually was — only two Maestro flows passing, no Connect ID, no marketplace, no form entry, no multi-app exercised through the UI.

Both problems turn out to share a single solution: the `+7426` magic prefix + `/users/generate_manual_otp` endpoint + a single manually-created fixture user. That solution deserves enough scaffolding (OAuth2 creds plumbing, OTP fetch helper, nightly CI workflow, Maestro subflows) that stuffing it inside "Phase 8 Tasks 2-4" would misrepresent its scope.

**Decision:** Phase 9 carries the full E2E UI testing initiative (11 waves, see `docs/superpowers/specs/2026-04-08-phase9-e2e-ui-testing-design.md`). Phase 9 Wave 0 absorbs Phase 8 Tasks 2-4 as the "API-level live tests" layer of its infrastructure build-out — same creds, same fixture user, same CI workflow, just adds `ConnectIdIntegrationTest` / `ConnectMarketplaceIntegrationTest` / `ConnectMessagingIntegrationTest` alongside the Maestro infrastructure.

This means Phase 8 can close cleanly, Phase 9 inherits the work it was going to need anyway, and no work is duplicated.

A dedicated learning doc captures the general heuristic: `docs/learnings/2026-04-08-phase-split-vs-extend-decision.md`.

## Metrics

- **New test files:** 5 (ConnectApiRequestTest, ThreadSafetyTest, CrashUploaderTest, EdgeCaseTest, CrashReporterTest)
- **New benchmark files:** 4 (Restore, CaseList, Crypto, FormEntry)
- **App Store metadata files:** 4
- **New platform files:** 1 (PlatformCrashUploader)
- **Modified ViewModels for thread safety:** 2 (Sync, Messaging)
- **Relocated tasks:** 3 (to Phase 9 Wave 0)

## Follow-Up Work

1. **Phase 9 Wave 0** (tracked in Phase 9 plan doc) — picks up the Connect live integration tests. Creates the fixture user, gets OAuth2 credentials from Dimagi, wires the OTP fetch helper, adds the three relocated test files, adds the CI workflow.
2. **Manual App Store submission steps** — privacy labels, iPad screenshot, submission action. Not code work.
3. **Phase 9 Waves 1-11** — the UI-level E2E testing initiative proper.
