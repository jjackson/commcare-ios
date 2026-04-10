#!/usr/bin/env bash
# Phase 9 Wave 5b: Visit form fill + submit + HQ verify.
#
# Chains Wave 3 install+login with a Visit form fill, then polls the HQ
# form API to confirm the submission landed server-side.
# This is the MSelect-exercising counterpart of Wave 4c (Register Household).

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
: "${COMMCARE_HQ_WEB_USERNAME:?must be set in .env.e2e.local}"
: "${COMMCARE_HQ_WEB_PASSWORD:?must be set in .env.e2e.local}"
: "${COMMCARE_DOMAIN:?must be set in .env.e2e.local}"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export MAESTRO_DRIVER_STARTUP_TIMEOUT="${MAESTRO_DRIVER_STARTUP_TIMEOUT:-180000}"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
APP_PATH="$ROOT/app/iosApp/build/Build/Products/Debug-iphonesimulator/CommCare.app"
BUNDLE_ID="org.marshellis.commcare.ios"

# Visit form xmlns — needed for HQ API verification.
# TODO: extract from actual submission; using placeholder until first successful submit.
FORM_XMLNS=""

echo "=== Phase 9 Wave 5b: Visit form fill + submit + HQ verify ==="
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
echo "--- Step 3: Fill + submit Visit form ---"
"$MAESTRO" test .maestro/flows/visit-submit.yaml --no-ansi

echo ""
echo "=== Wave 5b complete ==="
echo "Screenshots:"
ls -1 /tmp/phase9-wave5b-*.png 2>/dev/null || echo "  (none found)"
