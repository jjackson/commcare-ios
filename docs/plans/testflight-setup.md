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
