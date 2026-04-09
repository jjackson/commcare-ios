# Phase 9 Wave 2 — Post-recovery state + session persistence

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cover the "what happens after recovery" surface with Maestro tests — the SetupScreen variant shown to a signed-in user, the Connect menu entry point, and the kill/relaunch session-persistence behavior. Along the way, file a real product bug this wave uncovered.

**Architecture:** Extend the Wave 0/1 orchestrator split-flow pattern. Recovery runs as the precondition; new flows exercise what comes next. No new infrastructure. No new external dependencies.

**Spec:** `docs/superpowers/specs/2026-04-08-phase9-e2e-ui-testing-design.md` §7 (Wave 2)

**Predecessor:** `docs/superpowers/plans/2026-04-08-phase9-e2e-ui-testing.md` (Wave 0 infra + Wave 1 recovery)

---

## Mid-plan refactor: consolidate into one orchestrator

The plan as originally drafted had three separate orchestrators, each doing a full fresh-install + recovery + single assertion. Stability = 5× each = 15 full recoveries = ~22 minutes.

During implementation it became obvious this is wasteful. Recovery takes ~70s; the three assertions take ~5s each. And the three assertions all share the same precondition (a signed-in user post-recovery), so there's no reason to pay for recovery three times.

**Actual shipped shape:** a single `run-wave2.sh` that does recovery once and runs all three assertions in sequence:

1. Fresh install + recovery + tap done_button → SetupScreen signed in
2. **Assertion A** — `post-recovery-state.yaml` against SetupScreen
3. `simctl terminate` + `simctl launch`
4. **Assertion B** — `session-persistence.yaml` re-checks the same SetupScreen state after restart
5. **Assertion C** — `connect-menu-entry.yaml` taps Connect button and asserts the bug

One run = ~77 seconds. Stability = 3 back-to-back runs = ~4 minutes instead of 22+. The three assertion YAMLs are still independent files (debuggable in isolation if you put the simulator in the right state manually), but the individual orchestrators were deleted as duplicate ceremony.

The task breakdown below is kept as-is (task-by-task notes reflect the drafting process, including the fix for the missing `CONNECTID_E2E_PIN` env var). The File Map and Acceptance Criteria sections reflect the shipped shape after the refactor.

---

## Scope change from the spec

The spec's Wave 2 was titled "Login variants" and listed PIN login, biometric login, and saved-session restore as three independent flows. Empirical scouting on 2026-04-08 (iOS simulator, booted CommCare.app after successful recovery) invalidated most of those assumptions:

1. **PIN login does not trigger on normal relaunch.** Killing the app with `xcrun simctl terminate` and relaunching with `xcrun simctl launch` leaves the user on SetupScreen with "Signed in to Personal ID ✓" — no PIN entry screen appears. The PIN is stored for the one place it matters (Connect token refresh fallback), not as a lockscreen. The original Wave 2 design assumed an Android-style lockscreen that iOS does not currently implement. Deferred until we decide whether iOS should have one.
2. **Biometric login has the same problem.** No biometric prompt on relaunch either. Same reason. Deferred to the same decision.
3. **"Saved-session restore" is just "relaunch the app."** There is no explicit restore step — the app reads `ConnectIdRepository` on start and adapts the SetupScreen UI. This is worth testing, but it's a one-flow test, not a wave of work.

What remains actually testable right now:

- **Post-recovery state on SetupScreen.** After a successful recovery, SetupScreen should show the "GO TO CONNECT MENU" button and the "Signed in to Personal ID ✓" text. This is the state the user sees every time they open the app after the first-time setup. No test covers it today.
- **Session persistence across kill + relaunch.** Kill the process and relaunch; the same signed-in SetupScreen should render. Validates `ConnectIdRepository` round-trips through iOS storage.
- **Navigation into the Connect menu.** Tapping "GO TO CONNECT MENU" opens the Connect menu screen. This is where we uncovered a state inconsistency bug that needs to be filed and tracked.

