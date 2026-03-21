# App Store Preparation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make CommCare iOS TestFlight-ready under a personal developer account.

**Architecture:** Update bundle ID across all config files, add privacy manifest, configure launch screen via asset catalog, auto-increment build numbers from git commit count.

**Tech Stack:** Xcode/xcodegen, plist, xcprivacy, asset catalogs

**Spec:** `docs/superpowers/specs/2026-03-20-app-store-prep-design.md`

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `app/iosApp/project.yml` | Modify | Bundle ID, build script, sources |
| `app/iosApp/iosApp/Info.plist` | Modify | Launch screen config |
| `.github/workflows/ios-build.yml` | Modify | Bundle ID in simctl launch |
| `.maestro/config.yaml` | Modify | Bundle ID |
| `.maestro/flows/*.yaml` (6 files) | Modify | Bundle ID in all flows |
| `app/iosApp/iosApp/PrivacyInfo.xcprivacy` | Create | Privacy manifest |
| `app/iosApp/iosApp/Assets.xcassets/LaunchLogo.imageset/` | Create | Logo for launch screen |
| `app/iosApp/iosApp/Assets.xcassets/LaunchBackground.colorset/` | Create | White background color |
| `docs/plans/testflight-setup.md` | Create | TestFlight guide |

**No changes needed:** App icon (existing 1024x1024 already matches official branding), `ConnectIdApi.APPLICATION_ID` (server-registered, not the Xcode bundle ID).

---

### Task 1: Update Bundle ID

**Files:**
- Modify: `app/iosApp/project.yml`
- Modify: `.github/workflows/ios-build.yml:110`
- Modify: `.maestro/config.yaml:30`
- Modify: `.maestro/flows/login.yaml:3,8`
- Modify: `.maestro/flows/login-with-app.yaml:3,8`
- Modify: `.maestro/flows/sync-and-verify.yaml:5`
- Modify: `.maestro/flows/hq-round-trip.yaml:6`
- Modify: `.maestro/flows/login-and-home.yaml:5`
- Modify: `.maestro/flows/form-entry-navigation.yaml:8`

- [ ] **Step 1: Update project.yml**

Change `bundleIdPrefix` from `org.commcare` to `org.marshellis.commcare`, and both `PRODUCT_BUNDLE_IDENTIFIER` entries:

```yaml
# Line 3
bundleIdPrefix: org.marshellis.commcare

# Line 26 (CommCare target)
PRODUCT_BUNDLE_IDENTIFIER: org.marshellis.commcare.ios

# Line 50 (CommCareUITests target)
PRODUCT_BUNDLE_IDENTIFIER: org.marshellis.commcare.ios.uitests
```

- [ ] **Step 2: Update CI workflow**

In `.github/workflows/ios-build.yml` line 110, change:
```
xcrun simctl launch "$DEVICE_ID" org.marshellis.commcare.ios
```

Also update the grep on line 116:
```
APP_PID=$(xcrun simctl spawn "$DEVICE_ID" launchctl list | grep marshellis || true)
```

And the retry grep on line 120:
```
APP_PID=$(xcrun simctl spawn "$DEVICE_ID" launchctl list | grep marshellis || true)
```

- [ ] **Step 3: Update all Maestro flows**

Replace `org.commcare.ios` with `org.marshellis.commcare.ios` in all 7 Maestro files. Use replace-all across each file.

- [ ] **Step 4: Verify xcodegen regeneration**

Run: `cd app/iosApp && xcodegen generate`

Expected: Project regenerated with new bundle IDs. Verify with:
```bash
grep "PRODUCT_BUNDLE_IDENTIFIER" CommCare.xcodeproj/project.pbxproj
```
Should show `org.marshellis.commcare.ios` everywhere.

- [ ] **Step 5: Commit**

```bash
git add app/iosApp/project.yml .github/workflows/ios-build.yml .maestro/
git commit -m "feat: update bundle ID to org.marshellis.commcare.ios"
```

---

### Task 2: Add Privacy Manifest

**Files:**
- Create: `app/iosApp/iosApp/PrivacyInfo.xcprivacy`
- Modify: `app/iosApp/project.yml` (sources already cover `iosApp` directory)

- [ ] **Step 1: Create PrivacyInfo.xcprivacy**

