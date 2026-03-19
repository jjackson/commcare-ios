# Phase 5 Wave 1: App Icon + Login Cleanup — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a proper CommCare app icon and simplify the login screen to match Android (username + password only, domain extracted from username).

**Architecture:** Replace the developer-facing login form (5 fields) with a clean 2-field login matching Android. The app icon requires creating an `Assets.xcassets` catalog and referencing it in the Xcode project. The `AppState` structural migration (new sealed class variants, `ApplicationRecord`) is deferred to Wave 2 when the setup flow and SQLDelight schema changes give it a purpose. Wave 1 is purely cosmetic — same behavior, better UI.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Xcode/xcassets

**Spec:** `docs/plans/2026-03-19-phase5-android-ux-parity-spec.md` (Wave 1 section)

**Descoped from Wave 1 (moved to Wave 2):**
- `AppState` structural changes (new sealed class variants, `ApplicationRecord` data class)
- Updating `AppState.Ready` consumers (HomeScreen, DemoModeManager, etc.)
- SQLDelight schema changes (`application_record` table)
These require the setup flow infrastructure that Wave 2 provides. Adding them now would create dead code.

**Wave 1 user journey:** Login still works exactly as before — user enters `username@domain.commcarehq.org` + password. The ViewModel internally defaults `serverUrl` and constructs the profile URL from an optional `appId`. For real HQ logins with a specific app, the user can still set `appId` via the configureApp() method (used by future Wave 2 setup flow). Without an appId, the app creates a minimal platform (development mode).

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json` | Create | Icon catalog metadata |
| `app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/icon-1024.png` | Create | 1024x1024 master icon |
| `app/iosApp/iosApp/Assets.xcassets/Contents.json` | Create | Asset catalog root |
| `app/iosApp/project.yml` | Modify | Add Assets.xcassets source reference |
| `app/src/commonMain/kotlin/org/commcare/app/ui/LoginScreen.kt` | Modify | Remove Domain/AppID/ServerURL fields |
| `app/src/commonMain/kotlin/org/commcare/app/viewmodel/LoginViewModel.kt` | Modify | Remove extra fields, extract domain from username |
| `.maestro/flows/*.yaml` | Modify | Update for simplified login |

Note: `AppState.kt`, `App.kt`, `HomeScreen.kt`, `DemoModeManager.kt` are NOT modified in Wave 1. The AppState structural migration happens in Wave 2.

---

## Task 1: Create App Icon Assets

**Files:**
- Create: `app/iosApp/iosApp/Assets.xcassets/Contents.json`
- Create: `app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json`
- Create: `app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/icon-1024.png`
- Modify: `app/iosApp/project.yml`

- [ ] **Step 1: Create Assets.xcassets directory structure**

```bash
mkdir -p app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset
```

- [ ] **Step 2: Create asset catalog root Contents.json**

Write to `app/iosApp/iosApp/Assets.xcassets/Contents.json`:

```json
{
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

- [ ] **Step 3: Create AppIcon.appiconset/Contents.json**

iOS 16+ only needs a single 1024x1024 icon. Write to `app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json`:

```json
{
  "images" : [
    {
      "filename" : "icon-1024.png",
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "1024x1024"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

- [ ] **Step 4: Generate a 1024x1024 CommCare icon**

Use Python with Pillow to create a CommCare-branded icon (blue/purple gradient with "CC" text). This is a placeholder — can be replaced with the real CommCare icon later.

```bash
python3 -c "
from PIL import Image, ImageDraw, ImageFont
import math

size = 1024
img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)

# Blue-purple gradient background with rounded corners
for y in range(size):
    r = int(74 + (108 - 74) * y / size)
    g = int(144 + (99 - 144) * y / size)
    b = int(217 + (255 - 217) * y / size)
    draw.line([(0, y), (size, y)], fill=(r, g, b))

# White 'CC' text centered
try:
    font = ImageFont.truetype('/System/Library/Fonts/Helvetica.ttc', 400)
except:
    font = ImageFont.load_default()
bbox = draw.textbbox((0, 0), 'CC', font=font)
tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
x = (size - tw) // 2 - bbox[0]
y = (size - th) // 2 - bbox[1]
draw.text((x, y), 'CC', fill='white', font=font)

img.save('app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/icon-1024.png')
print('Icon created')
"
```

If Pillow is not available, create a simple solid-color PNG as placeholder:

```bash
python3 -c "
import struct, zlib

def create_png(path, size=1024):
    # Simple blue PNG
    raw = b''
    for y in range(size):
        raw += b'\x00'  # filter byte
        for x in range(size):
            r = int(74 + (108 - 74) * y / size)
            g = int(144 + (99 - 144) * y / size)
            b = int(217 + (255 - 217) * y / size)
            raw += bytes([r, g, b])

    def chunk(ctype, data):
        c = ctype + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)

    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 2, 0, 0, 0)))
        f.write(chunk(b'IDAT', zlib.compress(raw)))
        f.write(chunk(b'IEND', b''))

create_png('app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/icon-1024.png')
print('Icon created')
"
```

- [ ] **Step 5: Add Assets.xcassets to project.yml**

Modify `app/iosApp/project.yml` — add the Assets.xcassets path to the CommCare target sources:

```yaml
targets:
  CommCare:
    type: application
    platform: iOS
    sources:
      - path: iosApp
        type: group
      - path: iosApp/Assets.xcassets
        type: folder
```

Also add `ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon` to the base settings:

```yaml
    settings:
      base:
        INFOPLIST_FILE: iosApp/Info.plist
        PRODUCT_BUNDLE_IDENTIFIER: org.commcare.ios
        ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon
```

- [ ] **Step 6: Regenerate Xcode project and verify icon**

```bash
cd app/iosApp
xcodegen generate
xcodebuild -project CommCare.xcodeproj -scheme CommCare -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' build 2>&1 | tail -5
```

Expected: `** BUILD SUCCEEDED **`

Install and verify the icon appears:

```bash
APP_PATH=$(find ~/Library/Developer/Xcode/DerivedData/CommCare-*/Build/Products/Debug-iphonesimulator -name "CommCare.app" -maxdepth 1 | head -1)
xcrun simctl install "iPhone 16 Pro" "$APP_PATH"
```

- [ ] **Step 7: Commit**

```bash
git add app/iosApp/iosApp/Assets.xcassets/ app/iosApp/project.yml
git commit -m "feat: add CommCare app icon (1024x1024 placeholder)"
```

---

## Task 2: Simplify LoginScreen — Remove Extra Fields

**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/LoginScreen.kt`

