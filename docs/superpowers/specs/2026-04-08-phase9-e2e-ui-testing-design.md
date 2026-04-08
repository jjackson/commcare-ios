# Phase 9: End-to-End UI Testing — Design Spec

**Date:** 2026-04-08
**Status:** Draft, pending user review
**Predecessor:** Phase 8 (Production Readiness) — ongoing, unaffected by this spec
**Owner:** Phase 9 test infrastructure

## 1. Motivation

Phases 1-7 built the product. Phase 8 hardens it at the API and unit-test level. Phase 9 closes the missing leg: **UI-level end-to-end tests that exercise the real product against real backends.**

Three facts motivate this:

1. **We have deep unit coverage and shallow UI coverage.** 280+ JVM tests, 84+ KMP common tests, and 129+ commcare-core engine tests give us strong confidence in individual components. But only **two Maestro flows pass today** (`login-and-home.yaml`, `sync-and-verify.yaml`), and both use a hardcoded HQ user — no Connect ID flow, no marketplace, no form entry, no multi-app, no sync edge cases are exercised through the UI.
2. **The integration-testing-at-MVP lesson applies directly.** `docs/learnings/2026-03-17-integration-testing-timing-learnings.md` documents the cost of deferring real-backend integration tests: *"Debugging is harder (entire stack could be culprit). Confidence gap (800+ tests but zero proof against real server). Late surprises."* Phase 9 pays that debt.
3. **The `+7426` magic prefix unblocks Connect ID testing in prod.** Until now, we could not programmatically create Connect ID users in production because OTP delivery required a real SMS. Connect-id's `TEST_NUMBER_PREFIX = "+7426"` (users/const.py) suppresses the SMS send but still generates the OTP token in the database. The `GET /users/generate_manual_otp` endpoint (OAuth2 client-credential-protected) returns that token directly. Together, these let a test harness drive the real Connect ID registration and recovery UI end-to-end without human-in-the-loop.

Phase 9 does **not** replace oracle tests, commonTest, or Phase 8 API-level integration work. It complements them.

## 2. Goals and non-goals

### Goals

- Establish a reliable, repeatable E2E UI testing framework that runs against **real production backends** (connectid.dimagi.com, CommCare HQ prod) from CI.
- Cover every major user-facing feature area by the end of the phase: Connect ID, CommCare app management, case list, form entry, sync, marketplace, multi-app, settings.
- Enforce the `iosTest/` platform-test discipline from `docs/learnings/2026-03-18-ios-platform-test-gap-learnings.md`: every new platform implementation exercised by an E2E flow gets a paired iOS unit test first.
- Produce an observable, measurable test signal: pass rate, runtime, flakiness rate per flow, tracked over time.
- Keep test hooks **out of production app code**. The `+7426` bypass is entirely server-side; the client always walks the normal user journey.

### Non-goals

- **Replacing unit tests or oracle tests.** Those are faster, more focused, and should remain the primary safety net for component-level regressions.
- **Running E2E on every PR.** Phase 9 E2E runs nightly and on-demand, not on every PR. A slow flaky test should never block an unrelated code change.
- **iOS device-lab testing.** Simulator-only in this phase. Real device coverage (needed for full biometric, camera, and hardware-integration paths) is a later consideration.
- **Registration flow as Wave 1.** See §3 — registration is deferred pending a user-cleanup story.
- **Staging/dev backend testing.** We test against prod. There is no staging connect-id. The single fixture user lives in prod.

## 3. Strategic approach

### 3.1 Phase shape

Phase 9 is a distinct phase following Phase 8. It follows the project's standard phase structure:
- Plan doc in `docs/plans/`
- One GitHub issue per wave
- One PR per wave
- Wave exit criteria gate the next wave
- Learnings captured in `docs/learnings/` as discoveries happen

### 3.2 Single-fixture-user model

We use **one** real Connect ID user in production, referred to throughout this doc as the **fixture user**. It is created manually, once, before Wave 0 completes. Every E2E flow that needs a logged-in Connect user re-uses it.

