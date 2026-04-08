#!/usr/bin/env bash
#
# Phase 9 ONE-TIME registration helper: captures a screenshot of the
# backup code screen to a predictable path so the operator can extract
# the generated backup code from the image.
#
# Called via Maestro runScript during one-time-registration.yaml. Does
# not emit any Maestro output=key:value lines — it just writes the PNG
# and exits.

set -euo pipefail

OUT_PATH="/tmp/phase9-backup-code.png"

xcrun simctl io booted screenshot "$OUT_PATH" >/dev/null 2>&1

{
  echo "BACKUP CODE SCREENSHOT captured: $OUT_PATH"
  ls -la "$OUT_PATH" 2>&1 || echo "  (screenshot file not found)"
} >&2