The current login screen has 5 fields: Server URL, Username, Domain, App ID, Password. We need to remove Server URL, Domain, and App ID — leaving only Username and Password. Domain is extracted from the username automatically. Server URL and App ID will come from the installed app's `ApplicationRecord` (in Wave 2).

For now (Wave 1), we hardcode the server URL to `https://www.commcarehq.org` in `LoginViewModel` and keep the app ID optional (set via the existing `profileUrl` or `appId` fields on the ViewModel — they just won't have UI fields).

- [ ] **Step 1: Read the current LoginScreen.kt**

Read `app/src/commonMain/kotlin/org/commcare/app/ui/LoginScreen.kt` to understand current structure.

- [ ] **Step 2: Remove Server URL, Domain, and App ID fields from LoginScreen**

Replace the login screen content. Keep:
- CommCare title/header
- Username field (with `testTag("username_field")`)
- Password field (with `testTag("password_field")`)
- Login button
- Error display
- Demo mode button

Remove:
- Server URL `OutlinedTextField`
- Domain `OutlinedTextField`
- App ID `OutlinedTextField`
- Associated `FocusRequester` variables (`domainFocus`, `appIdFocus`)

The keyboard flow becomes: Username → (Next) → Password → (Done) → Login.

Remove the Server URL field from the login screen entirely. Advanced server URL configuration is available in the existing SettingsScreen (accessible from HomeScreen after login).

- [ ] **Step 3: Verify it compiles**

```bash
cd app
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18 ./gradlew compileKotlinIosSimulatorArm64 compileKotlinJvm 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/org/commcare/app/ui/LoginScreen.kt
git commit -m "feat: simplify login screen to username + password only"
```

---

## Task 3: Update LoginViewModel — Domain Extraction

**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/LoginViewModel.kt`

- [ ] **Step 1: Read the current LoginViewModel.kt**

Read `app/src/commonMain/kotlin/org/commcare/app/viewmodel/LoginViewModel.kt` to understand current state properties and login flow.

- [ ] **Step 2: Remove UI-facing domain/appId/serverUrl state properties**

Current public mutable state:
```kotlin
var serverUrl by mutableStateOf("https://www.commcarehq.org")
var username by mutableStateOf("")
var password by mutableStateOf("")
var domain by mutableStateOf("")
var appId by mutableStateOf("")
var profileUrl by mutableStateOf("")
```

Change to:
```kotlin
var username by mutableStateOf("")
var password by mutableStateOf("")

// Internal — not shown in UI. Will come from ApplicationRecord in Wave 2.
private var serverUrl = "https://www.commcarehq.org"
private var appId = ""
private var profileUrl = ""
```

- [ ] **Step 3: Update resolveDomain() to always extract from username**

```kotlin
private fun resolveDomain(): String {
    return if (username.contains("@")) {
        username.substringAfter("@").removeSuffix(".commcarehq.org")
    } else {
        // Default domain — will come from seated app in Wave 2
        "demo"
    }
}
```

- [ ] **Step 4: Add a method to configure server/app from external source**

This will be used by Wave 2 (SetupScreen) to pass the installed app's config:

```kotlin
/**
 * Configure server and app details from an installed application.
 * Called by the setup/dispatch flow after an app is installed.
 */
fun configureApp(serverUrl: String, appId: String) {
    this.serverUrl = serverUrl
    this.appId = appId
}
```

- [ ] **Step 5: Verify compilation**

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18 ./gradlew compileKotlinIosSimulatorArm64 compileKotlinJvm 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/commonMain/kotlin/org/commcare/app/viewmodel/LoginViewModel.kt
git commit -m "feat: remove domain/appId/serverUrl from login UI, extract domain from username"
```

---

## Task 4: Update Maestro Login Flow

**Files:**
- Modify: `.maestro/flows/login-with-app.yaml`
- Modify: `.maestro/flows/login.yaml`
- Modify: `.maestro/flows/login-and-home.yaml`
- Modify: `.maestro/flows/sync-and-verify.yaml`

The login screen no longer has Domain/App ID fields. Update Maestro flows to match.

- [ ] **Step 1: Update login-with-app.yaml**

Since the login screen now only has username + password, the flow simplifies dramatically. However, we need the app to be pre-configured with an app ID. For now, update the flow to just fill username + password:

```yaml
# Login flow — fills username and password only.
# App must be pre-installed (setup screen handles app selection in Wave 2).
# Expects env: COMMCARE_USERNAME, COMMCARE_PASSWORD
appId: org.commcare.ios

---

- launchApp:
    appId: "org.commcare.ios"
    clearState: true

- extendedWaitUntil:
    visible: "Log In"
    timeout: 10000

- tapOn:
    id: "username_field"
- inputText: "${COMMCARE_USERNAME}"

- tapOn:
    point: "50%,5%"
- waitForAnimationToEnd:
    timeout: 1000

- tapOn:
    id: "password_field"
- inputText: "${COMMCARE_PASSWORD}"

- tapOn:
    point: "50%,5%"
- waitForAnimationToEnd:
    timeout: 1000

- scrollUntilVisible:
    element: "Log In"
    direction: DOWN
- tapOn: "Log In"

- extendedWaitUntil:
    visible: "Start"
    timeout: 300000
```

Note: This flow will need the ViewModel to be pre-configured with the app ID. For Wave 1, we can temporarily set it via a hardcoded default or environment. The full setup flow in Wave 2 will handle this properly.

- [ ] **Step 2: Update login.yaml similarly**

Same pattern — remove Domain/App ID steps, use testTag IDs.

- [ ] **Step 3: Update login-and-home.yaml and sync-and-verify.yaml**

These flows reference the login sub-flow. Ensure they still work with the simplified login.

- [ ] **Step 4: Commit**

```bash
git add .maestro/flows/
git commit -m "feat: update Maestro flows for simplified login screen"
```

---

## Task 5: Build, Install, and End-to-End Verify

**Files:** None (verification only)

- [ ] **Step 1: Build the full iOS framework**

```bash
cd app
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18 ./gradlew linkDebugFrameworkIosSimulatorArm64 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Regenerate Xcode project (if not done) and build**

```bash
cd iosApp
xcodegen generate
xcodebuild -project CommCare.xcodeproj -scheme CommCare -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' build 2>&1 | tail -5
```

Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 3: Install and launch**

```bash
xcrun simctl terminate "iPhone 16 Pro" org.commcare.ios 2>/dev/null
APP_PATH=$(find ~/Library/Developer/Xcode/DerivedData/CommCare-*/Build/Products/Debug-iphonesimulator -name "CommCare.app" -maxdepth 1 | head -1)
xcrun simctl install "iPhone 16 Pro" "$APP_PATH"
xcrun simctl launch "iPhone 16 Pro" org.commcare.ios
```

- [ ] **Step 4: Verify app icon is visible**

Open Simulator and check the home screen — the CommCare app should show the blue/purple gradient icon with "CC" text instead of the default white icon.

- [ ] **Step 5: Verify login screen**

The login screen should show:
- "CommCare" title
- Username field only
- Password field only
- "Log In" button (disabled until both fields have content)
- "Enter Demo Mode" button
- No Server URL, Domain, or App ID fields

- [ ] **Step 6: Run all existing commcare-core tests**

```bash
cd ../commcare-core
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18 ./gradlew iosSimulatorArm64Test 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL` — all tests pass

- [ ] **Step 7: Run JVM tests**

```bash
cd ../app
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18 ./gradlew jvmTest 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Final commit if any cleanup needed**

```bash
git status
# If clean, no commit needed
# If any files changed during verification, add and commit
```

---

## Task 6: PR and Merge

- [ ] **Step 1: Create PR**

```bash
git push -u origin <branch>
gh pr create --title "feat: Phase 5 Wave 1 — app icon + simplified login" --body "$(cat <<'EOF'
## Summary

Phase 5 Wave 1: App Icon + Login Cleanup

- Add CommCare app icon (1024x1024 in Assets.xcassets)
- Simplify login screen to Username + Password only (matches Android)
- Domain extracted from username automatically
- Remove Server URL, Domain, App ID fields from login UI
- Update Maestro flows for new login screen

Part of Phase 5: Android UX Parity (spec: docs/plans/2026-03-19-phase5-android-ux-parity-spec.md)

## Test plan

- [ ] App icon visible on simulator home screen
- [ ] Login screen shows only Username + Password
- [ ] Login works with username@domain format
- [ ] All existing tests pass
- [ ] Xcode build succeeds

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)" --base main
```

- [ ] **Step 2: Merge**

```bash
gh pr merge --squash
```
