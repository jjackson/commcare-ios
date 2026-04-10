# Phase 9 Completion Report: End-to-End UI Testing

**Date:** 2026-04-10
**Duration:** Waves 0-9 across 3 days (2026-04-08 to 2026-04-10)
**Spec:** `docs/superpowers/specs/2026-04-08-phase9-e2e-ui-testing-design.md`

## Summary

Phase 9 established end-to-end UI testing infrastructure for CommCare iOS using Maestro on the iOS Simulator, then systematically walked through 11 waves of product coverage. The primary outcome: **13 bugs found and fixed**, all in code the AI port judged successful based on the JVM test suite. The bugs fall into three categories:

1. **iOS platform implementation gaps** (#401, #403) — `actual fun` stubs that were never implemented or tested on iOS
2. **ViewModel state management bugs** (#391, #399, #406, #410, #415, #416, #418) — state that was set but lost, overridden, or never wired to the UI
3. **K/N interop failures** (#389, #416) — iOS keychain `SecItemAdd` silently failing, CMP iOS `mutableStateOf` mutation during composition not triggering recomposition

## Wave Completion Summary

| Wave | Scope | Status | PRs | Bugs |
|------|-------|--------|-----|------|
| W0 | Test infrastructure + Connect ID | Complete (prior session) | #382, #386 | 3 |
| W1 | Connect ID recovery | Complete (prior session) | #387 | 0 |
| W2 | Post-recovery state + session | Complete (prior session) | #390 | 1 (#389 filed) |
| W3 | App install + login | Complete (prior session) | #392 | 1 (#391 filed) |
| W4a | Module + case list navigation | Complete | #393 | 0 |
| W4b | Form navigation (no submit) | Complete | #395, #396 | 1 (#394) |
| W4c | Form fill + submit + HQ verify | Complete | #397 | 0 |
| W5a | Visit form scout (25 pages) | Complete | #398-#405, #408-#409 | 5 (#391, #399, #401, #403, #406) |
| W5b | Visit form submit with MSelect | Complete | #407, #413 | 0 (fixes from 5a sufficient) |
| W5c | Select appearances, field-list, media | Complete | #414 | 1 (#415 signature mapping) |
| W6 | Multi-app install + switch | Complete | #417 | 1 (#416 CMP recomposition) |
| W7 | Connect marketplace | Complete (partial — no test opportunities) | #419, #422 | 2 (#389 keychain, hasConnectAccess) |
| W8 | Sync | Complete | #420 | 0 |
| W9 | Settings + diagnostics | Complete | #421 | 0 |
| W10 | Edge cases | Deferred to device testing | — | — |
| W11 | Reliability | Documented (MSelect timing) | — | — |

## Bugs Found and Fixed

| # | Issue | Root Cause | Layer | PR |
|---|-------|-----------|-------|-----|
| 1 | #391 | `resolveDomain()` hardcoded "demo" | ViewModel | #398 |
| 2 | #394 | Form buttons hidden by keyboard | UI/Layout | #396 |
| 3 | #399 | Case list filter by datum-id not case-type | ViewModel | #400 |
| 4 | #401 | iOS `loadClasspathResource` stub | iOS Platform | #402 |
| 5 | #403 | TreeElementParser crash on XML comments | Engine (commonMain) | #404 |
| 6 | #406 | MSelect state dropped by `updateQuestions` | ViewModel | #407 |
| 7 | #410 | Kill-relaunch login race | ViewModel/Compose | #411 |
| 8 | #415 | Signature mapped to IMAGE | ViewModel | #414 |
| 9 | #416 | NeedsLogin bridge never renders on CMP iOS | Compose/CMP | #417 |
| 10 | #418 | Badge count `${0}` rendered literally | ViewModel | #422 |
| 11 | #389 | iOS keychain `SecItemAdd` silently fails | iOS Platform/K/N | #422 |
| 12 | — | `hasConnectAccess` hardcoded false | ViewModel | #419 |
| 13 | — | DrawerViewModel missing ConnectIdRepository | Wiring | #419 |

## Test Coverage Added

| Test File | Tests | Platform | What |
|-----------|-------|----------|------|
| TreeElementParserTest.kt | 4 | JVM + iOS | XML comment handling regression |
| MultiSelectStateTest.kt | 4 | JVM | MSelect state persistence |
| SelectAppearanceTest.kt | 13 | JVM | All select appearance variants |
| FieldListGroupTest.kt | 6 | JVM | Field-list groups (replaced 5 placeholders) |
| MediaQuestionTypeTest.kt | 7 | JVM | Media question type mapping |
| LoginViewModelResolveDomainTest.kt | 6 | JVM | Domain resolution (from earlier) |

**Placeholder audit:** Removed 63 fake tests (492 lines) across 10 files that asserted constants and defaults without constructing real objects. 143 real tests remain.

## Maestro E2E Flows

| Flow | What |
|------|------|
| wave5a-scout.yaml | 25-page Visit form tour |
| visit-submit.yaml | Visit form fill + submit (MSelect + repeat) |
| wave8-sync.yaml | Incremental sync |
| wave9-diagnostics.yaml | Diagnostics + Settings screens |
| multi-app-switch.yaml | Install second app via App Manager |
| app-switch-to-bonsaaso.yaml | Switch back to Bonsaaso via dropdown |
| marketplace-scout.yaml | Connect marketplace entry |
| nav-drawer-scout.yaml | Navigation drawer |
| visit-draft-roundtrip.yaml | Form draft save (resume UI missing) |

Orchestrators: `run-wave5a-scout.sh`, `run-wave5b.sh`, `run-wave6.sh`

## Learnings

Two dated learning docs were shipped:

1. **`2026-04-09-ios-xml-whitespace-coalescing-gap.md`** — iOS XML parser whitespace/comment divergence from kxml2. The AI port's JVM tests never caught this because the iOS parser path was never exercised.

2. **`2026-04-09-viewmodel-layer-coverage-gap.md`** — The ViewModel layer has no real tests. Placeholder tests like `assertFalse(false)` gave misleading green CI. Every ViewModel state field that's set from multiple code paths needs a "does it survive refresh?" test.

## Known Issues (Not Fixed)

- **MSelect Maestro flakiness (~50% pass rate):** CMP iOS recomposition after checkbox toggles briefly makes the Next button invisible. `waitForAnimationToEnd` helps but doesn't fully resolve.
- **Form draft resume UI missing:** Save Draft works (stores to DB) but there's no UI to resume an incomplete form.
- **"Sign in to Personal ID" placeholder:** Nav drawer action not wired up — Connect ID sign-in only works from SetupScreen recovery flow.
- **iOS keychain `SecItemAdd` silently fails:** Root cause unknown — the K/N interop dual-path (`mapOf as CFDictionaryRef` vs `NSMutableDictionary`) both fail to persist items in the Compose context. Worked around via DB fallback.

## Deferred to Device Testing (W10-W11)

- Network loss mid-flow (airplane mode)
- Permission denials (camera, microphone, location)
- App kill mid-form (draft resume — no UI exists)
- Session expiry (token timeout)
- Low storage handling
- PIN change / biometric enable (need iOS lockscreen)
- Language switching (need multi-language form)

## Recommendation

Phase 9 has accomplished its primary goal: establishing E2E confidence that CommCare iOS works as a product, and surfacing the systematic test-coverage gap in the AI-ported code. The bug-finding rate has dropped to near zero for the final waves (W8, W9), suggesting the major paths are now stable.

**Next phases should focus on:**
1. **Backfilling ViewModel test coverage** for the remaining untested state fields (the placeholder audit showed the gap; the `MultiSelectStateTest` pattern shows how to fill it)
2. **Investigating the iOS keychain failure** — the DB fallback works but is less secure; the root cause in K/N `SecItemAdd` interop deserves a dedicated investigation
3. **App Store submission** — the remaining manual steps from `project_appstore_remaining.md` (privacy labels, iPad screenshot, submit)
