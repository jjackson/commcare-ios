# Phase 9 Wave 3 — CommCare app install + login

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove that a fresh CommCare iOS install can download a real HQ app from a profile URL, log in as a mobile worker, and land on the home screen — fully automated end-to-end against production `www.commcarehq.org`.

**Architecture:** Single-shot Maestro orchestrator following the Wave 2 pattern: one `run-wave3.sh` does `simctl uninstall + install`, then chains three Maestro flows against the same simulator state (install-via-URL → login-to-home → assert landing). No new infrastructure — reuses the XCTest driver cleanup + `MAESTRO_DRIVER_STARTUP_TIMEOUT` tricks from Wave 2. Adds testTags to the three screens touched (LoginScreen, HomeScreen Landing, InstallProgressScreen).

**Spec:** `docs/superpowers/specs/2026-04-08-phase9-e2e-ui-testing-design.md` §7 (Wave 3)

**Status:** Shipped. One product bug caught during execution and filed as #391 (iOS `LoginViewModel.resolveDomain()` hardcodes `"demo"` fallback instead of reading the installed app's domain). Workaround: use full `username@<domain>.commcarehq.org` form in `.env.e2e.local`.

**Also discovered:** the `app/iosApp` Xcode project does NOT have a gradle build phase, so `xcodebuild` does not regenerate the KMP `CommCareApp.framework` when `.kt` files change. A full rebuild requires `./gradlew linkDebugFrameworkIosSimulatorArm64` first, then `xcodebuild ... build`. Documented as a follow-up to add a gradle shell-script build phase to the Xcode project.

**Predecessors:**
- `docs/superpowers/plans/2026-04-08-phase9-e2e-ui-testing.md` — Waves 0 and 1 infrastructure
- `docs/superpowers/plans/2026-04-08-phase9-wave2-login-variants.md` — Wave 2 (split-flow consolidation pattern)

---

## Test target

Confirmed on 2026-04-08 via CommCare HQ APIs:

| Field | Value |
|---|---|
| HQ | `https://www.commcarehq.org` |
| Domain | `jonstest` |
| App | `Bonsaaso Application` (version 9, built 2012-12-14) |
| App ID | `1399c28e016a1ede7228056de4ebb1f5` |
| Profile URL | `https://www.commcarehq.org/a/jonstest/apps/download/1399c28e016a1ede7228056de4ebb1f5/profile.ccpr` |
| Mobile worker | `haltest` (logs in as `haltest@jonstest.commcarehq.org:password` against restore API — verified 200) |

All values live in `.env.e2e.local` under `COMMCARE_*` keys. The web admin `hal@dimagi-ai.com` is stored separately as `COMMCARE_HQ_WEB_USERNAME/PASSWORD` for API discovery only — the mobile app logs in as the mobile worker.

