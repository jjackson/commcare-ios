#!/usr/bin/env bash
#
# Phase 9 ONE-TIME fixture user registration — fully automated orchestrator.
#
# Runs the Maestro registration flow in two parts with a Playwright OTP
# fetch in the middle:
#
#   1. Part A (one-time-registration-to-otp.yaml): launches the app,
#      walks phone entry + PIN + waits for OTP screen to be visible.
#      Then exits (does NOT time out waiting for OTP — Maestro stops
#      immediately once the OTP screen is visible).
#
#   2. Playwright fetch: runs fetch-otp.js headlessly using the
#      persisted Dimagi SSO cookies in .maestro/scripts/playwright/userdata/.
#
#   3. Part B (one-time-registration-from-otp.yaml): types the fetched
#      OTP, verifies, continues through name + backup code + photo skip
#      + success.
#
# Requires:
#   - .env.e2e.local populated with CONNECTID_E2E_* vars
#   - .maestro/scripts/playwright/userdata/ contains a signed-in Chromium
#     session for Dimagi SSO (first-time setup: PHASE9_HEADED=1 node fetch-otp.js)
#   - iPhone simulator booted with CommCare.app installed
#   - JAVA_HOME set (gradle needs JDK 17+)
#
# Usage:
#   .maestro/scripts/run-registration.sh
#
# Exit code 0 on success, non-zero on any failure.

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

# Load env
if [ ! -f .env.e2e.local ]; then
  echo "ERROR: .env.e2e.local not found — copy from .env.e2e.local.example and fill in values" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env.e2e.local
set +a

# Default JAVA_HOME to openjdk@17 if not set
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"

MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
PLAYWRIGHT_SCRIPT="$ROOT/.maestro/scripts/playwright/fetch-otp.js"

if [ ! -x "$MAESTRO" ]; then
  echo "ERROR: maestro not found at $MAESTRO" >&2
  exit 1
fi

if [ ! -f "$PLAYWRIGHT_SCRIPT" ]; then
  echo "ERROR: Playwright fetch script not found at $PLAYWRIGHT_SCRIPT" >&2
  exit 1
fi

echo "=== Phase 9 one-time registration ==="
echo "Phone:     $CONNECTID_E2E_PHONE"
echo "Name:      Hal Test"
echo "PIN:       $CONNECTID_E2E_PIN"
echo "Backup:    $CONNECTID_E2E_BACKUP_CODE"
echo ""

# Ensure a clean simulator app state
echo "--- Step 0: Fresh app launch ---"
xcrun simctl terminate booted org.marshellis.commcare.ios 2>/dev/null || true
xcrun simctl uninstall booted org.marshellis.commcare.ios 2>/dev/null || true
APP_PATH="$ROOT/app/iosApp/build/Build/Products/Debug-iphonesimulator/CommCare.app"
if [ ! -d "$APP_PATH" ]; then
  echo "ERROR: $APP_PATH not found. Build with xcodebuild first." >&2
  exit 1
fi
xcrun simctl install booted "$APP_PATH"
xcrun simctl launch booted org.marshellis.commcare.ios >/dev/null
sleep 2

echo ""
echo "--- Step 1: Maestro to OTP screen ---"
"$MAESTRO" test \
  -e "CONNECTID_E2E_PHONE=$CONNECTID_E2E_PHONE" \
  -e "CONNECTID_E2E_PHONE_LOCAL=$CONNECTID_E2E_PHONE_LOCAL" \
  -e "CONNECTID_E2E_COUNTRY_CODE=$CONNECTID_E2E_COUNTRY_CODE" \
  -e "CONNECTID_E2E_PIN=$CONNECTID_E2E_PIN" \
  .maestro/flows/one-time-registration-to-otp.yaml \
  --no-ansi

echo ""
echo "--- Step 2: Playwright fetching OTP for $CONNECTID_E2E_PHONE ---"
OTP="$(node "$PLAYWRIGHT_SCRIPT" "$CONNECTID_E2E_PHONE")"
echo "Fetched OTP: $OTP"

if ! [[ "$OTP" =~ ^[0-9]{6}$ ]]; then
  echo "ERROR: Playwright returned invalid OTP: '$OTP'" >&2
  exit 2
fi

echo ""
echo "--- Step 3: Maestro from OTP through Success ---"
"$MAESTRO" test \
  -e "CONNECTID_E2E_OTP=$OTP" \
  -e "CONNECTID_E2E_BACKUP_CODE=$CONNECTID_E2E_BACKUP_CODE" \
  .maestro/flows/one-time-registration-from-otp.yaml \
  --no-ansi

echo ""
echo "=== Registration complete ==="
echo "Success screenshot: /tmp/phase9-registration-success.png"