Consequences:
- **Zero test-user pollution in production.** Only one phone number and one user exist.
- **Wave 1 cannot be the registration flow**, because registration creates a new user and would either fail (phone already taken) or require cleanup infrastructure that doesn't exist. Wave 1 is instead the **recovery flow** — "existing user, new device" — which is naturally idempotent: on every fresh simulator, it walks through phone → OTP (fetched) → backup code → credentials restored.
- Registration flow testing is deferred to a later wave, contingent on either (a) a connect-id user-delete endpoint from Dimagi, or (b) explicit acceptance of one additional test user per run.
- Subsequent waves inherit the fixture user's state (installed apps, credentials, PIN) unless the test explicitly erases the simulator first.

### 3.3 Maestro, not XCUITest

Maestro is already set up in this repo (`.maestro/flows/`, CI wired via `ios-build.yml`). It uses Compose `testTag` for element lookup, which the Connect ID registration and login screens already have thoroughly annotated (77 testTags across 17 commonMain files; every step of `PersonalIdScreen` tagged). Sticking with Maestro preserves the existing investment and keeps the infrastructure uniform.

### 3.4 OTP fetch via `generate_manual_otp`

The fulcrum of Phase 9. Maestro's `runScript` step calls a shell helper that:
1. Reads `CONNECTID_E2E_CLIENT_ID` / `CONNECTID_E2E_CLIENT_SECRET` from environment.
2. Exchanges them for an access token via `POST /o/token/` (client_credentials grant).
3. Calls `GET /users/generate_manual_otp?phone_number=<fixture>` with that token.
4. Returns the OTP as a Maestro output variable via `outputs.otp`.
5. Maestro types the OTP into the `otp_field` testTag.

Why this is safe:
- No test code in the production app.
- No persistent test bypass — every OTP is generated on demand and consumed immediately.
- The endpoint is already protected by `ClientProtectedResourceAuth`; compromising the client credentials does not bypass any user-facing authentication.
- The `+7426` prefix is a server-side contract that already exists and is tested in connect-id's own suite.

## 4. Wave structure — 11 waves

All waves are breadth-first: one area per wave, one PR per wave, shipped and stabilized before the next starts. The breadth-first choice is deliberate — the highest-value question today is "does the product work end-to-end across every feature" — and breadth-first answers that question sooner than depth-first.

| Wave | Area | Depends on |
|---|---|---|
| W0 | Test infrastructure foundation | Dimagi: OAuth2 client creds + pre-invited fixture number |
| W1 | Connect ID **recovery** flow (idempotent with fixture user) | W0 |
| W2 | Login variants: PIN set, PIN login, biometric enrollment, session restore | W1 |
| W3 | CommCare app install (URL, list, update detection) — subsumes the WIP `hq-round-trip.yaml` | W1 |
| W4 | Case list → form entry → submit → HQ verification — subsumes the WIP `form-entry-navigation.yaml` | W3 |
| W5 | Form features deep dive (select appearances, repeats, media, drafts, chaining) | W4 |
| W6 | Multi-app management + sandbox isolation | W3 |
| W7 | Connect marketplace (opportunities, claim, learning, delivery, payment, messaging) | W1, W3 |
| W8 | Sync edge cases (incremental, update apply, rollback, offline-online, heartbeat) | W4 |
| W9 | Settings, profile, diagnostics, language, practice mode | W1 |
| W10 | Negative / edge cases (network loss, permission denial, corrupted form, session expiry) | W1–W9 |
| W11 | Reliability layer (flakiness tracking, retry policy, runtime dashboards, failure patterns) | W1–W10 |

The two WIP Maestro flows documented in `.maestro/config.yaml` (`hq-round-trip.yaml` and `form-entry-navigation.yaml`) are not a separate wave — their debugging and stabilization happen inside W3 and W4 respectively, where their scope naturally belongs.

Registration flow E2E is intentionally absent from this list. It is tracked as an **open question** (§9) and added in a follow-up wave once the cleanup story exists.

## 5. Wave 0 — infrastructure (blocks all other waves)

Wave 0 is the unglamorous foundation that determines whether the rest of Phase 9 is fast or slow. No user-visible deliverable; every item is plumbing.

### 5.1 Deliverables

**Fixture user and pool documentation**
- `docs/phase9/fixture-user.md` — documents the single fixture phone number, the backup code, the test opportunity it belongs to, and **instructions to Dimagi** for pre-inviting the number to a test opportunity (see §5.2).
- The fixture user is created manually (via the iOS app or curl against connect-id prod) before Wave 0 completes. Its backup code and phone number are committed to source only in encrypted form, or stored exclusively as GitHub Actions secrets and documented inline in `fixture-user.md` with placeholders.