**Why Bonsaaso:**
- Already referenced in the WIP `hq-round-trip.yaml` (we're formalizing an existing loose choice, not inventing a new one).
- Has real modules/forms that exercise case lists and form entry (Pregnancy/Child/Household Lists + Registration).
- The profile URL is anonymous-accessible (no auth required on the iOS install path).
- Small enough to install quickly.

---

## Scope

**In scope for this wave:**
1. Install via direct URL entry (`enter_code_button` → type URL → submit → install).
2. Log in as mobile worker after install.
3. Assert home screen landing (`start_button` visible) — proves install + login + initial restore all succeeded.
4. Add testTags to `LoginScreen`, `HomeScreen` Landing, and `InstallProgressScreen` so the assertions are unambiguous.

**Out of scope (deferred to later sub-waves):**
- Install from HQ list (`install_from_list_button` → list → pick → install). Requires a mobile-worker-authenticated HQ list endpoint the iOS app can hit. Relevant follow-up when we stand up the `InstallFromListScreen` against jonstest.
- Update detection and apply. Layers on top of a working baseline install.
- QR install. Simulator QR injection is painful and the spec explicitly defers it.
- Actually running a form end-to-end (that's Wave 4 — "case list → form entry → submit").
- Subsuming the full `hq-round-trip.yaml` WIP. Wave 3 gets the install+login half; form entry + submission stay in Wave 4.

Calling the sub-wave "Wave 3" because it closes the install gap, not because it does everything in §7 W3. Spec items 2 and 3 become Wave 3b/3c if/when they're needed.

---

## File Map

| File | Action | Purpose |
|---|---|---|
| `app/src/commonMain/kotlin/org/commcare/app/ui/LoginScreen.kt` | Modify | Add `login_button` testTag on the primary Log In button |
| `app/src/commonMain/kotlin/org/commcare/app/ui/HomeScreen.kt` | Modify | Add testTags to Landing: `home_app_name`, `start_button`, `sync_button`, `settings_button`, `diagnostics_button`, `drawer_menu` |
| `app/src/commonMain/kotlin/org/commcare/app/ui/InstallProgressScreen.kt` | Modify | Add testTags: `install_progress_app_name`, `install_cancel_button`, `install_done_label`, `install_failed_label`, `install_retry_button` |
| `.env.e2e.local.example` | Modify | Document the new `COMMCARE_*` keys |
| `.maestro/flows/install-via-url.yaml` | Create | Type profile URL into SetupScreen, submit, wait for login screen |
| `.maestro/flows/login-to-home.yaml` | Create | Type mobile worker creds, tap Login, wait for home Start button |
| `.maestro/scripts/run-wave3.sh` | Create | Orchestrator: fresh install → install-via-url → login-to-home → assert home |
| `docs/phase9/fixture-user.md` | Modify | Add a "HQ test app" section pointing at jonstest/Bonsaaso |
| `CLAUDE.md` | Modify | Mark Wave 3 complete, link plan + update "next wave" hint |

---

## Acceptance Criteria

| Check | Verification | Pass condition |
|---|---|---|
| A | Bonsaaso profile.ccpr anonymous-accessible | `curl -s -o /dev/null -w "%{http_code}" "$COMMCARE_APP_PROFILE_URL"` → 200 (verified 2026-04-08) |
| B | Mobile worker login succeeds against HQ | `curl -u haltest@jonstest.commcarehq.org:password ".../a/jonstest/phone/restore/?version=2.0"` → 200 (verified 2026-04-08) |
| C | `run-wave3.sh` exits 0 with a fresh simulator | Install progress screen visible, login screen visible, Start button visible on home |
| D | 2 back-to-back stability runs of `run-wave3.sh` green | Both pass, ~90-120s each (install downloads + first restore are slower than the Wave 2 recovery-only flow) |
| E | All new testTags are on `commonMain/` elements | `grep testTag` on the three modified files shows the new names |

---

## Risks and mitigations

1. **Install time variability.** Bonsaaso is small but the HTTP reference factory + dozens of XML resources take time. Mitigation: `extendedWaitUntil` on the login screen with a generous 180s timeout (matches the existing `login-with-app.yaml` precedent).
2. **Restore time variability.** First-login OTA restore for a mobile worker that has no existing casedata is usually fast, but HQ latency can spike. Mitigation: 180s timeout on the home `start_button`.
3. **Bonsaaso is version 9 built in 2012.** It predates some CommCare features and uses `requiredMajor=2`, `requiredMinor=23` — well within what the iOS app supports. Confirmed from profile.ccpr content.
4. **testTag additions touch production code on master.** Small additive change (no behavior), but the iOS app rebuild + reinstall is required. Build time is the long pole — budget extra runtime for the first Wave 3 run.
5. **Simulator is shared state.** Wave 3 runs uninstall+install, so no state bleed from Wave 1/2 runs. Each run starts clean.

---

## Wave 3 — Task Breakdown

### Task 3.1: Add testTags to the three screens

**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/LoginScreen.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/HomeScreen.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/InstallProgressScreen.kt`

**Scene-setting:** Per spec §8.1, every interactive element should have a testTag. LoginScreen and HomeScreen Landing currently don't tag their primary Log In / Start / Sync / Settings / Diagnostics buttons. InstallProgressScreen has no testTags at all. Wave 3 tests cannot run reliably without these.

Naming convention: `snake_case`, action-oriented, stable across visual refactors. No "button" suffix on action buttons when the noun already implies action (e.g., `start_button` is fine, but prefer `install_cancel_button` over `cancel_button` for disambiguation from other Cancel buttons on other screens).

- [ ] **Step 1: LoginScreen — add `login_button` testTag**

Edit the primary Button in `LoginScreen.kt` (line ~226):

```kotlin
Button(
    onClick = { viewModel.login() },
    modifier = Modifier.fillMaxWidth().testTag("login_button"),
    enabled = viewModel.username.isNotBlank() && viewModel.password.isNotBlank()
) {
    Text("Log In")
}
```

- [ ] **Step 2: HomeScreen Landing — add testTags to the top-level buttons + app name**

Edit `HomeLandingContent` in `HomeScreen.kt` (line ~420+):

```kotlin
// Hamburger menu
Text(
    text = "\u2630",
    modifier = Modifier
        .clickable { onOpenDrawer() }
        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
        .padding(end = 8.dp)
        .testTag("drawer_menu"),
    ...
)

// App name label
Text(
    text = appName,
    modifier = Modifier.testTag("home_app_name"),
    ...
)

// Start
Button(
    onClick = onStart,
    modifier = Modifier.fillMaxWidth().testTag("start_button")
) { Text("Start") }

// Sync
OutlinedButton(
    onClick = onSync,
    modifier = Modifier.fillMaxWidth().testTag("sync_button")
) { Text("Sync") }

// Settings
OutlinedButton(
    onClick = onSettings,
    modifier = Modifier.weight(1f).testTag("settings_button")
) { Text("Settings") }

// Diagnostics
OutlinedButton(
    onClick = onDiagnostics,
    modifier = Modifier.weight(1f).testTag("diagnostics_button")
) { Text("Diagnostics") }
```

- [ ] **Step 3: InstallProgressScreen — add testTags to progress, done, and failed content**

Edit `InstallProgressScreen.kt`:

```kotlin
// InstallProgressContent — on the app name Text
Text(
    text = appName,
    style = MaterialTheme.typography.headlineMedium,
    modifier = Modifier.testTag("install_progress_app_name")
)

// InstallProgressContent — on the Cancel button
OutlinedButton(
    onClick = onCancel,
    modifier = Modifier.fillMaxWidth().testTag("install_cancel_button")
) { Text("Cancel") }

// InstallDoneContent — on the "X installed" Text
Text(
    text = "$appName installed",
    style = MaterialTheme.typography.headlineMedium,
    modifier = Modifier.testTag("install_done_label")
)

// InstallFailedContent — on the title and both buttons
Text(
    text = "Installation Failed",
    style = MaterialTheme.typography.headlineMedium,
    color = MaterialTheme.colorScheme.error,
    modifier = Modifier.testTag("install_failed_label")
)
Button(
    onClick = onRetry,
    modifier = Modifier.fillMaxWidth().testTag("install_retry_button")
) { Text("Retry") }
OutlinedButton(
    onClick = onCancel,
    modifier = Modifier.fillMaxWidth().testTag("install_cancel_button")
) { Text("Cancel") }
```

- [ ] **Step 4: Rebuild the iOS app**

Run:
```bash
cd app/iosApp
xcodebuild -scheme CommCare -destination 'id=AF5C58C3-794F-46BF-B941-3568117B8172' \
  -configuration Debug \
  -derivedDataPath build \
  build 2>&1 | tail -10
```
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 5: Commit**

```bash
git add app/src/commonMain/kotlin/org/commcare/app/ui/LoginScreen.kt \
        app/src/commonMain/kotlin/org/commcare/app/ui/HomeScreen.kt \
        app/src/commonMain/kotlin/org/commcare/app/ui/InstallProgressScreen.kt
git commit -m "feat(phase9-w3): add testTags to LoginScreen, HomeScreen, InstallProgressScreen"
```

---

### Task 3.2: `install-via-url.yaml` Maestro flow

**Files:**
- Create: `.maestro/flows/install-via-url.yaml`

**Scene-setting:** Starts on SetupScreen (post-`simctl install`). Navigates: `enter_code_button` → EnterCodeScreen → `profile_url_field` → `submit_button` → back to SetupScreen with URL populated → `install_button` → InstallProgressScreen runs → completed state flips `hasApps=true` → login screen appears.

The flow ends when the login screen is visible (`username_field` + `password_field` + `login_button`). Login itself is in Task 3.3.

**Gotcha:** The EnterCodeScreen's `submit_button` calls `setupViewModel.onCodeEntered(urlText)` AND navigates back to main. After that, SetupScreen shows the `install_button` conditionally on `setupViewModel.profileUrl.isNotBlank()`. The flow must wait for `install_button` to appear before tapping.

- [ ] **Step 1: Write the flow**

```yaml
# Phase 9 Wave 3: install Bonsaaso app via direct URL entry.
#
# Starting state: fresh SetupScreen (post simctl install + launch).
# Ending state: login screen visible (username_field + login_button).
#
# Orchestrator passes the profile URL via -e COMMCARE_APP_PROFILE_URL.

appId: org.marshellis.commcare.ios

---

# Wait for SetupScreen to be interactable
- extendedWaitUntil:
    visible:
      id: "enter_code_button"
    timeout: 30000

# Navigate to Enter Code screen
- tapOn:
    id: "enter_code_button"

# Type the profile URL
- extendedWaitUntil:
    visible:
      id: "profile_url_field"
    timeout: 10000

- tapOn:
    id: "profile_url_field"
- inputText: ${COMMCARE_APP_PROFILE_URL}

# Dismiss keyboard (consistent with Wave 1/2 pattern)
- tapOn:
    point: "50%,10%"
- waitForAnimationToEnd:
    timeout: 1000

# Submit — navigates back to SetupScreen with profileUrl populated
- tapOn:
    id: "submit_button"

# Wait for SetupScreen's install_button (only visible when profileUrl is set)
- extendedWaitUntil:
    visible:
      id: "install_button"
    timeout: 15000

- tapOn:
    id: "install_button"

# InstallProgressScreen — wait for the app to finish downloading + initializing.
# Budget 180s because the first install downloads dozens of resources + suite XML.
# We don't assert on install steps individually — the terminal signal is the
# transition to the login screen, which only renders once InstallState.Completed
# flips hasApps=true in the parent.
- extendedWaitUntil:
    visible:
      id: "login_button"
    timeout: 180000

- assertVisible:
    id: "username_field"

- assertVisible:
    id: "password_field"

- takeScreenshot: /tmp/phase9-wave3-post-install
```

- [ ] **Step 2: Commit**

```bash
git add .maestro/flows/install-via-url.yaml
git commit -m "feat(phase9-w3): install-via-url Maestro flow"
```

---

### Task 3.3: `login-to-home.yaml` Maestro flow

**Files:**
- Create: `.maestro/flows/login-to-home.yaml`

**Scene-setting:** Starts on login screen (post-install, SetupFlow's `hasApps=true` branch in App.kt has routed to LoginScreen). Types the mobile worker creds from env vars, taps `login_button`, waits for `start_button` on the home screen.

- [ ] **Step 1: Write the flow**

```yaml
# Phase 9 Wave 3: log in as mobile worker and land on home screen.
#
# Starting state: login screen visible after install-via-url.yaml.
# Ending state: home screen with Start button visible.
#
# Orchestrator passes COMMCARE_MOBILE_USERNAME and COMMCARE_MOBILE_PASSWORD.

appId: org.marshellis.commcare.ios

---

- extendedWaitUntil:
    visible:
      id: "username_field"
    timeout: 15000

- tapOn:
    id: "username_field"
- inputText: ${COMMCARE_MOBILE_USERNAME}

- tapOn:
    point: "50%,10%"
- waitForAnimationToEnd:
    timeout: 1000

- tapOn:
    id: "password_field"
- inputText: ${COMMCARE_MOBILE_PASSWORD}

- tapOn:
    point: "50%,10%"
- waitForAnimationToEnd:
    timeout: 1000

- tapOn:
    id: "login_button"

# Home screen Start button — 180s timeout to cover initial OTA restore.
- extendedWaitUntil:
    visible:
      id: "start_button"
    timeout: 180000

- assertVisible:
    id: "start_button"

- assertVisible:
    id: "sync_button"

- assertVisible:
    id: "home_app_name"

- takeScreenshot: /tmp/phase9-wave3-home-landing
```

- [ ] **Step 2: Commit**

```bash
git add .maestro/flows/login-to-home.yaml
git commit -m "feat(phase9-w3): login-to-home Maestro flow"
```

---

### Task 3.4: `run-wave3.sh` orchestrator

**Files:**
- Create: `.maestro/scripts/run-wave3.sh`

- [ ] **Step 1: Write the orchestrator**

```bash
#!/usr/bin/env bash
# Phase 9 Wave 3: fresh install → install Bonsaaso via URL → log in → home.
#
# Proves end-to-end that:
#   1. A fresh iOS install can install a real HQ app from a profile URL
#   2. A mobile worker can log in after install
#   3. The home screen renders with casedata after first restore
#
# Reuses the Wave 2 pattern: single orchestrator, one fresh install, flows
# chained in sequence. Budget is ~90-120s.
#
# Usage: .maestro/scripts/run-wave3.sh

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

if [ ! -f .env.e2e.local ]; then
  echo "ERROR: .env.e2e.local not found" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env.e2e.local
set +a

: "${COMMCARE_APP_PROFILE_URL:?must be set in .env.e2e.local}"
: "${COMMCARE_MOBILE_USERNAME:?must be set in .env.e2e.local}"
: "${COMMCARE_MOBILE_PASSWORD:?must be set in .env.e2e.local}"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export MAESTRO_DRIVER_STARTUP_TIMEOUT="${MAESTRO_DRIVER_STARTUP_TIMEOUT:-180000}"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
APP_PATH="$ROOT/app/iosApp/build/Build/Products/Debug-iphonesimulator/CommCare.app"
BUNDLE_ID="org.marshellis.commcare.ios"

echo "=== Phase 9 Wave 3: CommCare app install + login ==="
echo "Profile URL: $COMMCARE_APP_PROFILE_URL"
echo "Mobile user: $COMMCARE_MOBILE_USERNAME"
echo ""

# Kill stale Maestro driver from prior runs
pkill -f "maestro-driver-iosUITests-Runner" 2>/dev/null || true
pkill -f "xcodebuild test-without-building.*maestro" 2>/dev/null || true
sleep 1

# Fresh install
xcrun simctl terminate booted "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl uninstall booted "$BUNDLE_ID" 2>/dev/null || true
if [ ! -d "$APP_PATH" ]; then
  echo "ERROR: $APP_PATH not found. Build with xcodebuild first." >&2
  exit 1
fi
xcrun simctl install booted "$APP_PATH"
xcrun simctl launch booted "$BUNDLE_ID" >/dev/null
sleep 2

echo "--- Step 1: Install Bonsaaso via profile URL ---"
"$MAESTRO" test \
  -e "COMMCARE_APP_PROFILE_URL=$COMMCARE_APP_PROFILE_URL" \
  .maestro/flows/install-via-url.yaml \
  --no-ansi

echo ""
echo "--- Step 2: Log in as mobile worker ---"
"$MAESTRO" test \
  -e "COMMCARE_MOBILE_USERNAME=$COMMCARE_MOBILE_USERNAME" \
  -e "COMMCARE_MOBILE_PASSWORD=$COMMCARE_MOBILE_PASSWORD" \
  .maestro/flows/login-to-home.yaml \
  --no-ansi

echo ""
echo "=== Wave 3 complete ==="
echo "Screenshots:"
echo "  /tmp/phase9-wave3-post-install.png"
echo "  /tmp/phase9-wave3-home-landing.png"
```

- [ ] **Step 2: Make executable + commit**

```bash
chmod +x .maestro/scripts/run-wave3.sh
git add .maestro/scripts/run-wave3.sh
git commit -m "feat(phase9-w3): run-wave3.sh orchestrator — install + login"
```

---

### Task 3.5: Run the orchestrator against a fresh simulator

- [ ] **Step 1: Run it once**

```bash
.maestro/scripts/run-wave3.sh
```

Expected: `=== Wave 3 complete ===`, `/tmp/phase9-wave3-post-install.png` shows login screen, `/tmp/phase9-wave3-home-landing.png` shows home with Start button.

**If install hangs:** check InstallProgressScreen; if a step is FAILED, capture the error text and investigate. Most likely culprit: an HQ resource URL that the iOS HTTP reference factory can't resolve. Cross-reference `docs/learnings/2026-03-18-ios-platform-test-gap-learnings.md`.

**If login fails:** verify the mobile worker creds manually against the restore endpoint (already done on 2026-04-08 — `haltest@jonstest.commcarehq.org:password` → 200).

- [ ] **Step 2: Run 2 more times for stability**

```bash
for i in 1 2; do
  echo "##### stability run $i #####"
  time .maestro/scripts/run-wave3.sh > /tmp/phase9-wave3-stability-$i.log 2>&1 || {
    tail -20 /tmp/phase9-wave3-stability-$i.log
    exit 1
  }
done
```

Expected: both runs green, each ~90-120s.

---

### Task 3.6: Update docs

- [ ] **Step 1: Update `docs/phase9/fixture-user.md` with the HQ test app section**

Add a new section under the Connect ID fixture describing the jonstest/Bonsaaso test app and what credentials are needed.

- [ ] **Step 2: Update `.env.e2e.local.example`**

Add the new `COMMCARE_*` keys with placeholder values and a one-line comment each.

- [ ] **Step 3: Update `CLAUDE.md`**

Mark Wave 3 complete in the Phase 9 status line; link this plan doc; update "next natural step" from Wave 3 to Wave 4 (case list → form entry).

- [ ] **Step 4: Commit**

```bash
git add docs/phase9/fixture-user.md .env.e2e.local.example CLAUDE.md
git commit -m "docs(phase9-w3): Wave 3 complete — jonstest/Bonsaaso test app"
```

---

### Task 3.7: Ship as PR

- [ ] **Step 1: Push branch**

```bash
git push -u origin phase9/wave3-app-install
```

- [ ] **Step 2: Open PR**

Title: `feat(phase9-w3): install Bonsaaso + login-to-home end-to-end`

Body highlights:
- What shipped: install-via-url + login-to-home flows, run-wave3.sh orchestrator, testTags
- Test target: jonstest/Bonsaaso Application + haltest mobile worker
- Stability: 3 runs green (~90-120s each)
- Scope note: deferred install-from-list + update detection to Wave 3b/3c

---

## Self-review

- **Spec coverage (§7 W3):** direct URL entry ✓. Install from HQ list and update detection deferred with reasoning.
- **testTag policy (§8.1):** three new screens tagged, all interactive elements covered. Documents the `login_button`, `start_button`, etc. names for future flows.
- **Placeholder scan:** no `TBD` / `similar to above`. Each flow and script is spelled out fully.
- **Type consistency:** testTag names referenced in flows match exactly what the Kotlin edits introduce.
- **Credential hygiene:** no credentials in this doc — all values are referenced by env var name.
- **Follow-ups:**
  - Wave 3b: install from HQ list (needs `InstallFromListScreen` + backend wiring investigation)
  - Wave 3c: update detection + apply
  - Wave 4: case list → form entry → submit (starts from the state Wave 3 leaves: home screen with Start button)
  - Consider a `login_loading_spinner` testTag when we start needing to assert "login is in progress" (not needed for Wave 3 since we wait for the terminal state)
