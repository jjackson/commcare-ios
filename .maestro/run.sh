#!/usr/bin/env bash
# Run Maestro flows with credentials from credentials.env
# Usage: .maestro/run.sh [flow-file-or-folder]
# Examples:
#   .maestro/run.sh                                    # run all flows
#   .maestro/run.sh .maestro/flows/login-and-home.yaml # run one flow
#   .maestro/run.sh .maestro/flows/hq-round-trip.yaml  # full round-trip

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CREDS_FILE="$SCRIPT_DIR/.env"

if [[ ! -f "$CREDS_FILE" ]]; then
  echo "Error: $CREDS_FILE not found. Create it with:"
  echo "  COMMCARE_HQ_URL=https://www.commcarehq.org"
  echo "  COMMCARE_USERNAME=..."
  echo "  COMMCARE_PASSWORD=..."
  echo "  COMMCARE_DOMAIN=..."
  echo "  COMMCARE_APP_ID=..."
  exit 1
fi

# Build -e flags from credentials.env
ENV_FLAGS=()
while IFS='=' read -r key value; do
  [[ -z "$key" || "$key" =~ ^# ]] && continue
  ENV_FLAGS+=(-e "$key=$value")
done < "$CREDS_FILE"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"

TARGET="${1:-$SCRIPT_DIR/flows/}"

echo "Running: maestro test ${ENV_FLAGS[*]} $TARGET"
~/.maestro/bin/maestro test "${ENV_FLAGS[@]}" "$TARGET"
