#!/usr/bin/env bash
# Phase 9 Wave 2: Connect menu entry orchestrator.
#
# Flow:
#   1. Fresh install + boot
#   2. Recovery part A → OTP → recovery part B → done_button
#   3. Tap done_button (back to SetupScreen)
#   4. connect-menu-entry.yaml: tap Connect button, capture buggy state

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

set -a
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

# Fresh install
xcrun simctl terminate booted "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl uninstall booted "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl install booted "$APP_PATH"
xcrun simctl launch booted "$BUNDLE_ID" >/dev/null
sleep 2

# Recovery part A
"$MAESTRO" test \
  -e "CONNECTID_E2E_PHONE=$CONNECTID_E2E_PHONE" \
  -e "CONNECTID_E2E_PHONE_LOCAL=$CONNECTID_E2E_PHONE_LOCAL" \
  -e "CONNECTID_E2E_COUNTRY_CODE=$CONNECTID_E2E_COUNTRY_CODE" \
  -e "CONNECTID_E2E_PIN=$CONNECTID_E2E_PIN" \
  .maestro/flows/connect-id-recovery-to-otp.yaml \
  --no-ansi

OTP="$(node "$PLAYWRIGHT_SCRIPT" "$CONNECTID_E2E_PHONE")"
if ! [[ "$OTP" =~ ^[0-9]{6}$ ]]; then
  echo "ERROR: invalid OTP: '$OTP'" >&2
  exit 2
fi

# Recovery part B
"$MAESTRO" test \
  -e "CONNECTID_E2E_OTP=$OTP" \
  -e "CONNECTID_E2E_BACKUP_CODE=$CONNECTID_E2E_BACKUP_CODE" \
  .maestro/flows/connect-id-recovery-from-otp.yaml \
  --no-ansi

# Tap done_button to return to SetupScreen
"$MAESTRO" test .maestro/flows/tap-done-button.yaml --no-ansi

# Tap Connect button, capture buggy state
"$MAESTRO" test .maestro/flows/connect-menu-entry.yaml --no-ansi

echo "=== $(basename "$0") complete ==="