Create `app/iosApp/iosApp/PrivacyInfo.xcprivacy`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>NSPrivacyTracking</key>
	<false/>
	<key>NSPrivacyTrackingDomains</key>
	<array/>
	<key>NSPrivacyCollectedDataTypes</key>
	<array/>
	<key>NSPrivacyAccessedAPITypes</key>
	<array>
		<dict>
			<key>NSPrivacyAccessedAPIType</key>
			<string>NSPrivacyAccessedAPICategoryUserDefaults</string>
			<key>NSPrivacyAccessedAPITypeReasons</key>
			<array>
				<string>CA92.1</string>
			</array>
		</dict>
		<dict>
			<key>NSPrivacyAccessedAPIType</key>
			<string>NSPrivacyAccessedAPICategoryFileTimestamp</string>
			<key>NSPrivacyAccessedAPITypeReasons</key>
			<array>
				<string>C617.1</string>
			</array>
		</dict>
	</array>
</dict>
</plist>
```

- [ ] **Step 2: Verify it's included in build**

The `project.yml` sources already include `path: iosApp` which covers all files in the `iosApp/` directory. Xcodegen will pick up the `.xcprivacy` file automatically.

Verify: `cd app/iosApp && xcodegen generate && grep -i "privacy" CommCare.xcodeproj/project.pbxproj`

- [ ] **Step 3: Commit**

```bash
git add app/iosApp/iosApp/PrivacyInfo.xcprivacy
git commit -m "feat: add privacy manifest for TestFlight"
```

---

### Task 3: Configure Launch Screen

**Files:**
- Modify: `app/iosApp/iosApp/Info.plist`
- Create: `app/iosApp/iosApp/Assets.xcassets/LaunchLogo.imageset/Contents.json`
- Create: `app/iosApp/iosApp/Assets.xcassets/LaunchLogo.imageset/launch-logo.png`
- Create: `app/iosApp/iosApp/Assets.xcassets/LaunchBackground.colorset/Contents.json`

- [ ] **Step 1: Copy logo image to asset catalog**

```bash
mkdir -p app/iosApp/iosApp/Assets.xcassets/LaunchLogo.imageset
cp docs/branding/CommCare-Full-Color-Logo.png app/iosApp/iosApp/Assets.xcassets/LaunchLogo.imageset/launch-logo.png
```

- [ ] **Step 2: Create LaunchLogo.imageset/Contents.json**

```json
{
  "images" : [
    {
      "filename" : "launch-logo.png",
      "idiom" : "universal",
      "scale" : "1x"
    },
    {
      "idiom" : "universal",
      "scale" : "2x"
    },
    {
      "idiom" : "universal",
      "scale" : "3x"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

- [ ] **Step 3: Create LaunchBackground.colorset/Contents.json**

Create `app/iosApp/iosApp/Assets.xcassets/LaunchBackground.colorset/Contents.json`:

```json
{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "1.000",
          "green" : "1.000",
          "red" : "1.000"
        }
      },
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

- [ ] **Step 4: Update Info.plist launch screen config**

Replace the empty `UILaunchScreen` dict (line 28-29) with:

```xml
	<key>UILaunchScreen</key>
	<dict>
		<key>UIImageName</key>
		<string>LaunchLogo</string>
		<key>UIColorName</key>
		<string>LaunchBackground</string>
	</dict>
```

- [ ] **Step 5: Verify xcodegen picks up new assets**

Run: `cd app/iosApp && xcodegen generate`

Verify: `grep -i "LaunchLogo\|LaunchBackground" CommCare.xcodeproj/project.pbxproj`

- [ ] **Step 6: Commit**

```bash
git add app/iosApp/iosApp/Assets.xcassets/LaunchLogo.imageset/ \
       app/iosApp/iosApp/Assets.xcassets/LaunchBackground.colorset/ \
       app/iosApp/iosApp/Info.plist
git commit -m "feat: add CommCare launch screen with official logo"
```

---

### Task 4: Add Build Number Auto-Increment

**Files:**
- Modify: `app/iosApp/project.yml`

- [ ] **Step 1: Add postBuildScripts to CommCare target**

Add after the `settings` block of the CommCare target in `project.yml`:

```yaml
    postBuildScripts:
      - name: Set Build Number from Git
        script: |
          COMMIT_COUNT=$(git rev-list --count HEAD)
          /usr/libexec/PlistBuddy -c "Set :CFBundleVersion $COMMIT_COUNT" "${TARGET_BUILD_DIR}/${INFOPLIST_PATH}"
```

- [ ] **Step 2: Verify xcodegen regeneration**

Run: `cd app/iosApp && xcodegen generate`

Verify build phase exists: `grep -A2 "Set Build Number" CommCare.xcodeproj/project.pbxproj`

- [ ] **Step 3: Commit**

```bash
git add app/iosApp/project.yml
git commit -m "feat: auto-increment build number from git commit count"
```

---

### Task 5: Write TestFlight Setup Doc

**Files:**
- Create: `docs/plans/testflight-setup.md`

- [ ] **Step 1: Write the doc**

Create `docs/plans/testflight-setup.md`:

```markdown
# TestFlight Setup Guide

## Prerequisites

1. **Apple Developer account** — enrolled in Apple Developer Program ($99/year)
2. **Xcode** — with signing identity configured (Xcode > Settings > Accounts > add Apple ID)
3. **App Store Connect** — create app record with bundle ID `org.marshellis.commcare.ios`

## Building for TestFlight

### 1. Build the Kotlin frameworks

```bash
cd commcare-core && ./gradlew linkReleaseFrameworkIosArm64 --no-daemon
cd ../app && ./gradlew linkReleaseFrameworkIosArm64 --no-daemon
```

### 2. Generate Xcode project

```bash
cd app/iosApp && xcodegen generate
```

### 3. Open in Xcode and configure signing

Open `app/iosApp/CommCare.xcodeproj` in Xcode.

In the CommCare target > Signing & Capabilities:
- Check "Automatically manage signing"
- Select your development team
- Xcode will create/download provisioning profiles automatically

### 4. Archive

- Select "Any iOS Device (arm64)" as the build destination
- Product > Archive
- Wait for the archive to complete

### 5. Upload to App Store Connect

- In the Archives organizer, select the archive
- Click "Distribute App"
- Select "App Store Connect" > "Upload"
- Follow the prompts (Xcode handles signing automatically)

## App Store Connect Setup

1. Go to [appstoreconnect.apple.com](https://appstoreconnect.apple.com)
2. My Apps > "+" > New App
3. Fill in: name "CommCare", bundle ID `org.marshellis.commcare.ios`, SKU "commcare-ios"
4. After upload processes, the build appears under TestFlight

## Inviting Testers

### Internal Testing (up to 100 testers, no review needed)
1. TestFlight > Internal Testing > "+" > Create group
2. Add testers by Apple ID email
3. They receive a TestFlight invite automatically

### External Testing (up to 10,000 testers, requires Beta App Review)
1. TestFlight > External Testing > "+" > Create group
2. Add testers or generate a public link
3. First build requires Beta App Review (~24-48 hours)

## Build Numbers

Build numbers auto-increment from the git commit count. Each commit produces a unique build number (e.g., `1.0 (347)`). No manual management needed — just commit and rebuild.

## Troubleshooting

- **"No signing identity found"** — Add your Apple Developer account in Xcode > Settings > Accounts
- **"Provisioning profile doesn't match bundle ID"** — Let Xcode manage signing automatically
- **Upload fails with "redundant binary"** — Build number must be higher than any previous upload. Since we use git commit count, ensure you have the latest commits.
```

- [ ] **Step 2: Commit**

```bash
git add docs/plans/testflight-setup.md
git commit -m "docs: add TestFlight setup guide"
```

---

### Task 6: Final Verification

- [ ] **Step 1: Regenerate Xcode project and verify**

```bash
cd app/iosApp && xcodegen generate
```

- [ ] **Step 2: Build with xcodebuild**

```bash
xcodebuild build \
  -project CommCare.xcodeproj \
  -scheme CommCare \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -configuration Debug \
  ARCHS=arm64 \
  ONLY_ACTIVE_ARCH=YES \
  CODE_SIGN_IDENTITY="-" \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGNING_ALLOWED=NO \
  2>&1 | tail -20
```

Expected: BUILD SUCCEEDED

- [ ] **Step 3: Verify bundle ID in built app**

```bash
APP_PATH=$(find build -name "CommCare.app" -type d 2>/dev/null | head -1)
/usr/libexec/PlistBuddy -c "Print :CFBundleIdentifier" "$APP_PATH/Info.plist"
```

Expected: `org.marshellis.commcare.ios`

- [ ] **Step 4: Verify privacy manifest in bundle**

```bash
find "$APP_PATH" -name "PrivacyInfo*" 2>/dev/null
```

Expected: Shows PrivacyInfo.xcprivacy in the app bundle.

- [ ] **Step 5: Commit any fixes, then squash into final commit**

```bash
git add -A
git commit -m "feat: Phase 4 Wave 5 — App Store preparation (TestFlight ready)"
```
