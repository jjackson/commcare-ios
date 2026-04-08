#!/usr/bin/env bash
#
# Phase 9 Wave 1: Connect ID recovery — fully automated orchestrator.
#
# Drives a fresh-simulator recovery of the fixture user using
# Playwright to fetch the OTP. Idempotent: runs as many times as you
# want against the same server-side fixture user.
#
# Usage: .maestro/scripts/run-recovery.sh

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
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
PLAYWRIGHT_SCRIPT="$ROOT/.maestro/scripts/playwright/fetch-otp.js"

echo "=== Phase 9 Wave 1: Connect ID recovery ==="
echo "Phone:  $CONNECTID_E2E_PHONE"
echo "Backup: $CONNECTID_E2E_BACKUP_CODE"
echo ""

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
  .maestro/flows/connect-id-recovery-to-otp.yaml \
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
echo "--- Step 3: Maestro from OTP through recovery Success ---"
"$MAESTRO" test \
  -e "CONNECTID_E2E_OTP=$OTP" \
  -e "CONNECTID_E2E_BACKUP_CODE=$CONNECTID_E2E_BACKUP_CODE" \
  .maestro/flows/connect-id-recovery-from-otp.yaml \
  --no-ansi

echo ""
echo "=== Recovery complete ==="
echo "Success screenshot: /tmp/phase9-recovery-success.png"
