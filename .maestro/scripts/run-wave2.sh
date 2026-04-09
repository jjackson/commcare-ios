#!/usr/bin/env bash
# Phase 9 Wave 2: post-recovery state + session persistence + connect menu entry.
#
# Does a SINGLE full recovery and then runs all three Wave 2 assertions
# against that one signed-in state. This is dramatically faster than
# running a full recovery per assertion — recovery takes ~70s, the
# assertions take ~5s each.
#
# Flow:
#   1. Fresh install + boot
#   2. Full recovery (phone → PIN → OTP → backup code → Success)
#   3. Tap done_button → SetupScreen (signed in)
#   4. Assertion A: post-recovery SetupScreen state
#      → .maestro/flows/post-recovery-state.yaml
#   5. Kill + relaunch (simctl terminate + launch)
#   6. Assertion B: session persistence (same screen, re-validated after restart)
#      → .maestro/flows/session-persistence.yaml
#   7. Assertion C: Connect menu entry shows state inconsistency bug #389
#      → .maestro/flows/connect-menu-entry.yaml
#
# Usage: .maestro/scripts/run-wave2.sh

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

if [ ! -f .env.e2e.local ]; then
  echo "ERROR: .env.e2e.local not found — copy from .env.e2e.local.example and fill in values" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env.e2e.local
set +a

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
# Maestro's XCTest driver sometimes takes >30s to spin up under load;
# bump to 3 minutes so back-to-back runs don't flake on infrastructure.
export MAESTRO_DRIVER_STARTUP_TIMEOUT="${MAESTRO_DRIVER_STARTUP_TIMEOUT:-180000}"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
PLAYWRIGHT_SCRIPT="$ROOT/.maestro/scripts/playwright/fetch-otp.js"
APP_PATH="$ROOT/app/iosApp/build/Build/Products/Debug-iphonesimulator/CommCare.app"
BUNDLE_ID="org.marshellis.commcare.ios"

echo "=== Phase 9 Wave 2: post-recovery + session persistence + connect menu ==="

# Kill any lingering Maestro XCTest driver processes from prior runs.
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

echo ""
echo "--- Step 1: Recovery part A (phone → PIN → OTP screen) ---"
"$MAESTRO" test \
  -e "CONNECTID_E2E_PHONE=$CONNECTID_E2E_PHONE" \
  -e "CONNECTID_E2E_PHONE_LOCAL=$CONNECTID_E2E_PHONE_LOCAL" \
  -e "CONNECTID_E2E_COUNTRY_CODE=$CONNECTID_E2E_COUNTRY_CODE" \
  -e "CONNECTID_E2E_PIN=$CONNECTID_E2E_PIN" \
  .maestro/flows/connect-id-recovery-to-otp.yaml \
  --no-ansi

echo ""
echo "--- Step 2: Fetch OTP via Playwright ---"
OTP="$(node "$PLAYWRIGHT_SCRIPT" "$CONNECTID_E2E_PHONE")"
if ! [[ "$OTP" =~ ^[0-9]{6}$ ]]; then
  echo "ERROR: invalid OTP: '$OTP'" >&2
  exit 2
fi
echo "OTP: $OTP"

echo ""
echo "--- Step 3: Recovery part B (OTP → name → backup code → Success) ---"
"$MAESTRO" test \
  -e "CONNECTID_E2E_OTP=$OTP" \
  -e "CONNECTID_E2E_BACKUP_CODE=$CONNECTID_E2E_BACKUP_CODE" \
  .maestro/flows/connect-id-recovery-from-otp.yaml \
  --no-ansi

echo ""
echo "--- Step 4: Tap done_button → SetupScreen ---"
"$MAESTRO" test .maestro/flows/tap-done-button.yaml --no-ansi

echo ""
echo "--- Assertion A: Post-recovery SetupScreen state ---"
"$MAESTRO" test .maestro/flows/post-recovery-state.yaml --no-ansi

echo ""
echo "--- Kill + relaunch (session persistence setup) ---"
xcrun simctl terminate booted "$BUNDLE_ID"
sleep 1
xcrun simctl launch booted "$BUNDLE_ID" >/dev/null
sleep 3

echo ""
echo "--- Assertion B: Signed-in state survives kill + relaunch ---"
"$MAESTRO" test .maestro/flows/session-persistence.yaml --no-ansi

echo ""
echo "--- Assertion C: Connect menu shows state inconsistency bug #389 ---"
"$MAESTRO" test .maestro/flows/connect-menu-entry.yaml --no-ansi

echo ""
echo "=== Wave 2 complete ==="
echo "Screenshots:"
echo "  /tmp/phase9-post-recovery-state.png"
echo "  /tmp/phase9-session-persistence.png"
echo "  /tmp/phase9-connect-menu-entry.png"
