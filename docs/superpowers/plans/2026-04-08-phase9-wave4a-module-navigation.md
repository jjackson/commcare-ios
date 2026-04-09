# Phase 9 Wave 4a — Module list, case list, back navigation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Prove end-to-end that a logged-in mobile worker can navigate from the home screen through the Bonsaaso module list into a case list, see the expected "No cases found" baseline state, and walk back to the home screen — all against a fresh install with a live OTA restore.

**Architecture:** Extend the Wave 3 orchestrator pattern. `run-wave4.sh` chains: Wave 3's install + login → Wave 4a's navigation flow. No new infrastructure. The navigation flow asserts static text, static testTags, and the known "No cases found" state (mobile worker has no casedata for jonstest).

**Spec:** `docs/superpowers/specs/2026-04-08-phase9-e2e-ui-testing-design.md` §7 (Wave 4)

**Predecessor:** `docs/superpowers/plans/2026-04-08-phase9-wave3-app-install.md` — Wave 3 install + login

## Scope

This is **Wave 4a**, a narrow first slice of §7 W4. The full spec W4 is "install → sync → case list → form entry → submit → HQ verify", which is too much for one PR. Splitting:

- **Wave 4a (this plan):** module list + sub-menu + case list + back nav. No form entry, no submit, no HQ verify.
- **Wave 4b (follow-up):** navigate into Register Household form, fill questions, submit, poll HQ to verify the submission landed.
- **Wave 4c (follow-up):** form workflow variants — Save Draft, Back mid-form, repeat groups, swipe nav.

Wave 4a's value: proves sync/restore delivered the Bonsaaso app data (module list is populated, not empty), CommCare menu navigation works past the first level (sub-menus work), case list rendering works (including empty state), and back-navigation is wired correctly (it's actually unusual — see "Gotcha" below).

**Out of scope for Wave 4a:**
- Form entry into any form
- Submitting anything
- Verifying state on HQ
- Asserting on specific case data (there is none for this worker)
- Search functionality
- Sort toggle

## Scouting findings (2026-04-08)

Executed live against jonstest + Bonsaaso to calibrate testTags and asserts.

**Module list** (after tapping Start from home):
- Title: "CommCare"
- Back arrow: `<`
- Four modules, each a `Card` row: `${0} Pregnancy List`, `${0} Child List`, `${0} Household List`, `${0} Registration`
- The `${0}` prefix is a broken badge-count placeholder (should render as "(0)" or similar). Not a Wave 4a issue — file as a follow-up.
- Each row ends with `>` indicating it's a submenu.

**Household List sub-menu** (after tapping "Household List"):
- Title: "${0} Household List"
- Back arrow: `<`
- Four entries: `${0} Visit`, `${0} Close`, `${0} Edit Registration`, `${0} Death without Registration`

**Case list** (after tapping "Visit"):
- Title: "Select Case"
- Back arrow: `<`
- Search field (OutlinedTextField with label "Search")
- Count label: "0 cases"
- Sort toggle: "Name A-Z"
- Empty state: "No cases found"

**Gotcha — back navigation from case list:** Tapping `<` from the case list goes DIRECTLY to the module list, NOT to the Household List sub-menu. CommCare's menu-entry-into-caselist flow treats the case list as the primary view for that entry point, and back exits the whole chain. This surprised me during scouting — the scout flow initially asserted "Visit" visible after one back tap and failed because the screen had jumped back two levels.

So the back sequence is:
1. Case list → `<` → Module list (skips Household List sub-menu)
2. Module list → `<` → Home

Two back taps total, not three.

## File Map

| File | Action | Purpose |
|---|---|---|
| `app/src/commonMain/kotlin/org/commcare/app/ui/MenuScreen.kt` | Modify | Add testTags: `menu_title`, `menu_back`, `menu_list` |
| `app/src/commonMain/kotlin/org/commcare/app/ui/CaseListScreen.kt` | Modify | Add testTags: `case_list_title`, `case_list_back`, `case_list_search`, `no_cases_label` |
| `.maestro/flows/module-nav.yaml` | Create | Home → Start → Household List → Visit → case list → back chain → home |
| `.maestro/scripts/run-wave4.sh` | Create | Orchestrator: run Wave 3 install+login, then module-nav.yaml |
| `docs/superpowers/plans/2026-04-08-phase9-wave4a-module-navigation.md` | Create | This plan |
| `CLAUDE.md` | Modify | Mark Wave 4a complete, link plan |

Wave 4a does not modify `run-wave3.sh` — the Wave 4 orchestrator treats Wave 3 as a setup step and calls Wave 3's flows as nested Maestro invocations.

## Acceptance Criteria

| Check | Verification | Pass condition |
|---|---|---|
| A | `.maestro/scripts/run-wave4.sh` exits 0 with a fresh simulator | Install succeeds, login succeeds, module list visible, case list "No cases found" visible, back chain returns to home |
| B | 2 back-to-back stability runs of `run-wave4.sh` | Both pass |
| C | `grep testTag MenuScreen.kt CaseListScreen.kt` shows the new names | At least 7 new testTag invocations |

