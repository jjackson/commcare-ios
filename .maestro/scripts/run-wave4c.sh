#!/usr/bin/env bash
# Phase 9 Wave 4c: full Register Household fill + submit + HQ verify.
#
# Chains Wave 3 install+login with a form-fill flow, then polls the HQ
# form API to confirm the submission landed server-side.
#
# Each run uses a unique first_name like "E2E-<epoch>" so we can match
# the specific submission in the HQ form list.

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
FORM_XMLNS="http://openrosa.org/formdesigner/16fca2d3a5cfbb3acd4c724c83e8549d17b42908"

# Unique marker so we can find our exact submission on HQ.
UNIQUE_NAME="E2E-$(date +%s)"

echo "=== Phase 9 Wave 4c: form fill + submit + HQ verify ==="
echo "Unique marker: $UNIQUE_NAME"
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
echo "--- Step 3: Fill + submit Register Household form ---"
"$MAESTRO" test \
  -e "E2E_FIRST_NAME=$UNIQUE_NAME" \
  .maestro/flows/register-household-submit.yaml \
  --no-ansi

echo ""
echo "--- Step 4: Verify submission on HQ via form API ---"
# Poll for up to 60 seconds — HQ usually indexes within a few seconds.
VERIFIED=0
for i in 1 2 3 4 5 6; do
  sleep 10
  RESULT=$(/usr/bin/curl -s -u "$COMMCARE_HQ_WEB_USERNAME:$COMMCARE_HQ_WEB_PASSWORD" \
    "https://www.commcarehq.org/a/$COMMCARE_DOMAIN/api/v0.5/form/?format=json&xmlns=$FORM_XMLNS&limit=5&order_by=-received_on" 2>/dev/null)
  if echo "$RESULT" | /usr/bin/grep -q "$UNIQUE_NAME"; then
    echo "VERIFIED: submission containing '$UNIQUE_NAME' found on HQ"
    VERIFIED=1
    break
  fi
  echo "  poll $i/6: not yet indexed"
done

if [ "$VERIFIED" -ne 1 ]; then
  echo "FAIL: submission containing '$UNIQUE_NAME' not found on HQ after 60s" >&2
  exit 1
fi

echo ""
echo "=== Wave 4c complete ==="
echo "Screenshots:"
echo "  /tmp/phase9-wave4c-form-complete.png"
echo "  /tmp/phase9-wave4c-back-at-home.png"
echo "  /tmp/phase9-wave4c-sync-screen.png"
