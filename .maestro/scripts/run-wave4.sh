#!/usr/bin/env bash
# Phase 9 Wave 4a: fresh install → log in → module navigation baseline.
#
# Chains Wave 3's install+login with Wave 4a's navigation flow. One
# orchestrator, one fresh install, ~100s total.
#
# Usage: .maestro/scripts/run-wave4.sh

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

# Kill stale driver + fresh simulator install
pkill -f "maestro-driver-iosUITests-Runner" 2>/dev/null || true
pkill -f "xcodebuild test-without-building.*maestro" 2>/dev/null || true
sleep 1

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
echo "--- Step 3: Module navigation baseline ---"
"$MAESTRO" test .maestro/flows/module-nav.yaml --no-ansi

echo ""
echo "=== Wave 4a complete ==="
echo "Screenshots:"
echo "  /tmp/phase9-wave4-case-list.png"
echo "  /tmp/phase9-wave4-back-at-home.png"