Wave 4a deliberately does NOT assert stability > 2 runs — the total runtime is ~60s + the ~43s Wave 3 prefix = ~100s per run, so 2 back-to-back is enough evidence.

## Task Breakdown

### Task 4a.1: Add testTags to MenuScreen and CaseListScreen

**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/MenuScreen.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/CaseListScreen.kt`

**Scene-setting:** Both screens have zero testTags today (confirmed via `grep -n testTag`). Wave 4a only needs tags on the static structural elements, not on individual menu rows or case rows — those are dynamic and can be targeted via text matching with `text: ".*Household List.*"` etc.

- [ ] **Step 1: MenuScreen.kt — add `testTag` import + tags on title, back, list**

```kotlin
// Imports
import androidx.compose.ui.platform.testTag

// Title (around line 57)
Text(
    text = viewModel.title,
    style = MaterialTheme.typography.headlineMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.testTag("menu_title")
)

// Back arrow (around line 49)
Text(
    text = "<",
    style = MaterialTheme.typography.headlineSmall,
    modifier = Modifier.clickable { onBack() }
        .defaultMinSize(minHeight = 44.dp, minWidth = 44.dp)
        .padding(end = 8.dp)
        .testTag("menu_back")
)

// LazyColumn (around line 82)
LazyColumn(modifier = Modifier.fillMaxSize().testTag("menu_list")) { ... }
```

Note: `GridMenuScreen` also exists for style="grid" menus. Bonsaaso uses the list variant, so Wave 4a only tags `ListMenuScreen`. Grid menu tags can come with a future wave that uses a grid-style app.

- [ ] **Step 2: CaseListScreen.kt — add testTags on title, back, search, no-cases label**

```kotlin
// Title (around line 64)
Text(
    text = title,
    style = MaterialTheme.typography.headlineMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.testTag("case_list_title")
)

// Back arrow (around line 57)
Text(
    text = "<",
    style = MaterialTheme.typography.headlineSmall,
    modifier = Modifier.clickable { onBack() }
        .defaultMinSize(minHeight = 44.dp, minWidth = 44.dp)
        .padding(end = 8.dp)
        .testTag("case_list_back")
)

// Search field (around line 85)
OutlinedTextField(
    ...
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("case_list_search"),
    ...
)

// No cases label (around line 131)
Text(
    text = "No cases found",
    modifier = Modifier.padding(16.dp).testTag("no_cases_label"),
    ...
)
```

- [ ] **Step 3: Rebuild the KMP framework AND the iOS app**

Critical: two-step build per Wave 3 learnings. `xcodebuild` alone does NOT regenerate the KMP framework.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./app/gradlew -p ./app linkDebugFrameworkIosSimulatorArm64
cd app/iosApp && xcodebuild -scheme CommCare -destination 'id=AF5C58C3-794F-46BF-B941-3568117B8172' -configuration Debug -derivedDataPath build build
```

Expected: `BUILD SUCCESSFUL` from gradle, `** BUILD SUCCEEDED **` from xcodebuild.

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/org/commcare/app/ui/MenuScreen.kt app/src/commonMain/kotlin/org/commcare/app/ui/CaseListScreen.kt
git commit -m "feat(phase9-w4a): add testTags to MenuScreen and CaseListScreen"
```

---

### Task 4a.2: module-nav.yaml Maestro flow

**Files:**
- Create: `.maestro/flows/module-nav.yaml`

**Scene-setting:** Starts from the home screen post-login (orchestrator handles install + login first). Walks home → Start → module list → Household List → Visit → case list → back → module list → back → home. Assertion points: every step that changes screens.

**Selector strategy:** module list and sub-menu items are dynamic (populated from app data), so Wave 4a uses text matching for the tap targets but testTag-based assertions for the static chrome (title, back, no-cases label). This is the pragmatic choice — tagging every menu row would require a dynamic testTag strategy, which adds complexity for no win.

- [ ] **Step 1: Write the flow**

```yaml
# Phase 9 Wave 4a: module navigation baseline.
#
# Starting state: home screen with start_button visible (post-login).
# Walks: home → Start → Household List → Visit → case list → back → module list → back → home.
# Asserts: every transition lands on the expected screen.
#
# Why Household List → Visit:
# - Visit is a case-list entry point (not a registration form)
# - With zero cases (fresh mobile worker), we can assert on the static
#   "No cases found" label — deterministic regardless of HQ state

appId: org.marshellis.commcare.ios

---

- extendedWaitUntil:
    visible:
      id: "start_button"
    timeout: 15000

# Enter the module list
- tapOn:
    id: "start_button"
- waitForAnimationToEnd:
    timeout: 3000

- extendedWaitUntil:
    visible:
      id: "menu_title"
    timeout: 10000

- assertVisible: ".*Pregnancy List.*"
- assertVisible: ".*Household List.*"

# Drill into Household List sub-menu
- tapOn:
    text: ".*Household List.*"
- waitForAnimationToEnd:
    timeout: 3000

- assertVisible:
    id: "menu_title"
- assertVisible: ".*Visit.*"
- assertVisible: ".*Close.*"

