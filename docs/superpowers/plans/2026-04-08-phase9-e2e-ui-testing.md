# Phase 9: End-to-End UI Testing — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the E2E UI testing infrastructure (Wave 0) and the first real test flow against connect-id production (Wave 1: Connect ID recovery), proving the `+7426` magic-prefix infrastructure works end-to-end.

**Architecture:** A single fixture user (one `+7426*` phone number, manually created, pre-invited to a Connect Worker test opportunity) drives every E2E test. A bash helper script fetches OTPs from connect-id's `/users/generate_manual_otp` endpoint via OAuth2 client credentials. Maestro flows invoke the helper via `runScript`. A new nightly `e2e-ui.yml` GitHub Actions workflow runs the flows on a fresh simulator. Phase 8's deferred live Connect API tests (Tasks 2-4) land alongside the UI infrastructure since they share the same fixture user and credentials.

**Tech Stack:** Maestro (`.maestro/flows/`, `.maestro/subflows/`), bash + jq + curl, GitHub Actions (`macos-15`), Kotlin Multiplatform tests (JVM target), `xcodegen` + `xcodebuild`, `xcrun simctl`, gradle.

**Spec:** `docs/superpowers/specs/2026-04-08-phase9-e2e-ui-testing-design.md`

**Predecessor:** `docs/plans/2026-04-08-phase8-completion-report.md` (relocates Phase 8 Tasks 2-4 here)

**Scope of THIS plan:** Wave 0 (infrastructure + relocated Phase 8 tests) and Wave 1 (Connect ID recovery flow). Waves 2-11 are summarized in the spec §7 and will get their own per-wave plan docs before each one starts, per the project's phase convention.

---

## File Map

### Wave 0 — Infrastructure

| File | Action | Purpose |
|------|--------|---------|
| `docs/phase9/fixture-user.md` | Create | Manually-created fixture user metadata, Dimagi invite instructions, backup-code-reset runbook |
| `docs/phase9/ios-platform-test-policy.md` | Create | Codifies the iosTest-before-E2E rule from `2026-03-18-ios-platform-test-gap-learnings.md` |
| `.env.e2e.local.example` | Create | Template developers copy to `.env.e2e.local` (gitignored). Documents required env vars. |
| `.gitignore` | Modify | Add `.env.e2e.local` |
| `.maestro/scripts/fetch-otp.sh` | Create | Bash script: OAuth client-credentials → `generate_manual_otp` → print Maestro `output=otp:<value>` |
| `.maestro/subflows/fetch-otp.yaml` | Create | Maestro wrapper that runs `fetch-otp.sh` and exposes `${otp}` |
| `.maestro/flows/e2e-hello-world.yaml` | Create | Smallest possible flow: launch app, fetch an OTP, echo it. Proves the plumbing. |
| `.github/workflows/e2e-ui.yml` | Create | New nightly + manual workflow on `macos-15` |
| `app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectIdIntegrationTest.kt` | Create | Relocated Phase 8 Task 2: live ConnectID API tests |
| `app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectMarketplaceIntegrationTest.kt` | Create | Relocated Phase 8 Task 3a: live Marketplace API tests |
| `app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectMessagingIntegrationTest.kt` | Create | Relocated Phase 8 Task 3b: live Messaging API tests |
| `.github/workflows/hq-integration.yml` | Modify | Wire the three relocated tests into existing weekly workflow |

