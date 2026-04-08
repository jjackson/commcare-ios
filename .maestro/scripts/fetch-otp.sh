#!/usr/bin/env bash
#
# Phase 9 E2E: fetch a one-time OTP for the fixture user.
#
# Two modes, selected by which env vars are set:
#
#   1. AUTOMATED (Dimagi-provisioned OAuth2 client credentials).
#      If CONNECTID_E2E_CLIENT_ID and CONNECTID_E2E_CLIENT_SECRET are set,
#      calls /o/token/ then GET /users/generate_manual_otp on connect-id.
#      Dormant until Dimagi provisions a dedicated OAuth2 application.
#
#   2. INTERACTIVE (default for local dev with Dimagi SSO).
#      Opens the connect.dimagi.com manual OTP lookup page in the user's
#      default browser with the fixture phone number pre-filled, then reads
#      the OTP the user pastes back into the terminal. Requires a human at
#      the keyboard — does NOT work in unattended CI.
#
# Both modes print the result in Maestro's `runScript` output format so a
# Maestro flow can read it via ${output.otp}.
#
# Usage from Maestro:
#   - runScript:
#       file: scripts/fetch-otp.sh
#
# Usage from a developer shell (loads .env.e2e.local first):
#   set -a; source .env.e2e.local; set +a
#   bash .maestro/scripts/fetch-otp.sh
#
# Required environment variables:
#   CONNECTID_E2E_PHONE                 (full E.164, e.g. +74260000042)
#
# Automated-mode environment variables (optional):
#   CONNECTID_E2E_CLIENT_ID
#   CONNECTID_E2E_CLIENT_SECRET
#   CONNECTID_E2E_BASE_URL              (defaults to https://connectid.dimagi.com)
#
# Interactive-mode environment variables (optional):
#   CONNECT_MANUAL_OTP_URL              (defaults to https://connect.dimagi.com/users/connect_user_otp/)

set -euo pipefail

# ----------------------------------------------------------------------
# Shared preconditions
# ----------------------------------------------------------------------

if [ -z "${CONNECTID_E2E_PHONE:-}" ]; then
  echo "ERROR: CONNECTID_E2E_PHONE must be set (e.g. +74260000042)" >&2
  exit 1
fi

# ----------------------------------------------------------------------
# Mode selection
# ----------------------------------------------------------------------

if [ -n "${CONNECTID_E2E_CLIENT_ID:-}" ] && [ -n "${CONNECTID_E2E_CLIENT_SECRET:-}" ]; then
  MODE="automated"
else
  MODE="interactive"
fi

# ----------------------------------------------------------------------
# Automated mode (dormant until Dimagi provisions OAuth2 client creds)
# ----------------------------------------------------------------------

if [ "$MODE" = "automated" ]; then
  command -v jq >/dev/null 2>&1 || {
    echo "ERROR: jq is required for automated mode but not found in PATH. Install via 'brew install jq'." >&2
    exit 1
  }

  BASE_URL="${CONNECTID_E2E_BASE_URL:-https://connectid.dimagi.com}"

  # 1. Exchange client credentials for an access token.
  TOKEN_RESPONSE=$(curl -sS -X POST "$BASE_URL/o/token/" \
    --data-urlencode "grant_type=client_credentials" \
    --data-urlencode "client_id=${CONNECTID_E2E_CLIENT_ID}" \
    --data-urlencode "client_secret=${CONNECTID_E2E_CLIENT_SECRET}")

  ACCESS_TOKEN=$(printf '%s' "$TOKEN_RESPONSE" | jq -r '.access_token // empty')

  if [ -z "$ACCESS_TOKEN" ]; then
    echo "ERROR: failed to obtain access token" >&2
    echo "Response: $TOKEN_RESPONSE" >&2
    exit 2
  fi

  # 2. Fetch the OTP for the fixture phone number.
  OTP_RESPONSE=$(curl -sS -G "$BASE_URL/users/generate_manual_otp" \
    --data-urlencode "phone_number=${CONNECTID_E2E_PHONE}" \
    -H "Authorization: Bearer $ACCESS_TOKEN")

  OTP=$(printf '%s' "$OTP_RESPONSE" | jq -r '.otp // empty')

  if [ -z "$OTP" ]; then
    echo "ERROR: failed to obtain OTP" >&2
    echo "Response: $OTP_RESPONSE" >&2
    exit 3
  fi

  # 3. Emit Maestro `output=key:value`.
  echo "output=otp:$OTP"
  exit 0
fi

# ----------------------------------------------------------------------
# Interactive mode (default for local dev with Dimagi SSO)
# ----------------------------------------------------------------------

MANUAL_URL="${CONNECT_MANUAL_OTP_URL:-https://connect.dimagi.com/users/connect_user_otp/}"

{
  echo ""
  echo "=================================================================="
  echo "  Phase 9 E2E — Manual OTP fetch (interactive mode)"
  echo "=================================================================="
  echo "  Phone: ${CONNECTID_E2E_PHONE}"
  echo "  URL:   ${MANUAL_URL}"
  echo ""
} >&2

# Open the Dimagi manual OTP lookup page in the default browser. macOS
# only; fall back gracefully on other platforms.
if command -v open >/dev/null 2>&1; then
  open "$MANUAL_URL" >/dev/null 2>&1 || true
elif command -v xdg-open >/dev/null 2>&1; then
  xdg-open "$MANUAL_URL" >/dev/null 2>&1 || true
fi

OTP=""

# Preferred: macOS GUI dialog via osascript. Works regardless of whether
# stdin is a TTY, so it's safe when the script is invoked by Maestro
# (which has no TTY). The dialog pops up natively on the user's desktop.
if command -v osascript >/dev/null 2>&1; then
  # escape any double-quotes in the phone number for AppleScript
  PHONE_ESCAPED=$(printf '%s' "$CONNECTID_E2E_PHONE" | sed 's/"/\\"/g')
  PROMPT_TEXT="Look up the OTP for ${PHONE_ESCAPED} at:

${MANUAL_URL}

Paste the 6-digit code below."

  # display dialog returns e.g. "button returned:OK, text returned:123456"
  DIALOG_OUTPUT=$(osascript -e "display dialog \"$PROMPT_TEXT\" default answer \"\" with title \"Phase 9 E2E OTP\" buttons {\"Cancel\", \"OK\"} default button \"OK\"" 2>/dev/null) || {
    echo "" >&2
    echo "ERROR: OTP dialog cancelled or failed." >&2
    exit 5
  }
  OTP=$(printf '%s' "$DIALOG_OUTPUT" | sed -n 's/.*text returned:\(.*\)/\1/p' | tr -d '[:space:]')
fi

# Fallback: if osascript isn't available AND stdin is a TTY, prompt via read.
if [ -z "$OTP" ] && [ -t 0 ]; then
  printf "  OTP: " >&2
  read -r OTP_RAW
  OTP=$(printf '%s' "$OTP_RAW" | tr -d '[:space:]')
fi

if [ -z "$OTP" ]; then
  echo "" >&2
  echo "ERROR: no OTP entered." >&2
  echo "Interactive mode requires either macOS (for osascript dialog) or a TTY shell (for read)." >&2
  exit 5
fi

if [ -z "$OTP" ]; then
  echo "" >&2
  echo "ERROR: no OTP entered." >&2
  exit 5
fi

# Emit Maestro `output=key:value`.
echo "output=otp:$OTP"