# Drill into Visit → case list
- tapOn:
    text: ".*Visit.*"
- waitForAnimationToEnd:
    timeout: 5000

- extendedWaitUntil:
    visible:
      id: "case_list_title"
    timeout: 10000

- assertVisible:
    id: "case_list_search"

- assertVisible:
    id: "no_cases_label"

- takeScreenshot: /tmp/phase9-wave4-case-list

# Back from case list → module list (skips Household List sub-menu by design)
- tapOn:
    id: "case_list_back"
- waitForAnimationToEnd:
    timeout: 2000

- extendedWaitUntil:
    visible:
      id: "menu_title"
    timeout: 5000

- assertVisible: ".*Pregnancy List.*"
- assertVisible: ".*Household List.*"

# Back from module list → home
- tapOn:
    id: "menu_back"
- waitForAnimationToEnd:
    timeout: 2000

- extendedWaitUntil:
    visible:
      id: "start_button"
    timeout: 10000

- assertVisible:
    id: "start_button"

- takeScreenshot: /tmp/phase9-wave4-back-at-home
```

- [ ] **Step 2: Commit**

```bash
git add .maestro/flows/module-nav.yaml
git commit -m "feat(phase9-w4a): module navigation flow"
```

---

### Task 4a.3: run-wave4.sh orchestrator

**Files:**
- Create: `.maestro/scripts/run-wave4.sh`

**Scene-setting:** Reuses the Wave 3 orchestrator's setup steps inline (rather than shelling out to `run-wave3.sh`) so we don't depend on the stability of the other script's exit codes in a nested invocation. The whole thing is still one orchestrator with one Maestro driver lifecycle.

- [ ] **Step 1: Write it**

```bash
#!/usr/bin/env bash
# Phase 9 Wave 4a: fresh install → log in → module navigation baseline.
#
# Chains Wave 3's install+login with Wave 4a's navigation flow. One
# orchestrator, one fresh install, ~100s total.

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

echo "=== Phase 9 Wave 4a: install + login + module navigation ==="

# Kill stale driver + fresh simulator install (Wave 3 setup)
pkill -f "maestro-driver-iosUITests-Runner" 2>/dev/null || true
pkill -f "xcodebuild test-without-building.*maestro" 2>/dev/null || true
sleep 1

xcrun simctl terminate booted "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl uninstall booted "$BUNDLE_ID" 2>/dev/null || true
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
echo "--- Step 3: Module navigation baseline ---"
"$MAESTRO" test .maestro/flows/module-nav.yaml --no-ansi

echo ""
echo "=== Wave 4a complete ==="
echo "Screenshots:"
echo "  /tmp/phase9-wave4-case-list.png"
echo "  /tmp/phase9-wave4-back-at-home.png"
```

- [ ] **Step 2: Make executable + commit**

```bash
chmod +x .maestro/scripts/run-wave4.sh
git add .maestro/scripts/run-wave4.sh
git commit -m "feat(phase9-w4a): run-wave4.sh orchestrator"
```

---

### Task 4a.4: Run + stability

- [ ] **Step 1: Run it once**

```bash
.maestro/scripts/run-wave4.sh
```

Expected: exit 0, `Wave 4a complete`, both screenshots written.

- [ ] **Step 2: Run 2 more times for stability**

```bash
for i in 1 2; do
  time .maestro/scripts/run-wave4.sh > /tmp/phase9-wave4a-stability-$i.log 2>&1 || {
    tail -20 /tmp/phase9-wave4a-stability-$i.log
    exit 1
  }
done
```

Expected: both runs green.

---

### Task 4a.5: Docs + PR

- [ ] **Step 1: Update CLAUDE.md**

Mark Wave 4a complete in the Phase 9 status line; update "next natural step" from Wave 4 to Wave 4b (form entry + submit).

- [ ] **Step 2: Commit docs**

```bash
git add CLAUDE.md docs/superpowers/plans/2026-04-08-phase9-wave4a-module-navigation.md
git commit -m "docs(phase9-w4a): Wave 4a complete — module + case list navigation"
```

- [ ] **Step 3: Push + PR**

```bash
git push -u origin phase9/wave4-form-entry
gh pr create --title "feat(phase9-w4a): module list + case list navigation baseline" ...
```

---

## Self-review

- **Spec coverage:** covers one slice of §7 W4 (navigation through the live app). Form entry and submit are explicitly deferred to Wave 4b/4c.
- **Placeholder scan:** none. All flow steps spelled out; testTag names match between Kotlin edits and Maestro asserts.
- **testTag policy:** screens tagged with static-structure tags only; dynamic menu/case rows use text matching (pragmatic since Maestro can't filter by tag equality and dynamic tags add complexity without payoff).
- **Follow-ups:**
  - Wave 4b: form entry into Register Household, fill questions, submit, HQ verify
  - Wave 4c: form workflow variants
  - File a product bug for the `${0}` badge count placeholder (Bonsaaso renders `${0} Pregnancy List` instead of `(0) Pregnancy List`)
  - Add testTags to GridMenuScreen when a grid-layout app enters the test surface