**Note on `ConnectTestConfig.kt`:** This file already exists in `app/src/jvmTest/kotlin/org/commcare/app/integration/` from Phase 8 Task 1 (PR #373). Reuse it; do not recreate.

### Wave 1 — Connect ID recovery flow

| File | Action | Purpose |
|------|--------|---------|
| `.maestro/subflows/recover-connect-id.yaml` | Create | Shared subflow: phone entry → OTP fetch → backup code → success. Used by Wave 1 and every later wave that needs an authenticated session. |
| `.maestro/flows/connect-id-recovery.yaml` | Create | Wave 1's actual test flow. Calls the recovery subflow against a fresh simulator and asserts success. |
| `.github/workflows/e2e-ui.yml` | Modify | Add `connect-id-recovery.yaml` to the nightly run list. |

### Required Dimagi-side prerequisites (NOT files in this repo)

- An `+74260000xxx` phone number we choose, **pre-invited to a test opportunity** in Connect Worker (so `check_number_for_existing_invites` returns `True` and `send_session_otp`/`confirm_session_otp` return 200).
- An **OAuth2 client_id + client_secret** for `connectid.dimagi.com` with scope sufficient to call `GET /users/generate_manual_otp`. The endpoint is protected by `ClientProtectedResourceAuth` (no explicit `required_scopes` in `users/views.py:1023`); confirm with Dimagi which client scope works.
- Manual creation of one fixture user (Connect ID account) under that phone number, capturing the chosen name, backup code, PIN, and recovery answers.

**These prerequisites block every Wave 0 task that touches a real backend. Open the Dimagi asks on day one of Wave 0.**

---

## Acceptance Criteria

| Wave | Test / verification | Pass condition |
|---|---|---|
| W0 (infra) | `bash .maestro/scripts/fetch-otp.sh` from a dev machine with `.env.e2e.local` populated | Prints `output=otp:<6-digit value>` |
| W0 (CI) | `e2e-ui.yml` triggered manually via `gh workflow run e2e-ui.yml` | Workflow finishes green; `e2e-hello-world.yaml` Maestro flow passes |
| W0 (relocated tests) | `cd app && ./gradlew jvmTest --tests "*ConnectIdIntegrationTest*" --tests "*ConnectMarketplaceIntegrationTest*" --tests "*ConnectMessagingIntegrationTest*"` | All pass with credentials, all skip cleanly without |
| W0 (CI for relocated) | `hq-integration.yml` workflow runs the three integration test classes when `CONNECT_ACCESS_TOKEN` secret is set | Workflow green; tests skip if secret absent |
| W1 | `~/.maestro/bin/maestro test .maestro/flows/connect-id-recovery.yaml` from local dev | Flow ends green: phone entered, OTP fetched and verified, backup code accepted, post-recovery screen visible |
| W1 (stability) | 10 consecutive nightly runs of `connect-id-recovery.yaml` in CI | All 10 green, zero retries |

---

## Wave 0 — Infrastructure

### Task 0.1: Open Dimagi asks (external dependency, no code)

This task has no commit. It's a hard prerequisite for everything downstream and must be opened before any other Wave 0 work.

- [ ] **Step 1: Pick the fixture phone number**

Choose one number of the form `+74260000NNN` (e.g., `+74260000001`). Document the choice locally; do not commit it to the repo yet — it will live in `.env.e2e.local` and `docs/phase9/fixture-user.md` (with a placeholder).

- [ ] **Step 2: File a request to Dimagi connect-id ops**

Send the following to the Dimagi connect-id team (Slack, email, or Jira ticket — whichever is conventional):

> Subject: Connect ID test infrastructure request — iOS E2E testing
>
> We are building iOS E2E tests against `connectid.dimagi.com` production. We need three things:
>
> 1. **OAuth2 client credentials** (client_id + client_secret) for `connectid.dimagi.com` with scope sufficient to call `GET /users/generate_manual_otp`. Please confirm whether any scope works or a specific scope name is required. The endpoint is at `users/views.py:1022` and is protected by `ClientProtectedResourceAuth`.
>
> 2. **Pre-invite one phone number to a test opportunity on Connect Worker.** The number is `+74260000NNN` (replace with the chosen number). It needs `check_number_for_existing_invites` to return `True` so that `send_session_otp` and `confirm_session_otp` return 200 instead of 403 NOT_ALLOWED (see `users/views.py:955,968`).
>
> 3. **Confirmation that creating one fixture user under this number is acceptable** and does not need periodic cleanup. We will manually register the user once and reuse it across all E2E tests.

- [ ] **Step 3: Block on the response**

Wait for Dimagi to respond with credentials and confirmation. Until they do, no further Wave 0 task can complete. Document the block in the corresponding GitHub issue with status `blocked: external dependency`.

---

### Task 0.2: Manually create the fixture user

Once Dimagi confirms the number is invited, create the user by hand using either the iOS app on a simulator or curl. Capture every value the test will need to replay later.

- [ ] **Step 1: Boot a clean simulator and install the latest CommCare iOS build**

```bash
xcrun simctl erase all
xcrun simctl boot $(xcrun simctl list devices available -j | jq -r '.devices | to_entries | map(select(.key | contains("iOS"))) | first | .value | map(select(.name | contains("iPhone"))) | first | .udid')
cd app && ./gradlew linkDebugFrameworkIosSimulatorArm64 --no-daemon
cd iosApp && xcodegen generate
xcodebuild build -project CommCare.xcodeproj -scheme CommCare -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -configuration Debug -derivedDataPath build CODE_SIGN_IDENTITY="-" CODE_SIGNING_REQUIRED=NO
xcrun simctl install booted build/Build/Products/Debug-iphonesimulator/CommCare.app
xcrun simctl launch booted org.marshellis.commcare.ios
```

- [ ] **Step 2: Walk through the registration flow on the simulator**

Tap "LOGIN WITH PERSONALID" → enter the fixture phone number → wait for the OTP screen.

- [ ] **Step 3: Fetch the OTP manually via curl**

Until the helper script exists, fetch the OTP by hand to confirm Dimagi's setup works. Use the OAuth client credentials Dimagi provided:

```bash
ACCESS_TOKEN=$(curl -s -X POST https://connectid.dimagi.com/o/token/ \
  -d "grant_type=client_credentials" \
  -d "client_id=$CONNECTID_E2E_CLIENT_ID" \
  -d "client_secret=$CONNECTID_E2E_CLIENT_SECRET" \
  | jq -r '.access_token')

curl -s "https://connectid.dimagi.com/users/generate_manual_otp?phone_number=$CONNECTID_E2E_PHONE" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  | jq -r '.otp'
```

Expected: a 6-digit OTP string. If you get an error, verify scope/credentials with Dimagi before proceeding.

- [ ] **Step 4: Complete the registration manually**

Type the OTP into the simulator. Continue through name entry (use a recognizable test name like "QA Test User"), backup code (capture this exactly — you'll need it for every subsequent recovery test), skip photo, set a PIN.

- [ ] **Step 5: Capture all fixture values**

Write these down in a secure location (1Password, secrets manager, NOT in the repo):
- Phone number: `+74260000NNN`
- Display name: `QA Test User`
- Backup code: `XXXXXXXX` (12 chars typically)
- PIN: `NNNN`
- Test opportunity ID: from Dimagi
- OAuth client_id: from Dimagi
- OAuth client_secret: from Dimagi

- [ ] **Step 6: Verify recovery works manually before automating**

Erase the simulator (`xcrun simctl erase all`), reinstall the app, and walk through the recovery path: tap "LOGIN WITH PERSONALID", enter the fixture phone, fetch a new OTP via curl, type it in, enter the backup code, confirm credentials are restored. If this manual recovery doesn't work, the automated Wave 1 flow won't either — fix it before writing any Maestro flow.

No commit for this task — it's pure manual setup.

---

### Task 0.3: Create the fixture user documentation

**Files:**
- Create: `docs/phase9/fixture-user.md`
- Create: `docs/phase9/` (directory)

- [ ] **Step 1: Write the fixture user doc**

Create `docs/phase9/fixture-user.md` with the following content (literal — copy verbatim, then fill in placeholders during deployment, but commit with placeholders only):

```markdown
# Phase 9 Fixture User

This file documents the single Connect ID fixture user that all Phase 9 E2E
tests use. The user was created manually once and is reused across runs.

## Identity

- **Phone number:** `+74260000NNN` (see `CONNECTID_E2E_PHONE` in CI secrets / `.env.e2e.local`)
- **Display name:** `QA Test User`
- **Test opportunity:** `<opportunity-id-from-dimagi>` on Connect Worker

## Why this user exists

Connect-id's `TEST_NUMBER_PREFIX = "+7426"` (`users/const.py`) suppresses SMS
delivery for matching phone numbers but still generates the OTP token in the
database. The `GET /users/generate_manual_otp` endpoint, protected by
OAuth2 client credentials, returns that token directly. Together these let
test automation drive Connect ID flows end-to-end without a real human
receiving an SMS.

The user is pre-invited to a test opportunity because
`send_session_otp`/`confirm_session_otp` return 403 NOT_ALLOWED unless
`check_number_for_existing_invites(phone_number)` returns True
(see `users/views.py:955` and `users/views.py:968` in connect-id).

## Secrets

The actual phone number, backup code, PIN, and OAuth credentials are
**never committed to this repository**. They live in:

- **GitHub Actions secrets** (for CI):
  - `CONNECTID_E2E_CLIENT_ID`
  - `CONNECTID_E2E_CLIENT_SECRET`
  - `CONNECTID_E2E_PHONE`
  - `CONNECTID_E2E_BACKUP_CODE`
  - `CONNECTID_E2E_PIN`
- **Local dev:** `.env.e2e.local` (gitignored). See `.env.e2e.local.example`.
- **Long-term storage:** the team's password manager.

## Adding new test phone numbers

Phase 9 deliberately uses one fixture user. Adding a second number requires
re-running Task 0.1 (Dimagi ask) and Task 0.2 (manual creation) and
documenting the new fixture here. Do not add numbers without a strong
reason — accumulating fixture users defeats the "one user, no pollution"
property.

## How to invite a new number to the test opportunity

Send a Dimagi ops request:

> Please invite phone number `+74260000NNN` to test opportunity
> `<opportunity-id>` on Connect Worker. This is for iOS E2E test
> automation; the number uses the `+7426` test prefix and will not
> receive real SMS.

## Backup-code-reset runbook

If `connect-id` returns repeated `INCORRECT_CODE` errors during the
recovery flow, the fixture user may have hit `MAX_BACKUP_CODE_ATTEMPTS`
(`users/const.py:33`), which is currently 3. Recovery procedure:

1. File a Dimagi ops request to reset `failed_backup_code_attempts` to 0
   on the fixture user's `ConnectUser` record. Connect-id's
   `reset_failed_backup_code_attempts` model method exists for this.
2. While waiting, all E2E tests using the fixture user will fail. Mark
   the affected nightly job as a known failure in `e2e-ui.yml`.
3. Once Dimagi confirms the reset, the backup code returns to working.
4. If this happens repeatedly, audit Phase 9 tests for code that triggers
   recovery failure paths and fix them — they should mock the failure,
   not actually exhaust the real fixture user.

## How to recreate the user from scratch

If the fixture user is somehow lost (account deactivated, deleted, etc.):

1. Confirm the phone number is still pre-invited to the test opportunity.
2. Re-run Task 0.2 of the Phase 9 plan (manual creation via simulator).
3. Update the GitHub secrets with the new backup code and PIN.
4. Update this doc with any changed values.
5. Run a Wave 1 nightly to verify everything still works.
```

- [ ] **Step 2: Commit**

```bash
mkdir -p docs/phase9
git add docs/phase9/fixture-user.md
git commit -m "docs(phase9): fixture user documentation and runbook"
```

---

### Task 0.4: Create the iOS platform test policy doc

**Files:**
- Create: `docs/phase9/ios-platform-test-policy.md`

- [ ] **Step 1: Write the policy**

```markdown
# Phase 9 iOS Platform Test Policy

## Rule

Any new iOS platform code (anything in `app/src/iosMain/` or
`commcare-core/src/iosMain/`) touched in pursuit of a Phase 9 E2E flow
gets a paired unit test in `iosTest/` **before** the E2E flow that
depends on it can land.

## Why

`docs/learnings/2026-03-18-ios-platform-test-gap-learnings.md` documents
hours of Maestro debugging spent finding a one-line bug in
`IosXmlParser.skipWhitespaceAndComments()`. A unit test would have caught
it instantly. Phase 9 E2E flows are slow, hard to debug, and have many
potential failure points. We do not use Maestro to discover bugs in iOS
platform code; we use unit tests. Maestro's job is to verify the wiring,
not debug the implementations.

## What counts as "platform code"

Anything implementing an `expect`/`actual` declaration on the iOS side.
Examples:
- `PlatformKeychainStore` (iosMain implementation)
- `PlatformBiometricAuth` (iosMain implementation)
- `PlatformBarcodeScanner` (iosMain implementation)
- `PlatformHttpClient` (iosMain implementation)
- Any new `Platform*` class added during a wave

NOT platform code (so this rule does not apply):
- Compose UI in `commonMain/`
- ViewModels in `commonMain/`
- Network clients in `commonMain/` (already pure Kotlin)
- Maestro flows themselves

## Enforcement

This is a code-review rule, not a CI rule. The reviewer of any wave PR
checks the diff for `iosMain/` changes and rejects the PR if there is no
matching `iosTest/` test. Future Phase 9 waves may add a custom Detekt
or ktlint check, but for now the discipline is human.

## Exceptions

If a wave needs to touch `iosMain/` code that is genuinely untestable in
isolation (e.g., requires real hardware or a real iCloud session), the
PR description must explain why and the reviewer must explicitly accept
the exception. Do not silently skip the rule.
```

- [ ] **Step 2: Commit**

```bash
git add docs/phase9/ios-platform-test-policy.md
git commit -m "docs(phase9): iOS platform test policy"
```

---

### Task 0.5: Create the .env.e2e.local template

**Files:**
- Create: `.env.e2e.local.example`
- Modify: `.gitignore`

- [ ] **Step 1: Verify .gitignore does not already cover .env.e2e.local**

```bash
grep -n "env.e2e" .gitignore || echo "not present, will add"
```

Expected: "not present, will add" (or it's already there — skip Step 2).

- [ ] **Step 2: Add `.env.e2e.local` to .gitignore**

Append this block to `.gitignore`:

```
# Phase 9 E2E test secrets — never committed
.env.e2e.local
```

- [ ] **Step 3: Create the template**

Create `.env.e2e.local.example` with:

```bash
# Phase 9 E2E test environment.
# Copy this file to .env.e2e.local (gitignored) and fill in real values.
# For CI, the same vars come from GitHub Actions secrets.
#
# See docs/phase9/fixture-user.md for what these are and how to obtain them.

# OAuth2 client credentials for connect-id /users/generate_manual_otp
CONNECTID_E2E_CLIENT_ID=
CONNECTID_E2E_CLIENT_SECRET=

# The single fixture phone number, including +country code prefix.
# Must start with +7426 to qualify for the test-prefix bypass.
CONNECTID_E2E_PHONE=

# Fixture user's backup code (captured during manual registration in Task 0.2).
CONNECTID_E2E_BACKUP_CODE=

# Fixture user's PIN (captured during manual registration in Task 0.2).
CONNECTID_E2E_PIN=

# connect-id base URL (override only for staging, which doesn't currently exist)
CONNECTID_E2E_BASE_URL=https://connectid.dimagi.com
```

- [ ] **Step 4: Verify the example file does not contain any real values**

```bash
grep -E "[0-9]{4,}|client_id_[a-z]" .env.e2e.local.example
```

Expected: no matches.

- [ ] **Step 5: Commit**

```bash
git add .gitignore .env.e2e.local.example
git commit -m "chore(phase9): .env.e2e.local template + gitignore"
```

---

### Task 0.6: Create the OTP fetch shell script

**Files:**
- Create: `.maestro/scripts/fetch-otp.sh`

- [ ] **Step 1: Write the script**

```bash
#!/usr/bin/env bash
#
# Phase 9 E2E: fetch a one-time OTP for the fixture user.
#
# Calls /users/generate_manual_otp on connect-id (OAuth2 client credentials)
# and prints the OTP in Maestro's `runScript` output format so a Maestro
# flow can read it via ${output.otp}.
#
# Usage from Maestro:
#   - runScript:
#       file: scripts/fetch-otp.sh
#
# Usage from a developer shell (loads .env.e2e.local first):
#   set -a; source .env.e2e.local; set +a
#   bash .maestro/scripts/fetch-otp.sh
#
# Required environment variables:
#   CONNECTID_E2E_CLIENT_ID
#   CONNECTID_E2E_CLIENT_SECRET
#   CONNECTID_E2E_PHONE
#   CONNECTID_E2E_BASE_URL (defaults to https://connectid.dimagi.com)

set -euo pipefail

BASE_URL="${CONNECTID_E2E_BASE_URL:-https://connectid.dimagi.com}"

if [ -z "${CONNECTID_E2E_CLIENT_ID:-}" ] || [ -z "${CONNECTID_E2E_CLIENT_SECRET:-}" ]; then
  echo "ERROR: CONNECTID_E2E_CLIENT_ID and CONNECTID_E2E_CLIENT_SECRET must be set" >&2
  exit 1
fi

if [ -z "${CONNECTID_E2E_PHONE:-}" ]; then
  echo "ERROR: CONNECTID_E2E_PHONE must be set" >&2
  exit 1
fi

# 1. Exchange client credentials for an access token.
TOKEN_RESPONSE=$(curl -sS -X POST "$BASE_URL/o/token/" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=$CONNECTID_E2E_CLIENT_ID" \
  -d "client_secret=$CONNECTID_E2E_CLIENT_SECRET")

ACCESS_TOKEN=$(printf '%s' "$TOKEN_RESPONSE" | jq -r '.access_token // empty')

if [ -z "$ACCESS_TOKEN" ]; then
  echo "ERROR: failed to obtain access token" >&2
  echo "Response: $TOKEN_RESPONSE" >&2
  exit 2
fi

# 2. Fetch the OTP for the fixture phone number.
# URL-encode the leading + so curl does not interpret it as a space.
ENCODED_PHONE=$(printf '%s' "$CONNECTID_E2E_PHONE" | sed 's/^+/%2B/')

OTP_RESPONSE=$(curl -sS "$BASE_URL/users/generate_manual_otp?phone_number=$ENCODED_PHONE" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

OTP=$(printf '%s' "$OTP_RESPONSE" | jq -r '.otp // empty')

if [ -z "$OTP" ]; then
  echo "ERROR: failed to obtain OTP" >&2
  echo "Response: $OTP_RESPONSE" >&2
  exit 3
fi

# 3. Emit Maestro `output=key:value` so a flow can consume `${output.otp}`.
echo "output=otp:$OTP"
```

- [ ] **Step 2: Make it executable**

```bash
mkdir -p .maestro/scripts
chmod +x .maestro/scripts/fetch-otp.sh
```

- [ ] **Step 3: Lint with shellcheck**

```bash
brew install shellcheck 2>/dev/null || true
shellcheck .maestro/scripts/fetch-otp.sh
```

Expected: zero warnings. If shellcheck is not available, skip — but run it before merging.

- [ ] **Step 4: Test the script locally (requires `.env.e2e.local` populated and Dimagi setup complete)**

```bash
set -a; source .env.e2e.local; set +a
bash .maestro/scripts/fetch-otp.sh
```

Expected output: `output=otp:NNNNNN` where `NNNNNN` is a 6-digit number.

If you see `ERROR: failed to obtain access token`, the OAuth credentials are wrong or the scope is insufficient — go back to Dimagi.

If you see `ERROR: failed to obtain OTP`, the fixture phone may not be invited to a test opportunity, or no `SessionPhoneDevice` record exists yet because no `start_configuration` call has been made for this user. The script can only return an OTP after a session has been initiated; in Wave 1's Maestro flow this happens naturally before the script runs.

- [ ] **Step 5: Commit**

```bash
git add .maestro/scripts/fetch-otp.sh
git commit -m "feat(phase9): OTP fetch helper script for E2E flows"
```

---

### Task 0.7: Create the Maestro fetch-otp subflow

**Files:**
- Create: `.maestro/subflows/fetch-otp.yaml`

- [ ] **Step 1: Create the subflow directory**

```bash
mkdir -p .maestro/subflows
```

- [ ] **Step 2: Write the subflow**

```yaml
# Phase 9 E2E shared subflow: fetch a one-time OTP for the fixture user.
#
# Wraps .maestro/scripts/fetch-otp.sh and exposes the result as ${output.otp}
# to the calling flow.
#
# Usage from a parent flow:
#   - runFlow:
#       file: ../subflows/fetch-otp.yaml
#   - inputText: ${output.otp}
#
# Required env vars (set by the parent flow or the CI workflow):
#   CONNECTID_E2E_CLIENT_ID
#   CONNECTID_E2E_CLIENT_SECRET
#   CONNECTID_E2E_PHONE

appId: org.marshellis.commcare.ios

---

- runScript:
    file: ../scripts/fetch-otp.sh
```

- [ ] **Step 3: Verify Maestro can parse it**

```bash
~/.maestro/bin/maestro test --dry-run .maestro/subflows/fetch-otp.yaml 2>&1 || true
```

Expected: no YAML parse errors. (The dry run may report "no test steps" since the file is a subflow, not a standalone flow — that's OK as long as YAML parses.)

- [ ] **Step 4: Commit**

```bash
git add .maestro/subflows/fetch-otp.yaml
git commit -m "feat(phase9): Maestro subflow wrapping OTP fetch script"
```

---

### Task 0.8: Create the hello-world Maestro flow

This is the smallest possible flow that proves the OTP plumbing end-to-end. It launches the app and fetches an OTP. It does not type anything into the UI yet. Wave 1 will add UI interaction.

**Files:**
- Create: `.maestro/flows/e2e-hello-world.yaml`

- [ ] **Step 1: Write the flow**

```yaml
# Phase 9 E2E plumbing test: prove OTP fetch works from a Maestro flow.
#
# This flow does NOT exercise the Connect ID UI — it just launches the
# app, calls fetch-otp.sh, and asserts the script returned an OTP. It is
# the minimal proof that Wave 0 infrastructure works before Wave 1 adds
# real UI interaction.
#
# Run:
#   ~/.maestro/bin/maestro test .maestro/flows/e2e-hello-world.yaml
#
# Required env (from .env.e2e.local locally or GitHub secrets in CI):
#   CONNECTID_E2E_CLIENT_ID
#   CONNECTID_E2E_CLIENT_SECRET
#   CONNECTID_E2E_PHONE

appId: org.marshellis.commcare.ios

---

- launchApp:
    appId: "org.marshellis.commcare.ios"
    clearState: true

# Wait for any visible UI to confirm the app launched.
- extendedWaitUntil:
    visible: "Log In"
    timeout: 30000

# Fetch an OTP. If the script fails (auth error, missing fixture, etc.)
# Maestro will fail the flow and surface the script error.
- runFlow:
    file: ../subflows/fetch-otp.yaml

# At this point ${output.otp} should be a 6-digit string. We assert
# nothing further — Wave 1 will type it into the OTP field for real.
```

- [ ] **Step 2: Run the flow locally**

```bash
set -a; source .env.e2e.local; set +a
~/.maestro/bin/maestro test .maestro/flows/e2e-hello-world.yaml
```

Expected: green run, with the script's `output=otp:NNNNNN` line visible in Maestro's output.

**Important caveat:** at this stage the OTP fetch will likely fail because no `SessionPhoneDevice` exists yet for the fixture phone — connect-id only generates an OTP token after a `start_configuration` call. To test the script in isolation, manually call `start_configuration` first via curl, OR accept that this hello-world flow only fully passes once Wave 1's recovery flow has run at least once. Note this in the GitHub issue.

- [ ] **Step 3: Commit**

```bash
git add .maestro/flows/e2e-hello-world.yaml
git commit -m "feat(phase9): hello-world Maestro flow proving OTP plumbing"
```

---

### Task 0.9: Create the e2e-ui.yml CI workflow

**Files:**
- Create: `.github/workflows/e2e-ui.yml`

- [ ] **Step 1: Write the workflow**

```yaml
name: Phase 9 E2E UI Tests

on:
  workflow_dispatch:
  schedule:
    # Daily at 06:00 UTC
    - cron: '0 6 * * *'

concurrency:
  group: ${{ github.workflow }}
  cancel-in-progress: false

jobs:
  e2e:
    runs-on: macos-15
    timeout-minutes: 60
    env:
      CONNECTID_E2E_CLIENT_ID: ${{ secrets.CONNECTID_E2E_CLIENT_ID }}
      CONNECTID_E2E_CLIENT_SECRET: ${{ secrets.CONNECTID_E2E_CLIENT_SECRET }}
      CONNECTID_E2E_PHONE: ${{ secrets.CONNECTID_E2E_PHONE }}
      CONNECTID_E2E_BACKUP_CODE: ${{ secrets.CONNECTID_E2E_BACKUP_CODE }}
      CONNECTID_E2E_PIN: ${{ secrets.CONNECTID_E2E_PIN }}
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - uses: gradle/actions/setup-gradle@v4

      - name: Make gradlew executable
        run: chmod +x commcare-core/gradlew app/gradlew

      - name: Build commcare-core iOS framework
        working-directory: commcare-core
        run: ./gradlew linkDebugFrameworkIosSimulatorArm64 --no-daemon --stacktrace

      - name: Build app iOS framework
        working-directory: app
        run: ./gradlew linkDebugFrameworkIosSimulatorArm64 --no-daemon --stacktrace

      - name: Install xcodegen
        run: brew install xcodegen jq

      - name: Generate Xcode project
        working-directory: app/iosApp
        run: xcodegen generate

      - name: Build iOS app
        working-directory: app/iosApp
        run: |
          set -o pipefail
          xcodebuild build \
            -project CommCare.xcodeproj \
            -scheme CommCare \
            -sdk iphonesimulator \
            -destination 'generic/platform=iOS Simulator' \
            -configuration Debug \
            -derivedDataPath build \
            ARCHS=arm64 \
            ONLY_ACTIVE_ARCH=YES \
            CODE_SIGN_IDENTITY="-" \
            CODE_SIGNING_REQUIRED=NO \
            CODE_SIGNING_ALLOWED=NO \
            2>&1 | tail -80

      - name: Erase and boot a clean simulator
        run: |
          xcrun simctl erase all
          DEVICE_ID=$(xcrun simctl list devices available -j | python3 -c "
          import json, sys
          data = json.load(sys.stdin)
          for runtime, devices in sorted(data['devices'].items(), reverse=True):
            if 'iOS' in runtime:
              for d in devices:
                if 'iPhone' in d['name'] and d['isAvailable']:
                  print(d['udid']); sys.exit(0)
          sys.exit(1)
          ")
          echo "DEVICE_ID=$DEVICE_ID" >> $GITHUB_ENV
          xcrun simctl boot "$DEVICE_ID"
          xcrun simctl install "$DEVICE_ID" \
            "app/iosApp/build/Build/Products/Debug-iphonesimulator/CommCare.app"

      - name: Install Maestro
        run: |
          curl -Ls "https://get.maestro.mobile.dev" | bash
          echo "$HOME/.maestro/bin" >> $GITHUB_PATH

      - name: Run hello-world plumbing flow
        run: |
          ~/.maestro/bin/maestro test .maestro/flows/e2e-hello-world.yaml --no-ansi

      # Wave 1 will add the connect-id-recovery.yaml flow here in Task 1.4.

      - name: Upload Maestro artifacts on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: maestro-artifacts-${{ github.run_id }}
          path: |
            ~/.maestro/tests/**/*
          retention-days: 14

      - name: Shutdown simulator
        if: always()
        run: xcrun simctl shutdown all 2>/dev/null || true
```

- [ ] **Step 2: Validate YAML syntax**

```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/e2e-ui.yml'))" && echo "YAML valid"
```

Expected: `YAML valid`.

- [ ] **Step 3: Add GitHub secrets via the gh CLI (or web UI)**

```bash
gh secret set CONNECTID_E2E_CLIENT_ID
gh secret set CONNECTID_E2E_CLIENT_SECRET
gh secret set CONNECTID_E2E_PHONE
gh secret set CONNECTID_E2E_BACKUP_CODE
gh secret set CONNECTID_E2E_PIN
```

Each prompts for the value. Paste from the password manager (Task 0.2 Step 5).

- [ ] **Step 4: Trigger the workflow manually**

```bash
git push origin emdash/e2e-ios-test-1ai
gh workflow run e2e-ui.yml --ref emdash/e2e-ios-test-1ai
gh run watch
```

Expected: workflow finishes green. The hello-world flow may report `failed to obtain OTP` if no session exists for the fixture phone — that's OK at this stage as long as everything else (build, install, Maestro launch) succeeds. Wave 1 will make the OTP fetch reliably succeed.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/e2e-ui.yml
git commit -m "ci(phase9): nightly E2E UI workflow with OTP plumbing test"
```

---

### Task 0.10: Add ConnectIdIntegrationTest (relocated from Phase 8 Task 2)

**Files:**
- Create: `app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectIdIntegrationTest.kt`

- [ ] **Step 1: Verify ConnectTestConfig already exists**

```bash
ls app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectTestConfig.kt
```

Expected: file exists (created in Phase 8 Task 1, PR #373). If missing, stop and investigate — the relocation depends on it.

- [ ] **Step 2: Write ConnectIdIntegrationTest**

```kotlin
// app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectIdIntegrationTest.kt
package org.commcare.app.integration

import org.commcare.app.network.ConnectIdApi
import org.commcare.core.interfaces.createHttpClient
import org.junit.Assume
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Live integration tests against connectid.dimagi.com.
 *
 * Requires CONNECT_ACCESS_TOKEN (and/or CONNECT_USERNAME + CONNECT_PASSWORD)
 * to be set in the environment. Tests are skipped via JUnit Assume if no
 * credentials are configured, so they pass cleanly in unconfigured CI.
 *
 * These tests were originally Phase 8 Task 2 and were deferred until the
 * +7426 / generate_manual_otp infrastructure existed. Phase 9 Wave 0
 * provides that infrastructure; this file relocates the tests here.
 */
class ConnectIdIntegrationTest {

    private lateinit var api: ConnectIdApi

    @Before
    fun setup() {
        Assume.assumeTrue(
            "Connect credentials not configured (set CONNECT_ACCESS_TOKEN)",
            ConnectTestConfig.isConfigured
        )
        api = ConnectIdApi(createHttpClient())
    }

    @Test
    fun testFetchDbKeyWithValidToken() {
        val token = ConnectTestConfig.connectAccessToken
        Assume.assumeTrue("CONNECT_ACCESS_TOKEN not set", token.isNotBlank())

        val result = api.fetchDbKey(token)
        assertTrue(
            result.isSuccess,
            "fetchDbKey should succeed with a valid token: ${result.exceptionOrNull()}"
        )
        val dbKey = result.getOrNull()
        assertTrue(dbKey != null && dbKey.isNotBlank(), "DB key should be non-blank")
    }

    @Test
    fun testFetchDbKeyWithExpiredToken() {
        val result = api.fetchDbKey("expired-invalid-token-12345")
        assertTrue(result.isFailure, "Expired token should fail")
    }

    @Test
    fun testOAuthTokenWithInvalidCredentials() {
        val result = api.getOAuthToken("nonexistent@user.example", "wrongpassword")
        assertTrue(result.isFailure, "Invalid credentials should fail")
    }
}
```

- [ ] **Step 3: Run the test (will skip without credentials)**

```bash
cd app && ./gradlew jvmTest --tests "org.commcare.app.integration.ConnectIdIntegrationTest" -i
```

Expected: tests SKIP (because no `CONNECT_ACCESS_TOKEN` env var). Look for `ASSUMPTION FAILED` in the output — that's the skip marker, not a real failure.

- [ ] **Step 4: Commit**

```bash
git add app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectIdIntegrationTest.kt
git commit -m "test(phase9): ConnectID live integration tests (relocated from Phase 8 Task 2)"
```

---

### Task 0.11: Add ConnectMarketplaceIntegrationTest (relocated from Phase 8 Task 3a)

**Files:**
- Create: `app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectMarketplaceIntegrationTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
// app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectMarketplaceIntegrationTest.kt
package org.commcare.app.integration

import org.commcare.app.network.ConnectMarketplaceApi
import org.commcare.core.interfaces.createHttpClient
import org.junit.Assume
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Live integration tests against the Connect Marketplace API.
 * Relocated from Phase 8 Task 3a. See ConnectIdIntegrationTest header comment.
 */
class ConnectMarketplaceIntegrationTest {

    private lateinit var api: ConnectMarketplaceApi

    @Before
    fun setup() {
        Assume.assumeTrue(
            "Connect credentials not configured (set CONNECT_ACCESS_TOKEN)",
            ConnectTestConfig.isConfigured
        )
        api = ConnectMarketplaceApi(createHttpClient())
    }

    @Test
    fun testGetOpportunitiesReturnsValidList() {
        val token = ConnectTestConfig.connectAccessToken
        val result = api.getOpportunities(token)
        assertTrue(
            result.isSuccess,
            "getOpportunities should succeed: ${result.exceptionOrNull()}"
        )
        // The list may be empty if the fixture user has no opportunities,
        // but the call should not error.
    }

    @Test
    fun testGetOpportunitiesWithInvalidToken() {
        val result = api.getOpportunities("invalid-token-zzzz")
        assertTrue(result.isFailure, "Invalid token should fail")
    }
}
```

- [ ] **Step 2: Run the test**

```bash
cd app && ./gradlew jvmTest --tests "org.commcare.app.integration.ConnectMarketplaceIntegrationTest" -i
```

Expected: SKIP without credentials.

- [ ] **Step 3: Commit**

```bash
git add app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectMarketplaceIntegrationTest.kt
git commit -m "test(phase9): Connect marketplace live integration tests (relocated from Phase 8 Task 3)"
```

---

### Task 0.12: Add ConnectMessagingIntegrationTest (relocated from Phase 8 Task 3b)

**Files:**
- Create: `app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectMessagingIntegrationTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
// app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectMessagingIntegrationTest.kt
package org.commcare.app.integration

import org.commcare.app.network.ConnectMarketplaceApi
import org.commcare.core.interfaces.createHttpClient
import org.junit.Assume
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Live integration tests for Connect messaging APIs.
 *
 * Relocated from Phase 8 Task 3b. Note: messaging endpoints
 * (getMessages, sendMessage, updateConsent) live on
 * ConnectMarketplaceApi, not a separate client. This test uses
 * ConnectMarketplaceApi intentionally — see Phase 8 plan note.
 */
class ConnectMessagingIntegrationTest {

    private lateinit var api: ConnectMarketplaceApi

    @Before
    fun setup() {
        Assume.assumeTrue(
            "Connect credentials not configured (set CONNECT_ACCESS_TOKEN)",
            ConnectTestConfig.isConfigured
        )
        api = ConnectMarketplaceApi(createHttpClient())
    }

    @Test
    fun testGetMessagesReturnsThreadList() {
        val token = ConnectTestConfig.connectAccessToken
        val result = api.getMessages(token)
        assertTrue(
            result.isSuccess,
            "getMessages should succeed: ${result.exceptionOrNull()}"
        )
    }

    @Test
    fun testUpdateConsentSucceeds() {
        val token = ConnectTestConfig.connectAccessToken
        val result = api.updateConsent(token)
        assertTrue(
            result.isSuccess,
            "updateConsent should succeed: ${result.exceptionOrNull()}"
        )
    }

    @Test
    fun testGetMessagesWithInvalidToken() {
        val result = api.getMessages("invalid-token-zzzz")
        assertTrue(result.isFailure, "Invalid token should fail")
    }
}
```

- [ ] **Step 2: Run the test**

```bash
cd app && ./gradlew jvmTest --tests "org.commcare.app.integration.ConnectMessagingIntegrationTest" -i
```

Expected: SKIP without credentials.

- [ ] **Step 3: Commit**

```bash
git add app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectMessagingIntegrationTest.kt
git commit -m "test(phase9): Connect messaging live integration tests (relocated from Phase 8 Task 3)"
```

---

### Task 0.13: Wire relocated integration tests into hq-integration.yml

**Files:**
- Modify: `.github/workflows/hq-integration.yml`

- [ ] **Step 1: Read the existing workflow**

```bash
cat .github/workflows/hq-integration.yml
```

Find the existing test step. The new step goes after the HQ integration tests run.

- [ ] **Step 2: Add the Connect integration test step**

Append this step to the existing job in `hq-integration.yml`:

```yaml
      - name: Run Connect Integration Tests
        if: env.CONNECT_ACCESS_TOKEN != ''
        env:
          CONNECT_ACCESS_TOKEN: ${{ secrets.CONNECT_ACCESS_TOKEN }}
          CONNECT_USERNAME: ${{ secrets.CONNECT_USERNAME }}
          CONNECT_PASSWORD: ${{ secrets.CONNECT_PASSWORD }}
        working-directory: app
        run: |
          ./gradlew jvmTest \
            --tests "*ConnectIdIntegrationTest*" \
            --tests "*ConnectMarketplaceIntegrationTest*" \
            --tests "*ConnectMessagingIntegrationTest*" \
            --no-daemon --stacktrace
        timeout-minutes: 5
```

The `if: env.CONNECT_ACCESS_TOKEN != ''` guard ensures the step is skipped (not failed) when secrets are not configured — matching the same pattern Phase 8 used for HQ integration tests.

- [ ] **Step 3: Add the secrets to GitHub Actions if not already present**

```bash
gh secret set CONNECT_ACCESS_TOKEN
gh secret set CONNECT_USERNAME
gh secret set CONNECT_PASSWORD
```

These are the same secrets the existing HQ integration job needs.

- [ ] **Step 4: Validate YAML**

```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/hq-integration.yml'))" && echo "YAML valid"
```

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/hq-integration.yml
git commit -m "ci(phase9): wire Connect live integration tests into weekly workflow"
```

- [ ] **Step 6: Trigger the workflow and verify**

```bash
gh workflow run hq-integration.yml --ref emdash/e2e-ios-test-1ai
gh run watch
```

Expected: green run, with the new step either passing (if creds set) or skipped cleanly.

---

### Task 0.14: Test identifier audit for Wave 1

This task is a one-time audit. It produces no commit unless gaps are found.

- [ ] **Step 1: Read the screens Wave 1 will exercise**

```bash
ls app/src/commonMain/kotlin/org/commcare/app/ui/personalid/
```

The recovery flow specifically uses:
- `SetupScreen.kt` — the "LOGIN WITH PERSONALID" tap (`testTag("signup_link")`)
- `PhoneEntryStep.kt` — phone field, country code, continue button
- `OtpVerificationStep.kt` — OTP field, verify button
- `BackupCodeStep.kt` — backup code field, continue button
- The post-recovery success screen (whichever composable the recovery branch lands on)

- [ ] **Step 2: Verify every interactive element has a testTag**

```bash
grep -n "testTag" app/src/commonMain/kotlin/org/commcare/app/ui/personalid/SetupScreen.kt \
  app/src/commonMain/kotlin/org/commcare/app/ui/personalid/PhoneEntryStep.kt \
  app/src/commonMain/kotlin/org/commcare/app/ui/personalid/OtpVerificationStep.kt \
  app/src/commonMain/kotlin/org/commcare/app/ui/personalid/BackupCodeStep.kt
```

Expected: every `Button(`, `OutlinedTextField(`, `TextField(`, and `Switch(` in those files has an adjacent `Modifier.testTag("name")`. The exploration in spec §4 confirmed Connect ID screens are well-instrumented; spot-check is sufficient.

- [ ] **Step 3: If gaps exist, add testTags BEFORE writing Wave 1**

If any element is missing a tag, add it as a tiny no-test-changes commit:

```bash
git commit -m "fix(phase9): add missing testTag for <element> in <screen>"
```

Document the gap-finding in the Wave 1 GitHub issue. If no gaps, proceed to Wave 1 with no commit from this task.

---

## Wave 0 exit gate

Before starting Wave 1:

- [ ] All tasks 0.1-0.14 above are checked off.
- [ ] `bash .maestro/scripts/fetch-otp.sh` returns a real OTP from a dev shell with `.env.e2e.local` populated. (Requires Wave 1 to have been started at least once for a session to exist — see Task 1.3 caveat.)
- [ ] `gh workflow run e2e-ui.yml` triggers and finishes; the hello-world flow at minimum gets through `launchApp` and `runScript`.
- [ ] The three relocated integration test classes pass-or-skip cleanly under `./gradlew jvmTest`.
- [ ] `docs/phase9/fixture-user.md` and `docs/phase9/ios-platform-test-policy.md` exist and have been reviewed by a teammate.
- [ ] The fixture user actually exists in connect-id prod and recovery has been manually verified at least once (Task 0.2 Step 6).

---

## Wave 1 — Connect ID recovery flow

The narrowest possible E2E test of real product code. Logs the fixture user in via the recovery branch on a fresh simulator.

### Task 1.1: Create the recovery subflow

**Files:**
- Create: `.maestro/subflows/recover-connect-id.yaml`

- [ ] **Step 1: Write the subflow**

```yaml
# Phase 9 shared subflow: log in the fixture user via the Connect ID recovery
# branch from a fresh simulator state. Used by Wave 1 directly and inherited
# by every later wave that needs an authenticated Connect session.
#
# Requires:
#   CONNECTID_E2E_PHONE        (fixture phone, +7426...)
#   CONNECTID_E2E_BACKUP_CODE  (fixture backup code from manual registration)
#   CONNECTID_E2E_CLIENT_ID    (consumed by fetch-otp.sh)
#   CONNECTID_E2E_CLIENT_SECRET
#
# Caller is responsible for the launchApp + clearState. This subflow assumes
# the app is launched and at the SetupScreen.

appId: org.marshellis.commcare.ios

---

# Tap "LOGIN WITH PERSONALID" on the setup screen.
- tapOn:
    id: "signup_link"

# Wait for the phone entry screen.
- extendedWaitUntil:
    visible:
      id: "phone_number_field"
    timeout: 10000

# Enter the fixture phone number. The country code field defaults to
# the user's locale; we tap the country code field and clear it just in
# case the simulator's locale doesn't match +7426.
- tapOn:
    id: "country_code_field"
- eraseText: 10
- inputText: "+7"

- tapOn:
    id: "phone_number_field"
# Strip the +7 prefix from the env var since we already typed +7 above.
# This evaluates at flow time; if your fixture phone is "+74260000001",
# pass "4260000001" as the phone field input.
- inputText: ${CONNECTID_E2E_PHONE_LOCAL}

# Dismiss keyboard before tapping Continue.
- tapOn:
    point: "50%,10%"
- waitForAnimationToEnd:
    timeout: 1000

- tapOn:
    id: "continue_button"

# The server should detect the existing account and route to the recovery
# branch. Wait for the OTP field to appear.
- extendedWaitUntil:
    visible:
      id: "otp_field"
    timeout: 15000

# Fetch the OTP via the helper script. After this step, ${output.otp}
# holds the 6-digit OTP value.
- runFlow:
    file: ../subflows/fetch-otp.yaml

- tapOn:
    id: "otp_field"
- inputText: ${output.otp}
- tapOn:
    point: "50%,10%"
- waitForAnimationToEnd:
    timeout: 1000

- tapOn:
    id: "verify_button"

# Recovery requires the backup code. Wait for the backup code field.
- extendedWaitUntil:
    visible:
      id: "backup_code_field"
    timeout: 15000

- tapOn:
    id: "backup_code_field"
- inputText: ${CONNECTID_E2E_BACKUP_CODE}
- tapOn:
    point: "50%,10%"
- waitForAnimationToEnd:
    timeout: 1000

- tapOn:
    id: "continue_button"

# At this point recovery should complete and the app should land on either
# the SuccessStep done button or the home screen (depending on the build).
# Wave 1 asserts on the post-recovery state in connect-id-recovery.yaml.
```

**Note on `CONNECTID_E2E_PHONE_LOCAL`:** Maestro doesn't have built-in string slicing on env vars. The simplest workaround is to set both `CONNECTID_E2E_PHONE` (full E.164, used by fetch-otp.sh) and `CONNECTID_E2E_PHONE_LOCAL` (the phone-number-only portion, used by the UI input) as separate secrets/env vars. Document this in `docs/phase9/fixture-user.md` and `.env.e2e.local.example`. (Add the env var to those files as a follow-up commit if not already present.)

- [ ] **Step 2: Update `.env.e2e.local.example` and `docs/phase9/fixture-user.md` to add `CONNECTID_E2E_PHONE_LOCAL`**

In `.env.e2e.local.example`, add below `CONNECTID_E2E_PHONE`:

```bash
# The phone number digits AFTER the country code, no +. Used by Maestro
# UI input where the country code is entered separately.
# Example: if CONNECTID_E2E_PHONE=+74260000001, set this to 4260000001.
CONNECTID_E2E_PHONE_LOCAL=
```

In `docs/phase9/fixture-user.md`, add `CONNECTID_E2E_PHONE_LOCAL` to the GitHub Actions secrets list.

- [ ] **Step 3: Commit**

```bash
git add .maestro/subflows/recover-connect-id.yaml \
       .env.e2e.local.example \
       docs/phase9/fixture-user.md
git commit -m "feat(phase9): Connect ID recovery subflow + PHONE_LOCAL env var"
```

---

### Task 1.2: Create the Wave 1 flow

**Files:**
- Create: `.maestro/flows/connect-id-recovery.yaml`

- [ ] **Step 1: Write the flow**

```yaml
# Phase 9 Wave 1: Connect ID recovery flow (the first real E2E test).
#
# Walks the fixture user through "existing user, new device" recovery on a
# fresh simulator. This flow is idempotent: it can run on every nightly
# without polluting connect-id with new users, because it never registers a
# new account — it always recovers the same fixture user.
#
# Run:
#   ~/.maestro/bin/maestro test .maestro/flows/connect-id-recovery.yaml
#
# Required env (from .env.e2e.local locally or GitHub secrets in CI):
#   CONNECTID_E2E_CLIENT_ID
#   CONNECTID_E2E_CLIENT_SECRET
#   CONNECTID_E2E_PHONE
#   CONNECTID_E2E_PHONE_LOCAL
#   CONNECTID_E2E_BACKUP_CODE

appId: org.marshellis.commcare.ios

---

# Start from a guaranteed-fresh app state.
- launchApp:
    appId: "org.marshellis.commcare.ios"
    clearState: true

# Wait for SetupScreen.
- extendedWaitUntil:
    visible: "Log In"
    timeout: 30000

# Walk the recovery flow.
- runFlow:
    file: ../subflows/recover-connect-id.yaml

# After recovery, the app routes to either the SuccessStep done button or
# directly to the home screen. Either is acceptable as proof of success.
# Assert on whichever element the build actually shows.
- extendedWaitUntil:
    visible:
      id: "done_button"
    optional: true
    timeout: 10000

- runFlow:
    when:
      visible:
        id: "done_button"
    commands:
      - tapOn:
          id: "done_button"

# Final assertion: home screen "Start" button is visible. This is the same
# selector login-and-home.yaml uses, so we know it identifies the home
# screen unambiguously.
- extendedWaitUntil:
    visible: "Start"
    timeout: 15000
```

**Note on the optional `done_button`:** the spec §6.1 says recovery may land on either `SuccessStep` or directly the home screen depending on the iOS implementation's recovery branch. The flow accommodates both by treating the done button as optional and asserting only on `Start`. If exploration during execution reveals only one path is real, simplify the flow.

- [ ] **Step 2: Run the flow locally**

```bash
set -a; source .env.e2e.local; set +a
xcrun simctl erase all
~/.maestro/bin/maestro test .maestro/flows/connect-id-recovery.yaml
```

Expected: green run. The whole flow should take 30-90 seconds depending on network.

If the flow fails:
- **Phone entry screen never appears**: Connect ID UI may have changed; re-audit `signup_link` testTag in `SetupScreen.kt`.
- **OTP fetch errors**: `runScript` output will surface the script's stderr. Check `CONNECTID_E2E_*` env vars and Dimagi setup.
- **`backup_code_field` never appears**: server didn't route to recovery branch. Verify the fixture user actually exists in connect-id prod (Task 0.2).
- **`Start` never appears**: recovery succeeded but the app didn't navigate to home. Add a screenshot step (`- takeScreenshot: post-recovery`) and inspect.

- [ ] **Step 3: Commit**

```bash
git add .maestro/flows/connect-id-recovery.yaml
git commit -m "feat(phase9): Wave 1 — Connect ID recovery E2E flow"
```

---

### Task 1.3: Wire Wave 1 into the nightly CI workflow

**Files:**
- Modify: `.github/workflows/e2e-ui.yml`

- [ ] **Step 1: Add the recovery flow to the workflow**

In `.github/workflows/e2e-ui.yml`, find the comment `# Wave 1 will add the connect-id-recovery.yaml flow here in Task 1.4.` and replace it with:

```yaml
      - name: Run Connect ID recovery flow
        run: |
          ~/.maestro/bin/maestro test .maestro/flows/connect-id-recovery.yaml --no-ansi
```

- [ ] **Step 2: Validate YAML**

```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/e2e-ui.yml'))" && echo "YAML valid"
```

- [ ] **Step 3: Trigger the workflow manually**

```bash
git push origin emdash/e2e-ios-test-1ai
gh workflow run e2e-ui.yml --ref emdash/e2e-ios-test-1ai
gh run watch
```

Expected: workflow finishes green. Both flows pass.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/e2e-ui.yml
git commit -m "ci(phase9): run Wave 1 recovery flow in nightly E2E workflow"
```

---

### Task 1.4: Stabilize Wave 1 — 10 consecutive green runs

This task has no commit unless flakiness is found. It is a wait-and-observe gate.

- [ ] **Step 1: Trigger the workflow nightly for 10 days**

The cron schedule will run automatically. Track results with:

```bash
gh run list --workflow=e2e-ui.yml --limit 10 --json conclusion,createdAt
```

Expected: all 10 most-recent runs `conclusion: success`.

- [ ] **Step 2: For any failed run, classify the failure**

Categories:
- **Infrastructure** (sim boot, build, network, GH Actions outage): not Wave 1's fault. Re-run.
- **Flake** (passed on retry without code change): a real Maestro reliability issue. Add a retry step or stabilize the relevant assertion.
- **Product bug** (real change in behavior): file an issue. The whole point of Wave 1 is to catch these.
- **Test bug** (Wave 1 flow has a wrong assumption): fix the flow.

Document each failure in the Wave 1 GitHub issue.

- [ ] **Step 3: Mark Wave 1 stable**

Once 10 consecutive runs pass, close the Wave 1 GitHub issue per the project's Issue Closure Rules:

> ## What was done
> Wave 1 of Phase 9: Connect ID recovery E2E flow.
>
> ## Acceptance criteria verification
> - [x] connect-id-recovery.yaml passes locally
> - [x] connect-id-recovery.yaml runs in e2e-ui.yml nightly
> - [x] 10 consecutive green nightly runs (Apr DD - Apr DD)
> - [x] No app code changes (test infrastructure only)
>
> ## Notable technical decisions
> - [Document any decisions made during stabilization]
>
> ## PR link
> [Link to merged Wave 1 PR]

---

## Wave 1 exit gate

- [ ] `connect-id-recovery.yaml` passes locally on a fresh simulator.
- [ ] `connect-id-recovery.yaml` passes in CI nightly.
- [ ] 10 consecutive green nightly runs achieved.
- [ ] No commits modified files outside `.maestro/`, `docs/phase9/`, `.github/workflows/`, or test directories. (Wave 1 should not require any app code changes; if it did, audit those changes.)
- [ ] Wave 1 GitHub issue closed with the closure note above.
- [ ] Any iOS platform code touched during debugging has a paired `iosTest/` unit test (per `docs/phase9/ios-platform-test-policy.md`).

---

## Waves 2-11 — defer to per-wave plan docs

Per the project's phase-and-wave convention, each subsequent wave gets its own detailed plan doc written immediately before the wave starts. The spec §7 contains the scope sketch for each. The plan docs will live at:

- `docs/superpowers/plans/2026-MM-DD-phase9-wave2-login-variants.md`
- `docs/superpowers/plans/2026-MM-DD-phase9-wave3-app-install.md`
- ...etc.

Writing all 11 wave plans up front would lock in detailed decisions about screens, testTags, and selectors that may change between now and when each wave is implemented. Better to plan one wave at a time, after the previous wave's stabilization has surfaced what's actually true about the app right now.

When ready to start Wave 2, invoke this workflow:
1. Read the spec §7 entry for the wave.
2. Read any learnings captured during the previous wave.
3. Run the writing-plans skill on the spec entry to produce the wave's plan doc.
4. Open a GitHub issue for the wave.
5. Execute the plan via subagent-driven-development or executing-plans.

---

## PR strategy

Per Doc PR Rules: documentation and code changes are in separate PRs.

| PR | Contents | Tasks |
|---|---|---|
| **PR A (docs)** | This plan doc + spec update + Phase 8 closure (already committed `b72d557` + `8c08929`) | — |
| **PR B (docs)** | `docs/phase9/fixture-user.md`, `docs/phase9/ios-platform-test-policy.md` | 0.3, 0.4 |
| **PR C (infra config)** | `.env.e2e.local.example`, `.gitignore` update | 0.5 |
| **PR D (test infra)** | `.maestro/scripts/fetch-otp.sh`, `.maestro/subflows/fetch-otp.yaml`, `.maestro/flows/e2e-hello-world.yaml`, `.github/workflows/e2e-ui.yml` | 0.6, 0.7, 0.8, 0.9 |
| **PR E (relocated tests)** | `ConnectIdIntegrationTest.kt`, `ConnectMarketplaceIntegrationTest.kt`, `ConnectMessagingIntegrationTest.kt`, `hq-integration.yml` update | 0.10, 0.11, 0.12, 0.13 |
| **PR F (Wave 1)** | `.maestro/subflows/recover-connect-id.yaml`, `.maestro/flows/connect-id-recovery.yaml`, `.github/workflows/e2e-ui.yml` update | 1.1, 1.2, 1.3 |

PR D depends on PR B (file references). PR E is independent but should land after PR D so the e2e-ui workflow is in place. PR F depends on PR D. Tasks 0.1 (Dimagi ask), 0.2 (manual fixture creation), 0.14 (testTag audit), and 1.4 (stabilization) have no commits and span multiple PRs.

---

## Risks specific to this plan

| Risk | Mitigation |
|---|---|
| Dimagi takes weeks to provide credentials | Open the ask immediately on day one. Block formal Wave 0 start until response. Unblockable via internal escalation. |
| `generate_manual_otp` requires a session that doesn't exist yet (chicken-and-egg with hello-world flow) | Documented in Task 0.8 Step 2. Wave 1 makes the OTP fetch reliably succeed because it precedes it with a real `start_configuration` call via the UI. Hello-world flow may stay imperfect — that's acceptable. |
| Maestro testTag selectors drift between Compose builds | Task 0.14 audit before Wave 1. If a wave-N PR breaks a selector, the old wave's flow goes red and we fix the selector before merging. |
| Backup code attempts exhausted on the fixture user | Documented in `docs/phase9/fixture-user.md` runbook. Manual reset via Dimagi. If chronic, audit waves for code that triggers failure paths against the real fixture. |
| Country code / phone number formatting differs between dev locale and CI runner locale | The `CONNECTID_E2E_PHONE` and `CONNECTID_E2E_PHONE_LOCAL` split addresses this. Both env vars must be set consistently. |
| `xcrun simctl erase all` slows CI runs | Documented as part of the fresh-simulator-per-flow contract. Acceptable; the alternative is bleed-over between flows which is worse. |
| Maestro upgrade breaks existing flows | Pin Maestro version in `e2e-ui.yml` once stable. Currently uses `curl -Ls "https://get.maestro.mobile.dev" | bash` which always fetches latest. Track in Wave 11 reliability work. |

---

## Self-review checklist (already executed)

- [x] **Spec coverage**: every spec §5 deliverable maps to at least one Wave 0 task. Spec §6 (Wave 1) maps to Tasks 1.1-1.3.
- [x] **Placeholder scan**: no "TBD", "TODO", "fill in details" in this plan. Real env-var names. Real file paths. Real shell commands.
- [x] **Type consistency**: `ConnectIdApi`, `ConnectMarketplaceApi`, `ConnectTestConfig` all reference real classes that exist on `main` (verified via Phase 8 Task 1 PR #373).
- [x] **Scope check**: only Wave 0 + Wave 1 are detailed. Waves 2-11 explicitly deferred. The plan does not try to do too much.
- [x] **Forward dependencies**: PR strategy table reflects file-level dependencies. No PR ordering will block on a missing file.
- [x] **No test hooks in production code**: every task in Wave 0 + Wave 1 touches only `.maestro/`, `.github/workflows/`, `docs/`, `.env.e2e.local.example`, or `app/src/jvmTest/`. Zero changes to `commonMain/`, `iosMain/`, or `jvmMain/`.

---

## Execution mode

After this plan lands and is committed, the plan author offers two execution paths to the user:

1. **Subagent-driven** (recommended): one fresh subagent per task, two-stage review between tasks, fast iteration.
2. **Inline execution**: tasks run in the same session via `superpowers:executing-plans`, batched with checkpoints.

The subagent-driven path is preferred for Wave 0 because the tasks are independent and well-bounded. Wave 1's recovery flow may benefit from inline execution because debugging Maestro selectors in real time tends to need conversational back-and-forth.