**OTP fetch helper**
- `.maestro/scripts/fetch-otp.sh` — ~40-line bash script that:
  - Reads `CONNECTID_E2E_CLIENT_ID`, `CONNECTID_E2E_CLIENT_SECRET`, `CONNECTID_E2E_PHONE` from env
  - POSTs `/o/token/` for an access token
  - GETs `/users/generate_manual_otp?phone_number=<fixture>`
  - Prints `output=otp:<value>` (Maestro output format)
- Invoked from Maestro flows via `runScript: scripts/fetch-otp.sh`.
- Also callable directly from a developer shell for local debugging.

**Secrets plumbing**
- GitHub Actions secrets: `CONNECTID_E2E_CLIENT_ID`, `CONNECTID_E2E_CLIENT_SECRET`, `CONNECTID_E2E_PHONE`, `CONNECTID_E2E_BACKUP_CODE`.
- Local dev: `.env.e2e.local` (gitignored) with the same vars. Pre-commit hook refuses commits containing `+7426`, common credential patterns, or `.env.e2e.local`.

**CI workflow**
- `.github/workflows/e2e-ui.yml` — new workflow, runs on `macos-15`.
  - Triggers: `workflow_dispatch` (manual), `schedule: '0 6 * * *'` (daily at 06:00 UTC).
  - Does not run on PRs initially.
  - Builds the iOS framework (`./gradlew :commcare-core:linkDebugFrameworkIosSimulatorArm64`), builds the app via xcodebuild, installs to a freshly-erased simulator (`xcrun simctl erase all`), runs the current wave's Maestro flows, uploads screenshots/video on failure as GitHub artifacts.

**Shared Maestro subflows**
- `.maestro/subflows/fetch-otp.yaml` — wraps the shell helper, exposes `${otp}` output.
- `.maestro/subflows/recover-connect-id.yaml` — the shared "log in as the fixture user on a fresh device" subflow used by every downstream wave.

**Test identifier audit**
- Grep all screens in scope for Waves 1-11 and verify every interactive element has a `testTag`. Fill gaps. Document any screen missing tags as a Wave 0 blocker. The recent exploration found 77 tags across 17 files; Connect ID and login paths look solid, but app install, case list, and form entry need verification.

**iOS platform test discipline**
- Policy document `docs/phase9/ios-platform-test-policy.md`: any new iOS platform code (iosMain/) touched during Phase 9 gets a unit test in `iosTest/` before the E2E flow depending on it can land. This codifies the lesson from `docs/learnings/2026-03-18-ios-platform-test-gap-learnings.md`.

### 5.2 Dimagi dependencies

Wave 0 has two hard external dependencies. The plan doc for W0 must open these as external asks before any code is written.

1. **OAuth2 client credentials** for `connectid.dimagi.com`. Scope: whatever `ClientProtectedResourceAuth` requires for the `generate_manual_otp` endpoint. Stored as GitHub Actions secrets.
2. **Pre-invite the fixture number to a test opportunity.** The number we will use is chosen during Wave 0 (a single `+7426xxxxxxx`). It must be added to an existing or new test opportunity on Connect Worker so `check_number_for_existing_invites` returns `True`. This is required because `send_session_otp` and `confirm_session_otp` both return 403 NOT_ALLOWED if `invited_user=False` (see `users/views.py:955,968` in connect-id).

The instructions-to-Dimagi document is a Wave 0 deliverable. Draft text:

