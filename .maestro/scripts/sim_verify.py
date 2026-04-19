#!/usr/bin/env python3
"""
Simulator-state verification helper for commcare-ios e2e flows.

Why this exists:
  During autonomous Maestro runs Claude has no reliable way to confirm
  that a tap/type actually did what it intended — fields end up with
  literal "undefined", simulators crash at fixed PINs, and Claude spins
  asking "am I running?". This helper captures a screenshot and (when
  available) dumps the UI hierarchy so Claude can assert visible state
  before moving on.

Usage:
  # Just capture a screenshot of the booted simulator
  python .maestro/scripts/sim_verify.py capture

  # Capture and assert expected text is visible
  python .maestro/scripts/sim_verify.py verify --expect "+74260000042"

  # As a module
  from sim_verify import capture_screenshot, verify_visible
  path = capture_screenshot()
  result = verify_visible(expect="PIN")
  assert result["ok"], result

Exit codes:
  0  success (or verification passed)
  2  verification failed (text not visible, etc.)
  1  infrastructure error (no booted device, xcrun missing, etc.)
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

DEFAULT_DIR = Path(os.environ.get("SIM_VERIFY_DIR", "/tmp/sim-verify"))


def booted_device_udid() -> str:
    """Return the UDID of the currently-booted iOS simulator, or raise."""
    out = subprocess.run(
        ["xcrun", "simctl", "list", "devices", "booted", "-j"],
        capture_output=True, text=True, check=True,
    ).stdout
    data = json.loads(out)
    for _runtime, devices in (data.get("devices") or {}).items():
        for d in devices:
            if d.get("state") == "Booted":
                return d["udid"]
    raise RuntimeError("No booted simulator found. Boot one with `xcrun simctl boot <udid>`.")


def capture_screenshot(path: Path | None = None) -> Path:
    """Save a screenshot of the booted simulator and return its path."""
    DEFAULT_DIR.mkdir(parents=True, exist_ok=True)
    if path is None:
        path = DEFAULT_DIR / f"sim-{int(time.time())}.png"
    udid = booted_device_udid()
    subprocess.run(
        ["xcrun", "simctl", "io", udid, "screenshot", str(path)],
        check=True, capture_output=True,
    )
    return path


def dump_hierarchy() -> dict | None:
    """Dump the current Maestro UI hierarchy as a dict, or None if unavailable."""
    if not _command_exists("maestro"):
        return None
    proc = subprocess.run(
        ["maestro", "hierarchy"],
        capture_output=True, text=True,
    )
    if proc.returncode != 0:
        return None
    try:
        return json.loads(proc.stdout)
    except json.JSONDecodeError:
        return {"raw": proc.stdout}


def visible_text(hierarchy: dict | None) -> list[str]:
    """Extract all text/label strings from a Maestro hierarchy tree."""
    if not hierarchy:
        return []
    texts: list[str] = []

    def walk(node):
        if isinstance(node, dict):
            for key in ("text", "label", "value", "placeholder", "name"):
                v = node.get(key)
                if isinstance(v, str) and v.strip():
                    texts.append(v.strip())
            for v in node.values():
                walk(v)
        elif isinstance(node, list):
            for item in node:
                walk(item)

    walk(hierarchy)
    return texts


def verify_visible(expect: str | None = None, forbid: str | None = None) -> dict:
    """Capture + dump; check that expect is present and forbid is absent."""
    screenshot = capture_screenshot()
    hierarchy = dump_hierarchy()
    texts = visible_text(hierarchy)
    joined = "\n".join(texts)

    result = {
        "ok": True,
        "screenshot": str(screenshot),
        "hierarchy_available": hierarchy is not None,
        "visible_text_sample": texts[:20],
        "failures": [],
    }

    if expect is not None:
        if hierarchy is None:
            result["failures"].append(
                f"cannot verify expect={expect!r}: Maestro hierarchy not available "
                f"(inspect screenshot at {screenshot})"
            )
            result["ok"] = False
        elif expect not in joined:
            result["failures"].append(f"expected text not visible: {expect!r}")
            result["ok"] = False

    if forbid is not None and hierarchy is not None:
        matches = [t for t in texts if forbid in t]
        if matches:
            result["failures"].append(f"forbidden text present: {forbid!r} in {matches}")
            result["ok"] = False

    # Guard against the exact failure mode that motivated this script.
    if hierarchy is not None:
        undefs = [t for t in texts if re.search(r"\bundefined\b", t)]
        if undefs:
            result["failures"].append(f"literal 'undefined' visible: {undefs}")
            result["ok"] = False

    return result


def _command_exists(cmd: str) -> bool:
    return subprocess.run(["which", cmd], capture_output=True).returncode == 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[1])
    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("capture", help="Save a screenshot and print its path")
    verify = sub.add_parser("verify", help="Capture + assert visible state")
    verify.add_argument("--expect", help="Text that must be visible")
    verify.add_argument("--forbid", help="Text that must NOT be visible")
    args = parser.parse_args()

    try:
        if args.cmd == "capture":
            print(capture_screenshot())
            return 0
        result = verify_visible(expect=args.expect, forbid=args.forbid)
        print(json.dumps(result, indent=2))
        return 0 if result["ok"] else 2
    except (RuntimeError, subprocess.CalledProcessError, FileNotFoundError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
