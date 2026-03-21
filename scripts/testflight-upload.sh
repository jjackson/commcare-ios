#!/bin/bash
set -euo pipefail

# CommCare iOS — Build and upload to TestFlight
# Usage: ./scripts/testflight-upload.sh

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

echo "=== CommCare iOS TestFlight Upload ==="
echo ""

# Find Java
if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_HOME=$(/opt/homebrew/opt/openjdk@17/bin/java -XshowSettings:property -version 2>&1 | grep java.home | awk '{print $3}')
    export JAVA_HOME
fi
echo "JAVA_HOME: $JAVA_HOME"

# Step 1: Build release Kotlin frameworks
echo ""
echo "=== Step 1/5: Building commcare-core release framework ==="
cd "$REPO_ROOT/commcare-core"
./gradlew linkReleaseFrameworkIosArm64 --no-daemon -q

echo "=== Step 2/5: Building app release framework ==="
cd "$REPO_ROOT/app"
./gradlew linkReleaseFrameworkIosArm64 --no-daemon -q -Dorg.gradle.jvmargs="-Xmx4g"

# Step 2: Generate Xcode project
echo ""
echo "=== Step 3/5: Generating Xcode project ==="
cd "$REPO_ROOT/app/iosApp"
xcodegen generate

# Step 3: Archive
echo ""
echo "=== Step 4/5: Archiving release build ==="
xcodebuild archive \
    -project CommCare.xcodeproj \
    -scheme CommCare \
    -archivePath build/CommCare.xcarchive \
    -destination 'generic/platform=iOS' \
    -configuration Release \
    -allowProvisioningUpdates \
    -quiet

ARCHIVE_SIZE=$(du -sh build/CommCare.xcarchive/Products/Applications/CommCare.app | awk '{print $1}')
echo "Archive size: $ARCHIVE_SIZE"

# Step 4: Export and upload to TestFlight
echo ""
echo "=== Step 5/5: Uploading to TestFlight ==="
xcodebuild -exportArchive \
    -archivePath build/CommCare.xcarchive \
    -exportPath build/ipa \
    -exportOptionsPlist ExportOptions.plist \
    -allowProvisioningUpdates

# Summary
COMMIT=$(git rev-list --count HEAD)
SHA=$(git rev-parse --short HEAD)
echo ""
echo "=== Done ==="
echo "Build: 1.0 ($COMMIT) — $SHA"
echo "Size: $ARCHIVE_SIZE"
echo "Check TestFlight: https://appstoreconnect.apple.com/apps"
