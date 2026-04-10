#!/usr/bin/env bash
# Phase 9 Wave 6: multi-app management.
#
# Installs Bonsaaso, logs in, then installs Small App as a second app
# via App Manager, and verifies both apps work and can be switched
# between via the login dropdown.

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
SECOND_APP_URL="https://www.commcarehq.org/a/jonstest/apps/download/12ab84e1f722c951c32147f57a91ebb2/profile.ccpr"

echo "=== Phase 9 Wave 6: multi-app management ==="
echo ""

# Kill stale Maestro driver
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

echo "--- Step 1: Install Bonsaaso + login ---"
"$MAESTRO" test \
  -e "COMMCARE_APP_PROFILE_URL=$COMMCARE_APP_PROFILE_URL" \
  .maestro/flows/install-via-url.yaml \
  --no-ansi

"$MAESTRO" test \
  -e "COMMCARE_MOBILE_USERNAME=$COMMCARE_MOBILE_USERNAME" \
  -e "COMMCARE_MOBILE_PASSWORD=$COMMCARE_MOBILE_PASSWORD" \
  .maestro/flows/login-to-home.yaml \
  --no-ansi

echo ""
echo "--- Step 2: Kill-relaunch + install Small App via App Manager ---"
xcrun simctl terminate booted "$BUNDLE_ID"
sleep 2
xcrun simctl launch booted "$BUNDLE_ID" >/dev/null
sleep 5

"$MAESTRO" test \
  -e "SECOND_APP_PROFILE_URL=$SECOND_APP_URL" \
  -e "COMMCARE_MOBILE_USERNAME=$COMMCARE_MOBILE_USERNAME" \
  -e "COMMCARE_MOBILE_PASSWORD=$COMMCARE_MOBILE_PASSWORD" \
  .maestro/flows/multi-app-switch.yaml \
  --no-ansi

echo ""
echo "--- Step 3: Kill-relaunch + switch back to Bonsaaso ---"
xcrun simctl terminate booted "$BUNDLE_ID"
sleep 2
xcrun simctl launch booted "$BUNDLE_ID" >/dev/null
sleep 5

"$MAESTRO" test \
  -e "COMMCARE_MOBILE_USERNAME=$COMMCARE_MOBILE_USERNAME" \
  -e "COMMCARE_MOBILE_PASSWORD=$COMMCARE_MOBILE_PASSWORD" \
  .maestro/flows/app-switch-to-bonsaaso.yaml \
  --no-ansi

echo ""
echo "=== Wave 6 complete ==="
echo "Screenshots:"
ls -1 /tmp/phase9-wave6-*.png 2>/dev/null || echo "  (none found)"