> We are building iOS E2E tests against connectid.dimagi.com prod. We need:
> 1. OAuth2 client credentials with scope sufficient to call `GET /users/generate_manual_otp`. Please confirm the scope name.
> 2. One `+7426` phone number (we'll pick one and share it) pre-invited to a test opportunity on Connect Worker. This is required so `send_session_otp`/`confirm_session_otp` return 200 for our test runs.
> 3. Confirmation that creating one fixture user under this number is acceptable and does not need to be cleaned up regularly.

### 5.3 Wave 0 exit criteria

1. `bash .maestro/scripts/fetch-otp.sh` returns a valid OTP string for the fixture number from a local dev machine with secrets in `.env.e2e.local`.
2. The same script runs from the nightly CI workflow with GitHub Actions secrets wired in.
3. The fixture user exists in connect-id prod, is pre-invited, and has documented backup code + test opportunity association.
4. `.maestro/flows/` has at least one hello-world flow that invokes `fetch-otp.sh` via `runScript`, receives the OTP, and echoes it — proving the plumbing works end-to-end before any real test is written.
5. `e2e-ui.yml` runs that hello-world flow green on a nightly schedule.

## 6. Wave 1 — Connect ID recovery flow

The minimal first real test. Proves Wave 0 infrastructure and exercises actual product code.

### 6.1 Scope

On a fresh simulator (clean keychain, no installed apps), the fixture user walks through:
1. SetupScreen → tap "LOGIN WITH PERSONALID" (`signup_link`)
2. PhoneEntryStep → enter fixture phone number → tap Continue (`continue_button`)
3. Server returns `accountExists=true`, app routes to recovery branch
4. OTP screen → Maestro calls `fetch-otp.sh` via `runScript` → types OTP into `otp_field` → tap Verify (`verify_button`)
5. BackupCodeStep → Maestro types fixture backup code into `backup_code_field` → tap Continue (`continue_button`)
6. Recovery succeeds, credentials stored in keychain
7. App routes to SuccessStep (or the post-recovery home screen, whichever the iOS implementation uses)
8. Flow asserts the success element is visible (`done_button` or home screen `testTag`)

### 6.2 New files

- `.maestro/flows/connect-id-recovery.yaml` — the flow itself, ~60 lines.
- `.maestro/subflows/fetch-otp.yaml` — already created in Wave 0, consumed here.
- No app code changes.

### 6.3 Out of scope for Wave 1

- Registration flow (deferred, §9)
- PIN setup after recovery (Wave 2)
- Biometric enrollment (Wave 2)
- App install (Wave 3)
- Any HQ / CommCare backend interaction (Wave 4+)
- Photo capture during recovery

### 6.4 Wave 1 exit criteria

1. `connect-id-recovery.yaml` passes 10 consecutive nightly runs in CI.
2. Same flow passes from a developer's local machine via `maestro test .maestro/flows/connect-id-recovery.yaml`.
3. Failure diagnostics (screenshots, logs) are captured as GitHub artifacts on any failed run.
4. No test-only code lives in `app/src/commonMain` or `app/src/iosMain`.
5. Any iOS platform code touched during debugging is paired with an `iosTest/` unit test before merge (per §5.1 policy).

## 7. Waves 2-11 summaries

These sketches are deliberately thin — each wave gets its own detailed plan doc in `docs/plans/` before implementation. The goal here is to name scope and dependencies, not design each wave.

### W2 — Login variants
After recovery (Wave 1), the app has credentials in the keychain. Wave 2 exercises:
- Set a PIN post-recovery
- Logout, log back in via PIN
- Enable biometric, log back in via simulated Face ID
- Saved-session restore (kill app, relaunch, skip re-auth)

### W3 — CommCare app install
Install a test CommCare app (known HQ app, controlled by test setup) via:
- Direct URL entry
- Install from HQ list after login
- Update detection and apply

QR install is deferred to a later sub-wave if simulator QR injection proves painful. **This wave subsumes the WIP `.maestro/flows/hq-round-trip.yaml`** — its debugging and stabilization happen here as part of landing a full install flow against the fixture user.

### W4 — Case list → form entry → submit
The "does the product actually work" test. Walks:
- Install → sync (OTA restore) → case list populated → pick a case → open form → fill simple questions → save → submit → verify submission appeared on HQ via a follow-up API check.

**This wave subsumes the WIP `.maestro/flows/form-entry-navigation.yaml`** — its debugging and stabilization happen here.

### W5 — Form features deep dive
All question types; select appearances (`minimal`, `compact-N`, `quick`, `combobox`, `label`, `list-nolabel`); repeat groups; field-list groups; collapsible groups; media (image, signature, video, audio, document); form drafts; form chaining; swipe navigation; alternative calendars; language switching mid-form. Split across multiple flows per category — do not put everything in one file.

### W6 — Multi-app management
Install two apps, switch between them via drawer and login dropdown, verify sandbox isolation (cases in app A invisible from app B), archive/uninstall.

### W7 — Connect marketplace
Opportunities list → detail → claim → auto app install → learning module start and complete → delivery form submit → payment confirmation → messaging send/receive. This is the highest-screen-count wave.

### W8 — Sync edge cases
Incremental sync (no changes, 412 path); incremental sync with changes; staged update with rollback; offline → online resume; heartbeat-triggered force update.

### W9 — Settings, profile, diagnostics
PIN change; biometric enable/disable; language switch; practice mode isolation; profile update; diagnostic screen; app version display.

### W10 — Negative / edge cases
Network loss mid-flow; app kill mid-form (form draft resume); permission denials (camera, biometric, location); corrupted form quarantine; session expiry; low-storage handling.

### W11 — Reliability layer
This wave is meta. Deliverables:
- Per-flow flakiness tracking: count of failed-then-passed runs per flow over a rolling 14-day window.
- Per-flow runtime tracking.
- Retry policy: Maestro supports built-in retries; define which flows qualify.
- Dashboard: a simple markdown or HTML report published from the nightly job summarizing flow status and trends.
- Failure-pattern analysis: categorize failures (infrastructure, product bug, test bug, flake).

## 8. Cross-cutting concerns

### 8.1 Test identifier policy

- Every interactive element in `commonMain/` UI gets a `testTag` modifier.
- Naming convention: `snake_case`, descriptive of the element's purpose, stable (do not change for visual refactors).
- Policy enforced by a custom lint pass added during Wave 0 or Wave 2 (open question: is Detekt/ktlint custom-rule overhead worth it, or manual code-review the right enforcement?).

### 8.2 iOS platform test pairing

Copied from §5.1 because it bears repeating: any iOS platform code touched in pursuit of an E2E flow gets a paired unit test in `iosTest/` before the E2E flow lands. This is a hard rule, not a soft suggestion.

### 8.3 Flakiness budget

- A flow is "stable" after 10 consecutive passing nightly runs.
- An unstable flow **does not gate** the next wave from starting — but the team cannot start a new wave with more than two unstable flows in the tail.
- Flakiness > 20% (1 in 5 runs fails for non-product reasons) causes the flow to be marked quarantine, skipped from the nightly run, and added to the Wave 11 backlog.

### 8.4 Test data lifecycle

- Exactly one Connect ID fixture user exists across the entire Phase 9 lifetime.
- Any test that would create additional users (e.g., a future registration-flow test) must include cleanup or explicit "this wave uses N disposable users" in its plan doc.
- Forms submitted by E2E tests accumulate on HQ. This is acceptable for Phase 9 and not cleaned up. If accumulation becomes a problem, Wave 11 can add a cleanup step.
- HQ test apps: we use a single known test app on HQ, documented in `docs/phase9/fixture-user.md`. Not deleted.

### 8.5 Secrets management

- All credentials live in GitHub Actions secrets for CI and `.env.e2e.local` for dev.
- Pre-commit hook rejects `.env.e2e.local`, any occurrence of `+7426` in source files outside of documented fixture files, and common credential patterns (private keys, OAuth secrets).
- No credentials in plan docs, learnings, or spec files. Reference them by env-var name only.

### 8.6 CI cost and cadence

- Phase 9 E2E runs on macos-15 minutes (expensive). The nightly schedule is the default because it balances signal freshness against cost.
- `workflow_dispatch` allows on-demand runs from a branch for anyone debugging.
- The existing `ios-build.yml` smoke test continues to run on every PR unchanged — Phase 9 does not add per-PR cost.
- Cost ceiling: if nightly runs consume more than 90 minutes of macos time, split flows across multiple jobs running in parallel.

## 9. Open questions

1. **Registration flow testing.** When and how? Options:
   - Ask Dimagi for a user-delete endpoint (cleanest, requires Dimagi work).
   - Accept one additional user per registration-test run (adds ~N users/month to prod).
   - Rotate through a fixed small pool of phone numbers, deleting-then-recreating manually.
   - Skip registration-flow automation entirely and keep it as a manual test before release.
   - **Decision: deferred. Tracked as the first open question for the first post-Wave-1 planning session.**

2. **Scope of `ClientProtectedResourceAuth` for `generate_manual_otp`.** The endpoint code does not declare a `required_scopes` attribute. Need Dimagi to confirm whether any scope suffices or a specific one is needed.

3. **Biometric on simulator.** iOS simulator supports simulated Face ID enrollment. Does Compose Multiplatform's biometric integration respect this? To be verified during Wave 2.

4. **Custom lint for test identifiers.** Worth the setup cost, or rely on code review? Revisit during Wave 2.

5. **HQ test-app stability.** The CommCare test app we use in Wave 3+ must be stable and controlled. Who owns it and where does it live? Document before Wave 3.

## 10. Risks and mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Dimagi delays issuing OAuth2 creds or pre-inviting the fixture number | High | Blocks W0-W11 | Open the ask on day one of Wave 0. Wave 0 is purely blocked on this. Do not start implementation without it. |
| Maestro flakiness on CMP/iOS (already documented for keyboard, frame duration) | High | Slows wave cadence | Wave 11 is explicitly about reliability. Each wave's exit criteria include 10 consecutive green runs. Quarantine policy prevents flaky flows from blocking new work. |
| Simulator state bleed between flows | Medium | False failures | `xcrun simctl erase all` at the start of every flow in CI. Local dev documented to do the same. |
| The fixture user gets into a bad state (e.g., locked account, backup code attempts exhausted) | Medium | Blocks every flow using recovery | Backup code has a `MAX_BACKUP_CODE_ATTEMPTS` counter (`users/const.py:33`). If hit, manually reset via Dimagi. Document the recovery procedure in `docs/phase9/fixture-user.md`. Use `reset_failed_backup_code_attempts` path or manual admin intervention. |
| Test credentials leak | Low | Compromises fixture user + OAuth client | Pre-commit hook blocks them from source. Rotate via Dimagi if it happens. No broader blast radius because the client has no user-impersonation scope. |
| HQ backend changes break a passing flow | Medium | Test bug but looks like product bug | Distinguish in Wave 11 failure-pattern analysis. Maintain a log of "HQ-side change broke flow" events. |
| iOS platform code changes break previously-green flows mid-wave | Medium | Wasted wave time | The iosTest-before-E2E policy (§8.2) catches most of these earlier. |
| Scope creep — a wave tries to add "one more thing" | High | Wave duration doubles | Each wave has a written plan doc. Scope changes require an amendment, not a handshake. |
| Maestro gets abandoned or deprecated upstream | Low | Long-term infra risk | Not a Phase 9 concern. If it happens, migration to XCUITest is a separate effort. |

## 11. Success criteria for Phase 9

Phase 9 is complete when:

1. All 11 waves have shipped and passed their individual exit criteria.
2. The nightly `e2e-ui.yml` workflow runs green for 14 consecutive nights.
3. At least one real product bug has been caught by Phase 9 E2E tests and fixed (proving the tests have value beyond "they run").
4. No E2E flow is in quarantine at phase close.
5. A learnings doc in `docs/learnings/` captures what broke, what surprised us, and what to do differently next time.
6. Registration flow testing has a documented plan (even if not implemented) resolving open question §9.1.
7. CLAUDE.md is updated with Phase 9 completion status.

## 12. Phase 9 deliverables index

For quick reference, every artifact Phase 9 produces:

- `docs/plans/2026-04-08-phase9-e2e-ui-testing-plan.md` — the per-wave implementation plan, written via the writing-plans skill after this spec is approved.
- One GitHub issue per wave.
- `docs/phase9/fixture-user.md` — fixture user metadata, Dimagi invite instructions, backup-code-reset runbook.
- `docs/phase9/ios-platform-test-policy.md` — the iosTest-before-E2E policy.
- `.maestro/scripts/fetch-otp.sh` — OTP fetch helper.
- `.maestro/subflows/fetch-otp.yaml` — Maestro subflow wrapper.
- `.maestro/subflows/recover-connect-id.yaml` — recovery login subflow used by all downstream waves.
- `.maestro/flows/connect-id-recovery.yaml` — Wave 1 flow.
- `.maestro/flows/*.yaml` — one-or-more flows per wave.
- `.github/workflows/e2e-ui.yml` — new nightly CI workflow.
- `docs/learnings/2026-??-??-phase9-*.md` — learnings captured during the phase.

## 13. Explicit non-decisions

Things this spec deliberately does NOT decide, leaving them to per-wave plan docs:

- Exact selection of Maestro flow files per wave beyond W1.
- Which HQ test app to use for W4+ (requires W3 sub-decision).
- Whether Wave 11 reliability metrics are built in-repo or via an external service.
- The detailed format of per-flow screenshot artifacts.
- Whether to parallelize flows in CI or run serially (revisit during W11 if runtime becomes a problem).
- The exact failure-categorization taxonomy (infrastructure vs product vs test vs flake) — decided during W11.