**New scope — bug filing:** the Connect menu screen shows "Not signed in to ConnectID" immediately after SetupScreen said "Signed in to Personal ID ✓". Two sources of truth disagree about the same user state. This is a real product bug found by E2E scouting. It gets a GitHub issue and a test that documents the current (buggy) behavior so a future fix can turn it into a positive assertion.

**Out of scope (deferred):**

- PIN login flow (no lockscreen exists to trigger it)
- Biometric login flow (same)
- Fresh simctl biometric enrollment automation (no trigger to exercise it)

When iOS grows a lockscreen, a follow-up wave will cover those variants. For now, Wave 2 ships what we actually have.

---

## File Map (as shipped)

| File | Action | Purpose |
|---|---|---|
| `.maestro/flows/post-recovery-state.yaml` | Create | Assertion A: SetupScreen signed-in variant visible (connect_button + signup_link) |
| `.maestro/flows/session-persistence.yaml` | Create | Assertion B: same SetupScreen signed-in variant visible after kill + relaunch |
| `.maestro/flows/connect-menu-entry.yaml` | Create | Assertion C: tap Connect button, document current inconsistent state (bug #389) |
| `.maestro/flows/tap-done-button.yaml` | Create | Shared helper: taps done_button on the Success screen |
| `.maestro/scripts/run-wave2.sh` | Create | Single consolidated orchestrator: recovery → done_button → A → kill+launch → B → C |
| `docs/phase9/wave2-state-inconsistency.md` | Create | Technical writeup of bug #389, used as the GitHub issue body |
| `CLAUDE.md` | Modify | Mark Phase 9 Wave 2 complete, reference plan + bug issue |
| (GitHub issue) | Create | #389 — `Connect ID state inconsistency: SetupScreen vs Connect menu disagree after recovery` |

Nothing in this wave modifies production code. The bug fix itself is NOT in Wave 2 scope — we're cataloging, not repairing. A separate issue owns the fix.

---

## Acceptance Criteria (as shipped)

| Check | Verification | Pass condition |
|---|---|---|
| A | `.maestro/scripts/run-wave2.sh` Assertion A step | `post-recovery-state.yaml` asserts `connect_button` + `signup_link` + `scan_barcode_button` visible |
| B | `.maestro/scripts/run-wave2.sh` Assertion B step | After simctl terminate + launch, `session-persistence.yaml` re-asserts same signed-in state |
| C | `.maestro/scripts/run-wave2.sh` Assertion C step | `connect-menu-entry.yaml` taps connect_button, captures screenshot, asserts "Not signed in to ConnectID" (buggy state, deliberately) |
| Bug | GitHub issue #389 filed, referenced in CLAUDE.md, linked from the bug doc | Issue open with `bug` label, body matches `docs/phase9/wave2-state-inconsistency.md` |
| Stability | `run-wave2.sh` executed 3 times back-to-back locally | All 3 runs green, zero flakes, ~77s each |

---

## Wave 2 — Task Breakdown

### Task 2.1: Post-recovery state flow

**Files:**
- Create: `.maestro/flows/post-recovery-state.yaml`
- Create: `.maestro/scripts/run-post-recovery.sh`

**Scene-setting:** After `run-recovery.sh` finishes, the app sits on the Success screen with `done_button` visible. Tapping it returns the user to `SetupScreen`. In the `isConnectIdRegistered` branch (see `app/src/commonMain/kotlin/org/commcare/app/ui/SetupScreen.kt:96-117`), SetupScreen renders a "GO TO CONNECT MENU" button (testTag `connect_button`) and a "Signed in to Personal ID ✓" text (testTag `signup_link`). That's what we assert.

**Gotcha — testTag overload:** `signup_link` is used for BOTH the "Signed in to Personal ID ✓" text (when registered) AND the "LOGIN WITH PERSONALID" button (when not registered). See `SetupScreen.kt:189` and `SetupScreen.kt:194`. Maestro's `visible: id:` matcher cannot distinguish. The test must rely on the unambiguous `connect_button` testTag, which only renders in the registered branch. File a follow-up to split the tag (not in this wave — changing prod testTags touches screenshots and snapshot tests).

- [ ] **Step 1: Write `.maestro/flows/post-recovery-state.yaml`**

```yaml
# Phase 9 Wave 2: Post-recovery SetupScreen state.
#
# Runs AFTER recovery has completed AND the user has tapped done_button.
# Asserts the SetupScreen shows the signed-in variant: both the
# "GO TO CONNECT MENU" button (connect_button) and the "Signed in to
# Personal ID" indicator (signup_link, same testTag as the login button).
#
# The connect_button testTag is the unambiguous signal — it only renders
# in the isConnectIdRegistered branch of SetupScreen.kt.

appId: org.marshellis.commcare.ios

---

- extendedWaitUntil:
    visible:
      id: "connect_button"
    timeout: 15000

- assertVisible:
    id: "connect_button"

- assertVisible:
    id: "signup_link"

- assertVisible:
    id: "scan_barcode_button"

- takeScreenshot: /tmp/phase9-post-recovery-state
```

- [ ] **Step 2: Write `.maestro/scripts/run-post-recovery.sh`**

```bash
#!/usr/bin/env bash
# Phase 9 Wave 2: post-recovery SetupScreen state orchestrator.
#
# Flow:
#   1. Fresh install + boot
#   2. Run recovery part A → fetch OTP → recovery part B → done_button visible
#   3. Tap done_button to return to SetupScreen
#   4. Run post-recovery-state.yaml to assert the signed-in SetupScreen

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

set -a
source .env.e2e.local
set +a

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
PLAYWRIGHT_SCRIPT="$ROOT/.maestro/scripts/playwright/fetch-otp.js"
APP_PATH="$ROOT/app/iosApp/build/Build/Products/Debug-iphonesimulator/CommCare.app"

# Fresh install
xcrun simctl terminate booted org.marshellis.commcare.ios 2>/dev/null || true
xcrun simctl uninstall booted org.marshellis.commcare.ios 2>/dev/null || true
xcrun simctl install booted "$APP_PATH"
xcrun simctl launch booted org.marshellis.commcare.ios >/dev/null
sleep 2

# Recovery part A
"$MAESTRO" test \
  -e "CONNECTID_E2E_PHONE=$CONNECTID_E2E_PHONE" \
  -e "CONNECTID_E2E_PHONE_LOCAL=$CONNECTID_E2E_PHONE_LOCAL" \
  -e "CONNECTID_E2E_COUNTRY_CODE=$CONNECTID_E2E_COUNTRY_CODE" \
  -e "CONNECTID_E2E_PIN=$CONNECTID_E2E_PIN" \
  .maestro/flows/connect-id-recovery-to-otp.yaml \
  --no-ansi

# Fetch OTP
OTP="$(node "$PLAYWRIGHT_SCRIPT" "$CONNECTID_E2E_PHONE")"
if ! [[ "$OTP" =~ ^[0-9]{6}$ ]]; then
  echo "ERROR: invalid OTP: '$OTP'" >&2
  exit 2
fi

# Recovery part B
"$MAESTRO" test \
  -e "CONNECTID_E2E_OTP=$OTP" \
  -e "CONNECTID_E2E_BACKUP_CODE=$CONNECTID_E2E_BACKUP_CODE" \
  .maestro/flows/connect-id-recovery-from-otp.yaml \
  --no-ansi

# Tap done_button to return to SetupScreen
"$MAESTRO" test .maestro/flows/tap-done-button.yaml --no-ansi

# Assert post-recovery SetupScreen state
"$MAESTRO" test .maestro/flows/post-recovery-state.yaml --no-ansi

echo "=== $(basename "$0") complete ==="
```

- [ ] **Step 3: `chmod +x .maestro/scripts/run-post-recovery.sh`**

- [ ] **Step 4: Run it from a clean simulator**

Run: `.maestro/scripts/run-post-recovery.sh`
Expected: exit 0, `=== run-post-recovery.sh complete ===` on stdout, `/tmp/phase9-post-recovery-state.png` exists and shows SetupScreen with signed-in UI.

- [ ] **Step 5: Commit**

```bash
git add .maestro/flows/post-recovery-state.yaml .maestro/scripts/run-post-recovery.sh
git commit -m "feat(phase9-w2): post-recovery SetupScreen state flow"
```

---

### Task 2.2: Session persistence flow (kill + relaunch)

**Files:**
- Create: `.maestro/flows/session-persistence.yaml`
- Create: `.maestro/scripts/run-session-persistence.sh`

**Scene-setting:** The Connect ID session is persisted via `ConnectIdRepository` (backed by the app database + keychain). A kill + relaunch should reload the session and the UI should reflect it. The spec's "Task 2.3: saved-session restore flow" is exactly this scenario, just renamed since there's no explicit "restore" step — it's automatic.

**The assertion is the same as Task 2.1** — `connect_button` visible, `signup_link` visible — but the timing is different: the app is killed between the recovery that sets the state and the assertion that reads it. A naive implementation would reuse `post-recovery-state.yaml`, but the flow needs to sleep after relaunch before the first `extendedWaitUntil` because `xcrun simctl launch` does not block until interactable (learning 7 from `2026-04-08-phase9-test-infra-patterns.md`).

- [ ] **Step 1: Write `.maestro/flows/session-persistence.yaml`**

```yaml
# Phase 9 Wave 2: Connect ID session persistence across app restart.
#
# Runs AFTER the app has been killed + relaunched (orchestrator handles
# the simctl calls). Same assertion as post-recovery-state.yaml: the
# SetupScreen shows the signed-in variant. The point of this flow is
# to prove the state survives a process restart.

appId: org.marshellis.commcare.ios

---

# Generous timeout because simctl launch doesn't block until interactable.
- extendedWaitUntil:
    visible:
      id: "connect_button"
    timeout: 20000

- assertVisible:
    id: "connect_button"

- assertVisible:
    id: "signup_link"

- assertVisible:
    id: "scan_barcode_button"

- takeScreenshot: /tmp/phase9-session-persistence
```

- [ ] **Step 2: Write `.maestro/scripts/run-session-persistence.sh`**

```bash
#!/usr/bin/env bash
# Phase 9 Wave 2: Connect ID session persistence orchestrator.
#
# Flow:
#   1. Fresh install + boot
#   2. Recovery part A → OTP → recovery part B → done_button visible
#   3. Tap done_button (back to SetupScreen)
#   4. xcrun simctl terminate  (kill the process)
#   5. xcrun simctl launch     (relaunch)
#   6. session-persistence.yaml asserts signed-in state persists

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

set -a
source .env.e2e.local
set +a

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
PLAYWRIGHT_SCRIPT="$ROOT/.maestro/scripts/playwright/fetch-otp.js"
APP_PATH="$ROOT/app/iosApp/build/Build/Products/Debug-iphonesimulator/CommCare.app"
BUNDLE_ID="org.marshellis.commcare.ios"

# Fresh install
xcrun simctl terminate booted "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl uninstall booted "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl install booted "$APP_PATH"
xcrun simctl launch booted "$BUNDLE_ID" >/dev/null
sleep 2

# Recovery part A
"$MAESTRO" test \
  -e "CONNECTID_E2E_PHONE=$CONNECTID_E2E_PHONE" \
  -e "CONNECTID_E2E_PHONE_LOCAL=$CONNECTID_E2E_PHONE_LOCAL" \
  -e "CONNECTID_E2E_COUNTRY_CODE=$CONNECTID_E2E_COUNTRY_CODE" \
  -e "CONNECTID_E2E_PIN=$CONNECTID_E2E_PIN" \
  .maestro/flows/connect-id-recovery-to-otp.yaml \
  --no-ansi

OTP="$(node "$PLAYWRIGHT_SCRIPT" "$CONNECTID_E2E_PHONE")"
if ! [[ "$OTP" =~ ^[0-9]{6}$ ]]; then
  echo "ERROR: invalid OTP: '$OTP'" >&2
  exit 2
fi

# Recovery part B
"$MAESTRO" test \
  -e "CONNECTID_E2E_OTP=$OTP" \
  -e "CONNECTID_E2E_BACKUP_CODE=$CONNECTID_E2E_BACKUP_CODE" \
  .maestro/flows/connect-id-recovery-from-otp.yaml \
  --no-ansi

# Tap done_button to return to SetupScreen
"$MAESTRO" test .maestro/flows/tap-done-button.yaml --no-ansi

# Kill + relaunch — the actual test
xcrun simctl terminate booted "$BUNDLE_ID"
sleep 1
xcrun simctl launch booted "$BUNDLE_ID" >/dev/null
sleep 3

# Assert the signed-in state survived the restart
"$MAESTRO" test .maestro/flows/session-persistence.yaml --no-ansi

echo "=== $(basename "$0") complete ==="
```

- [ ] **Step 3: `chmod +x .maestro/scripts/run-session-persistence.sh`**

- [ ] **Step 4: Run it from a clean simulator**

Run: `.maestro/scripts/run-session-persistence.sh`
Expected: exit 0, screenshot at `/tmp/phase9-session-persistence.png` shows SetupScreen with signed-in UI, no PIN prompt, no crash.

- [ ] **Step 5: Commit**

```bash
git add .maestro/flows/session-persistence.yaml .maestro/scripts/run-session-persistence.sh
git commit -m "feat(phase9-w2): session-persistence kill+relaunch flow"
```

---

### Task 2.3: Connect menu entry flow + state inconsistency capture

**Files:**
- Create: `.maestro/flows/connect-menu-entry.yaml`
- Create: `.maestro/scripts/run-connect-menu-entry.sh`
- Create: `docs/phase9/wave2-state-inconsistency.md`

**Scene-setting:** From the signed-in SetupScreen, tapping `connect_button` opens the Connect menu (`showOpportunities = true` branch in `app/src/commonMain/kotlin/org/commcare/app/App.kt:142`). Scouting on 2026-04-08 found the Connect menu screen renders `errorMessage = "Not signed in to ConnectID"` despite the SetupScreen showing "Signed in to Personal ID ✓" moments earlier. Two sources of truth disagree:

- **SetupScreen** checks `connectIdRepository.isRegistered()` — returns true if a user record exists in storage (`ConnectIdRepository.kt:58`).
- **Connect menu screens** (`OpportunitiesViewModel.kt:83`, `MessagingViewModel.kt:90`) call `tokenManager.getConnectIdToken()` — returns null if either the cached access token is absent/expired OR the ROPC credentials (`KEY_CONNECT_USERNAME` + `KEY_CONNECT_PASSWORD`) aren't stored. See `ConnectIdTokenManager.kt:49-56` and `:66-76`.

After recovery, the repository has a user record (so `isRegistered()` is true), but recovery does not establish a full ROPC credential pair (the recovery flow authenticates via backup code, not password). Result: the signed-in view renders but the token-dependent views can't fetch a token.

**This test documents the current buggy state.** The flow taps the Connect button and asserts the error string is visible. When the bug is fixed, the flow breaks loudly and the fixer updates the assertion to match the correct state. That's a deliberate choice — a failing test after a bug fix is better than a silent regression.

- [ ] **Step 1: Write `docs/phase9/wave2-state-inconsistency.md`** (used as GitHub issue body in Task 2.4)

Content:

```markdown
# Bug: Connect ID state inconsistency between SetupScreen and Connect menu

**Found by:** Phase 9 Wave 2 E2E scouting, 2026-04-08
**Severity:** Medium (user-visible, reproducible, not crashing)
**Affects:** iOS, CommCare.app on simulator, post-recovery flow

## Reproduce

1. Run `.maestro/scripts/run-recovery.sh` successfully (user recovers via backup code).
2. Tap `done_button` on the Success screen.
3. Observe SetupScreen: "GO TO CONNECT MENU" button + "Signed in to Personal ID ✓" text are both visible.
4. Tap "GO TO CONNECT MENU".
5. Observe: the Connect menu screen shows `errorMessage = "Not signed in to ConnectID"`.

Two UI surfaces disagree about whether the same user is signed in.

## Root cause (hypothesis)

Two sources of truth for "is the user signed in":

| Source | Used by | Logic |
|---|---|---|
| `ConnectIdRepository.isRegistered()` | `SetupScreen` via `App.kt:49,63,141` | `getUser() != null` — true if any user record exists |
| `ConnectIdTokenManager.getConnectIdToken()` | `OpportunitiesViewModel`, `MessagingViewModel` | Returns cached token if unexpired, else calls `refreshConnectIdToken()` which needs `KEY_CONNECT_USERNAME` + `KEY_CONNECT_PASSWORD` from the keychain. Returns null on any failure. |

After recovery, the app stores a user record but does NOT persist a usable ROPC password pair — recovery authenticates via backup code, not via the standard password grant. Consequence: `isRegistered()` returns true, but `getConnectIdToken()` returns null, and the two UIs show contradictory states.

## Suggested fix directions

Not a full design, just pointers:

- **Option A:** unify on `getConnectIdToken() != null` as the single "signed in" signal. Forces SetupScreen to show the logged-out variant after recovery, which is wrong in its own way (the user just finished recovering).
- **Option B:** on successful recovery, call `refreshConnectIdToken()` explicitly with the appropriate credentials. This requires the recovery flow to have enough material to mint a token (it may not — recovery trades a backup code for session credentials, which may or may not include what ROPC needs).
- **Option C:** extend the token manager with a "session token from recovery" path that stores a short-lived token without requiring ROPC password. Matches how `send_session_otp` / `confirm_session_otp` already work on the server side.

Prefer C. Least invasive and most honest about what recovery actually produces.

## Test coverage

`.maestro/flows/connect-menu-entry.yaml` documents the current buggy behavior — it asserts the error string is visible. When the bug is fixed, this test will fail and the fixer should update the assertion to the correct state (e.g., assert the opportunities list is visible instead).

## Links

- SetupScreen: `app/src/commonMain/kotlin/org/commcare/app/ui/SetupScreen.kt:96-117,184-202`
- App state wiring: `app/src/commonMain/kotlin/org/commcare/app/App.kt:49,63,141`
- ConnectIdRepository.isRegistered: `app/src/commonMain/kotlin/org/commcare/app/storage/ConnectIdRepository.kt:58`
- ConnectIdTokenManager: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/ConnectIdTokenManager.kt:49-76,174`
- OpportunitiesViewModel error path: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/OpportunitiesViewModel.kt:80-85`
```

- [ ] **Step 2: Write `.maestro/flows/connect-menu-entry.yaml`**

```yaml
# Phase 9 Wave 2: Connect menu entry — documents state inconsistency bug.
#
# Runs AFTER post-recovery SetupScreen is visible (orchestrator handles
# the recovery + done_button tap). Taps the Connect button to open the
# Connect menu, waits for the screen to settle, and captures a screenshot.
#
# Today this asserts the BUGGY state: "Not signed in to ConnectID" is
# visible after the SetupScreen said the user IS signed in. See
# docs/phase9/wave2-state-inconsistency.md for the full writeup.
#
# When the bug is fixed, this flow will fail on the assertNotVisible
# line. The fixer should update the assertion to match the corrected
# behavior (e.g., assertVisible on a content container for the
# opportunities list).

appId: org.marshellis.commcare.ios

---

- extendedWaitUntil:
    visible:
      id: "connect_button"
    timeout: 15000

- tapOn:
    id: "connect_button"

# Give the Connect menu a moment to load and fail
- waitForAnimationToEnd:
    timeout: 3000

- takeScreenshot: /tmp/phase9-connect-menu-entry

# Document the current bug. This assertion is deliberately on the
# BROKEN behavior — it will fail loudly when the bug is fixed.
- assertVisible:
    text: "Not signed in to ConnectID"
```

- [ ] **Step 3: Write `.maestro/scripts/run-connect-menu-entry.sh`**

```bash
#!/usr/bin/env bash
# Phase 9 Wave 2: Connect menu entry orchestrator.
#
# Flow:
#   1. Fresh install + boot
#   2. Recovery part A → OTP → recovery part B → done_button
#   3. Tap done_button (back to SetupScreen)
#   4. connect-menu-entry.yaml: tap Connect button, capture buggy state

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

set -a
source .env.e2e.local
set +a

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
PLAYWRIGHT_SCRIPT="$ROOT/.maestro/scripts/playwright/fetch-otp.js"
APP_PATH="$ROOT/app/iosApp/build/Build/Products/Debug-iphonesimulator/CommCare.app"
BUNDLE_ID="org.marshellis.commcare.ios"

# Fresh install
xcrun simctl terminate booted "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl uninstall booted "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl install booted "$APP_PATH"
xcrun simctl launch booted "$BUNDLE_ID" >/dev/null
sleep 2

# Recovery part A
"$MAESTRO" test \
  -e "CONNECTID_E2E_PHONE=$CONNECTID_E2E_PHONE" \
  -e "CONNECTID_E2E_PHONE_LOCAL=$CONNECTID_E2E_PHONE_LOCAL" \
  -e "CONNECTID_E2E_COUNTRY_CODE=$CONNECTID_E2E_COUNTRY_CODE" \
  -e "CONNECTID_E2E_PIN=$CONNECTID_E2E_PIN" \
  .maestro/flows/connect-id-recovery-to-otp.yaml \
  --no-ansi

OTP="$(node "$PLAYWRIGHT_SCRIPT" "$CONNECTID_E2E_PHONE")"
if ! [[ "$OTP" =~ ^[0-9]{6}$ ]]; then
  echo "ERROR: invalid OTP: '$OTP'" >&2
  exit 2
fi

# Recovery part B
"$MAESTRO" test \
  -e "CONNECTID_E2E_OTP=$OTP" \
  -e "CONNECTID_E2E_BACKUP_CODE=$CONNECTID_E2E_BACKUP_CODE" \
  .maestro/flows/connect-id-recovery-from-otp.yaml \
  --no-ansi

# Tap done_button to return to SetupScreen
"$MAESTRO" test .maestro/flows/tap-done-button.yaml --no-ansi

# Tap Connect button, capture buggy state
"$MAESTRO" test .maestro/flows/connect-menu-entry.yaml --no-ansi

echo "=== $(basename "$0") complete ==="
```

- [ ] **Step 4: `chmod +x .maestro/scripts/run-connect-menu-entry.sh`**

- [ ] **Step 5: Run it from a clean simulator**

Run: `.maestro/scripts/run-connect-menu-entry.sh`
Expected: exit 0, `/tmp/phase9-connect-menu-entry.png` shows the Connect menu screen with the "Not signed in to ConnectID" error.

- [ ] **Step 6: Commit**

```bash
git add .maestro/flows/connect-menu-entry.yaml .maestro/scripts/run-connect-menu-entry.sh docs/phase9/wave2-state-inconsistency.md
git commit -m "test(phase9-w2): connect menu entry flow + state inconsistency bug doc"
```

---

### Task 2.4: File the state inconsistency bug as a GitHub issue

- [ ] **Step 1: Create the issue**

```bash
gh issue create \
  --title "Connect ID state inconsistency: SetupScreen vs Connect menu disagree after recovery" \
  --label "bug,connect-id,phase9" \
  --body-file docs/phase9/wave2-state-inconsistency.md
```

Capture the returned issue number (call it `<ISSUE_N>`).

- [ ] **Step 2: Update the bug doc with its issue number**

Edit `docs/phase9/wave2-state-inconsistency.md` and add at the top:

```markdown
**Tracking issue:** #<ISSUE_N>
```

- [ ] **Step 3: Commit the reference**

```bash
git add docs/phase9/wave2-state-inconsistency.md
git commit -m "docs(phase9-w2): link state inconsistency bug to issue #<ISSUE_N>"
```

---

### Task 2.5: Stability runs

- [ ] **Step 1: Run each orchestrator 5 times back-to-back**

```bash
for i in 1 2 3 4 5; do
  echo "=== post-recovery run $i ==="
  .maestro/scripts/run-post-recovery.sh || { echo "FAIL on run $i"; exit 1; }
done

for i in 1 2 3 4 5; do
  echo "=== session-persistence run $i ==="
  .maestro/scripts/run-session-persistence.sh || { echo "FAIL on run $i"; exit 1; }
done

for i in 1 2 3 4 5; do
  echo "=== connect-menu-entry run $i ==="
  .maestro/scripts/run-connect-menu-entry.sh || { echo "FAIL on run $i"; exit 1; }
done
```

Expected: all 15 runs green.

**If any run flakes:** inspect the screenshot at the step where it failed, adjust timeouts, re-run the full 5 for that script. Do not ship a flaky flow.

---

### Task 2.6: Update CLAUDE.md

- [ ] **Step 1: Update Phase 9 status in CLAUDE.md**

Find the Phase 9 status block in `CLAUDE.md` and add a Wave 2 entry. Reference:

- The plan doc path (this file)
- The wave 2 bug doc path
- The GitHub issue number

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: Phase 9 Wave 2 complete — post-recovery state + session persistence"
```

---

### Task 2.7: Ship as PR

- [ ] **Step 1: Push the branch**

```bash
git push -u origin phase9/wave2-login-variants
```

- [ ] **Step 2: Create the PR**

Use a PR title like: `feat(phase9-w2): post-recovery state + session persistence + state inconsistency bug`

PR body should include:
- What shipped (3 new flows + 3 orchestrators + bug doc + issue link)
- What was deferred and why (PIN login, biometric login — no lockscreen on iOS yet)
- Link to the state inconsistency issue filed in Task 2.4
- Test evidence: screenshots at `/tmp/phase9-*.png` and the 15-run stability results

- [ ] **Step 3: Wait for CI to pass, then merge**

---

## Self-review

- **Spec coverage:** Wave 2 per the spec listed PIN login, biometric, saved-session restore. PIN and biometric are explicitly deferred with reasons (no lockscreen exists to trigger them); saved-session restore is covered by Task 2.2 under the more accurate name "session persistence". Post-recovery state and Connect menu entry were added based on empirical findings — these are strict additions, not replacements.
- **Placeholder scan:** No "TBD" or "similar to above" — each flow is spelled out. The `<ISSUE_N>` placeholder in Task 2.4 is intentional (filled in after the issue is filed).
- **Type consistency:** testTag strings (`connect_button`, `signup_link`, `done_button`, `scan_barcode_button`) all match `SetupScreen.kt` and the existing recovery flow.
- **Follow-ups for future waves:** split the overloaded `signup_link` testTag (needs to coordinate with snapshot tests), fix the state inconsistency bug itself (separate issue, separate wave), revisit PIN/biometric when iOS grows a lockscreen.
