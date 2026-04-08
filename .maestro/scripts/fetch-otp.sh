#!/usr/bin/env bash
#
# Phase 9 E2E: fetch a one-time OTP for the fixture user.
#
# Calls /users/generate_manual_otp on connect-id (OAuth2 client credentials)
# and prints the OTP in Maestro's `runScript` output format so a Maestro
# flow can read it via ${output.otp}.
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
#   CONNECTID_E2E_CLIENT_ID
#   CONNECTID_E2E_CLIENT_SECRET
#   CONNECTID_E2E_PHONE
#   CONNECTID_E2E_BASE_URL (defaults to https://connectid.dimagi.com)

set -euo pipefail

BASE_URL="${CONNECTID_E2E_BASE_URL:-https://connectid.dimagi.com}"

if [ -z "${CONNECTID_E2E_CLIENT_ID:-}" ] || [ -z "${CONNECTID_E2E_CLIENT_SECRET:-}" ]; then
  echo "ERROR: CONNECTID_E2E_CLIENT_ID and CONNECTID_E2E_CLIENT_SECRET must be set" >&2
  exit 1
fi

if [ -z "${CONNECTID_E2E_PHONE:-}" ]; then
  echo "ERROR: CONNECTID_E2E_PHONE must be set" >&2
  exit 1
fi

# 1. Exchange client credentials for an access token.
TOKEN_RESPONSE=$(curl -sS -X POST "$BASE_URL/o/token/" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=$CONNECTID_E2E_CLIENT_ID" \
  -d "client_secret=$CONNECTID_E2E_CLIENT_SECRET")

ACCESS_TOKEN=$(printf '%s' "$TOKEN_RESPONSE" | jq -r '.access_token // empty')

if [ -z "$ACCESS_TOKEN" ]; then
  echo "ERROR: failed to obtain access token" >&2
  echo "Response: $TOKEN_RESPONSE" >&2
  exit 2
fi

# 2. Fetch the OTP for the fixture phone number.
# URL-encode the leading + so curl does not interpret it as a space.
ENCODED_PHONE=$(printf '%s' "$CONNECTID_E2E_PHONE" | sed 's/^+/%2B/')

OTP_RESPONSE=$(curl -sS "$BASE_URL/users/generate_manual_otp?phone_number=$ENCODED_PHONE" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

OTP=$(printf '%s' "$OTP_RESPONSE" | jq -r '.otp // empty')

if [ -z "$OTP" ]; then
  echo "ERROR: failed to obtain OTP" >&2
  echo "Response: $OTP_RESPONSE" >&2
  exit 3
fi

# 3. Emit Maestro `output=key:value` so a flow can consume `${output.otp}`.
echo "output=otp:$OTP"
