---
title: Phase 9 test infrastructure patterns
date: 2026-04-08
tags: [phase9, e2e-testing, maestro, playwright, orchestration, kotlin-native]
---

Phase 9 Wave 0 and Wave 1 established a working pattern for iOS
E2E testing against real production backends. This learning captures
the non-obvious architectural choices, Maestro gotchas, and Playwright
tricks that made it work, so future waves can reuse them instead of
rediscovering them.

## Summary of the working architecture

```
.maestro/scripts/run-<flow-name>.sh  (orchestrator bash)
  │
  ├─ step 1: fresh simulator state
  │    xcrun simctl terminate/uninstall/install/launch
  │
  ├─ step 2: Maestro part A (deterministic front half)
  │    ~/.maestro/bin/maestro test <flow>-to-<pause-point>.yaml
  │    — walks to a predictable pause point (e.g. OTP screen)
  │    — exits successfully once the target element is visible
  │
  ├─ step 3: out-of-band data fetch (Playwright)
  │    node .maestro/scripts/playwright/fetch-otp.js <phone>
  │    — uses persistent Chromium user data dir with cached
  │      Dimagi SSO cookies
  │    — scrapes OTP from live Dimagi page
  │    — prints value to stdout, captured by bash
  │
  └─ step 4: Maestro part B (continuation)
       ~/.maestro/bin/maestro test -e "OTP=$OTP" <flow>-from-<pause-point>.yaml
       — types the fetched value + continues through the rest
       — asserts Success screen
```

Everything is composable. Each part runs independently for debugging.

## Learning 1 — Maestro `runScript` runs JavaScript, not shell

First attempt at the OTP fetch was a bash script invoked via Maestro's
`runScript`:

```yaml
- runScript:
    file: ../scripts/fetch-otp.sh
```

Maestro's GraalJS engine tried to parse the bash as JavaScript and
failed with:

```
/path/fetch-otp.sh:2:0 Expected an operand but found error
#
^
```

`runScript` only executes JavaScript. There is no way to invoke a
shell script directly from a Maestro flow.

**Workarounds:**

1. **Orchestrator pattern (recommended, what we use):** run
   non-JavaScript logic outside Maestro, as a bash script that
   invokes Maestro with `-e KEY=VALUE` environment variable flags
   between runs. This is the split-flow pattern above.

2. **JS-only runScript:** write a `.js` file that does whatever you
   need. Limited — no HTTP fetches with auth cookies, limited Java
   interop. Not useful for the Dimagi OTP case because you can't
   import Playwright into Maestro's JS runtime.

3. **Java interop from GraalJS:** possible in theory (`Java.type(...)`)
   but Maestro's JS sandbox may or may not allow it. Untested.
   Probably brittle.

**Bottom line:** for any non-trivial external side effect, use the
orchestrator pattern. Maestro flows should only do UI interaction.

## Learning 2 — Maestro env var expansion needs `-e` flags

Trying to reference a shell env var inside a Maestro flow:

```yaml
- inputText: ${CONNECTID_E2E_PHONE}
```

Maestro does NOT automatically pick up environment variables. With
the above alone, Maestro types the literal string `${CONNECTID_E2E_PHONE}`
into the field (we saw this as "undefined" literals being typed into
phone fields in early debugging).

The flow's top-level `env:` block with `${VAR}` syntax is for
declaring variable *defaults*, not for reading shell env:

```yaml
env:
  CONNECTID_E2E_PHONE: ${CONNECTID_E2E_PHONE}  # does NOT expand from shell
```

**The reliable pattern:** pass values explicitly via `-e KEY=VALUE`
on the CLI:

```bash
set -a; source .env.e2e.local; set +a
maestro test \
  -e "CONNECTID_E2E_PHONE=$CONNECTID_E2E_PHONE" \
  -e "CONNECTID_E2E_PIN=$CONNECTID_E2E_PIN" \
  .maestro/flows/my-flow.yaml
```

Then inside the flow body, `${CONNECTID_E2E_PHONE}` references the
Maestro variable that was set via `-e` and expands correctly.

**Gotcha:** `-e` values must be strings. If a var is unset in the
shell, `-e "KEY=$UNSET"` becomes `-e "KEY="` which sets an empty
string, not "undefined". The flow body will get empty strings
silently — leading to "nothing typed in this field" failures that
look like assertion timeouts.

## Learning 3 — Playwright with persisted Chromium user data dir

The Dimagi OTP lookup page requires Dimagi SSO. No API for it (or
none we could get provisioned in a reasonable time). But a human
sitting at the browser can log in via SSO and then read the OTP.

**Solution:** Playwright's `chromium.launchPersistentContext` with a
dedicated `userDataDir` under `.maestro/scripts/playwright/userdata/`
(gitignored). First run is headed (human logs in). All subsequent
runs are headless, reusing the saved session cookies automatically.

```javascript
const { chromium } = require('playwright');

const context = await chromium.launchPersistentContext(USER_DATA_DIR, {
    headless: !HEADED,
    viewport: { width: 1280, height: 900 },
});
```

**Env var toggle for headed mode:**

```javascript
const HEADED = process.env.PHASE9_HEADED === '1';
```

**First-time login flow:**

```javascript
if (!currentUrl.startsWith(PHASE9_URL)) {
    if (HEADED) {
        process.stderr.write(`Please complete Dimagi SSO login in the browser window.\n`);
        process.stderr.write(`Press ENTER when done.\n`);
        await new Promise((resolve) => {
            process.stdin.once('data', resolve);
            process.stdin.resume();
        });
        await page.goto(PHASE9_URL, { waitUntil: 'networkidle' });
    } else {
        die(2, `not authenticated; re-run with PHASE9_HEADED=1`);
    }
}
```

**Why this beats the alternatives:**

- **OAuth2 client credentials:** requires Dimagi ops provisioning,
  which we never got (and didn't need). The Playwright approach
  requires zero external action beyond the one-time Dimagi SSO
  login that a developer already has in their regular browser.
- **Importing cookies from Chrome/Safari:** possible via
  `browser_cookie3` or similar, but Chrome's cookies are encrypted
  and extraction is OS-specific. Breaks on Chrome updates. Playwright's
  own persistent context avoids all of that.
- **Service account in Dimagi SSO:** would work but requires Dimagi
  IT to create a test account. Same provisioning friction as OAuth2.
- **Hardcoded session cookie in .env.e2e.local:** fragile. Session
  cookies expire.

The `userdata/` directory is gitignored and contains a full Chromium
profile (including cached SSO cookies). Each developer has their
own. When a developer's Dimagi SSO session expires, they just run
`PHASE9_HEADED=1 node fetch-otp.js <phone>` once to re-login.

## Learning 4 — Split flows for external pauses

**Anti-pattern (what we tried first):** single Maestro flow that
does the whole registration, with a long `extendedWaitUntil` in the
middle for the user to manually enter the OTP:

```yaml
- extendedWaitUntil:
    visible: id: "full_name_field"
    timeout: 180000   # wait 3 minutes for human OTP entry
```

Problems with this:

- Timeouts don't scale. If the human is slow, the flow times out
  and has to restart from scratch.
- Debugging is painful. If something goes wrong between "OTP
  entered" and "name screen appeared," Maestro's error is about
  the assertion timeout, not the real issue.
- No control over what happened during the wait. If the app
  crashed or the user tapped the wrong thing, you can't tell.
- Flaky in CI — never works unattended.

**The split pattern:**

```yaml
# part-A.yaml: walks to a deterministic pause point, then exits cleanly
- extendedWaitUntil:
    visible: id: "otp_field"
    timeout: 15000
# (no action after this — Maestro exits successfully once the OTP field is visible)
```

```yaml
# part-B.yaml: assumes the simulator is already at the pause point state
- extendedWaitUntil:
    visible: id: "otp_field"
    timeout: 10000
- tapOn: id: "otp_field"
- inputText: ${CONNECTID_E2E_OTP}
# ... continue
```

Benefits:

- **Each part has its own timeline and error messages.** If part A
  fails, you know phone entry is broken. If part B fails, you know
  OTP entry or downstream is broken.
- **The external step is just a shell command.** You can debug it
  in isolation, replace it with a different data source, or mock it.
- **No "did the user do something?" ambiguity.** The simulator
  state between A and B is reproducible and inspectable.
- **Works equally well for human-in-the-loop AND fully automated
  runs.** Same split, different external step.

## Learning 5 — Capturing Kotlin/Native unhandled exceptions

When the iOS app crashed on `set_pin_button` tap, the crash log at
`~/Library/Logs/DiagnosticReports/CommCare-*.ips` showed the call
stack but NOT the exception message. Kotlin/Native's
`terminateWithUnhandledException` writes the exception details to
stderr, not to `os_log` and not to the crash reporter's diagnostic
report.

**`log stream` with app predicate filter doesn't capture it:**

```bash
xcrun simctl spawn booted log stream \
  --predicate 'processImagePath contains "CommCare"'
```

Returns UIKit + CoreFoundation messages but not the K/N stderr.

**`xcrun simctl launch --console` does capture it:**

```bash
xcrun simctl launch --console booted org.commcare.ios > /tmp/log.txt 2>&1 &
# Then run Maestro in the foreground
sleep 2
set -a; source .env.e2e.local; set +a
maestro test .maestro/flows/one-time-registration.yaml
# When the app crashes, kill the backgrounded console listener
```

The console output captures stdout AND stderr from the app process,
including the K/N unhandled exception dump. This is the only reliable
way to see the actual exception message.

**Even better for interactive debugging:** `--console-pty` keeps
the console attached as a pseudoterminal so the app doesn't exit
when the launcher disconnects.

## Learning 6 — Dual-path interop as a defensive pattern

Kotlin/Native's implicit Map-to-CFDictionary bridging works in some
runtime contexts and fails in others (specifically: Compose onClick
handlers on Xcode 26.4). We can't rely on a single bridging strategy.

**The dual-path pattern:**

```kotlin
try {
    // Preferred path: uses implicit K/N bridging
    val query = mapOf<Any?, Any?>(...)
    @Suppress("UNCHECKED_CAST")
    SecItemAdd(query as CFDictionaryRef, null)
} catch (_: ClassCastException) {
    // Fallback path: explicit NSMutableDictionary + CFBridgingRetain
    val dict = NSMutableDictionary().apply {
        setObject(value, forKey = key as NSCopyingProtocol)
    }
    withCFDictionary(dict) { cfDict -> SecItemAdd(cfDict, null) }
}
```

Where `withCFDictionary` does the CFBridgingRetain + reinterpret +
CFRelease dance.

**When to use this:** any K/N interop code that (a) uses `as
CFDictionaryRef` or similar unchecked casts, AND (b) runs in
contexts where K/N's bridging behavior might vary. Compose UI event
handlers are the big one.

**When NOT to use this:** code that only ever runs in standalone
contexts (unit tests, non-UI CLI tools). Adds complexity for no
benefit.

**Follow-up:** audit `app/src/iosMain/` and
`commcare-core/src/iosMain/` for other `as CF*` casts. Each is a
potential crash site under the same Xcode version combinations.

## Learning 7 — `simctl` workflow for reproducible tests

Fresh simulator state per test run is the foundation of Phase 9.
The canonical reset:

```bash
xcrun simctl terminate booted org.marshellis.commcare.ios 2>/dev/null || true
xcrun simctl uninstall booted org.marshellis.commcare.ios 2>/dev/null || true
xcrun simctl install booted "$APP_PATH"
xcrun simctl launch booted org.marshellis.commcare.ios >/dev/null
sleep 2  # let the app finish starting
```

This uninstalls the app (which wipes Keychain and app-container
state) and reinstalls fresh. `xcrun simctl erase` is a heavier
option that wipes the entire simulator (removes all apps, cache,
etc.) and requires `xcrun simctl shutdown` first — overkill for
most cases.

**Gotchas:**

- `xcrun simctl install` doesn't error on stale apps with wrong
  bundle IDs. We hit this early on: an older build had bundle ID
  `org.commcare.ios` and was still installed even after "fresh
  install" commands. Verify the installed app matches the expected
  bundle ID with:
  ```bash
  xcrun simctl listapps booted | grep -A 2 "marshellis\|commcare"
  ```
  Or check `Info.plist`:
  ```bash
  plutil -p "$APP_PATH/Info.plist" | grep CFBundleIdentifier
  ```

- `xcrun simctl launch` doesn't block until the app is ready. The
  app needs a couple of seconds after launch before it's
  interactable. Either `sleep 2` or have Maestro's first
  `extendedWaitUntil` target a known-visible element with a
  generous timeout.

## Learning 8 — Xcode version SDK/runtime mismatches

On Xcode 26.4, the default `iphonesimulator` SDK is `26.4`, but the
installed iOS simulator runtime was `26.3`. Running `xcodebuild` with
`-destination 'generic/platform=iOS Simulator'` failed with:

```
Supported platforms for the buildables in the current scheme is empty.
Ineligible destinations: iOS 26.4 is not installed.
```

**Fix:** explicit simulator UDID in the destination:

```bash
xcodebuild ... -destination 'id=AF5C58C3-794F-46BF-B941-3568117B8172'
```

The explicit UDID bypasses the SDK/runtime matching logic. `simctl
list devices available -j` gives the UDIDs.

**Alternative fix:** download the matching runtime via `xcodebuild
-downloadAllPlatforms` (or Xcode → Settings → Platforms). This is
slow (several GB) but persistent.

## Putting it all together: orchestrator template

Every subsequent wave should start from this skeleton:

```bash
#!/usr/bin/env bash
# .maestro/scripts/run-<waveN-scenario>.sh

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

# Load env
set -a
source .env.e2e.local
set +a

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
PLAYWRIGHT_SCRIPT="$ROOT/.maestro/scripts/playwright/fetch-otp.js"

# Fresh install
xcrun simctl terminate booted org.marshellis.commcare.ios 2>/dev/null || true
xcrun simctl uninstall booted org.marshellis.commcare.ios 2>/dev/null || true
xcrun simctl install booted "$ROOT/app/iosApp/build/Build/Products/Debug-iphonesimulator/CommCare.app"
xcrun simctl launch booted org.marshellis.commcare.ios >/dev/null
sleep 2

# Part A: deterministic walk to the external-data pause point
"$MAESTRO" test \
  -e "CONNECTID_E2E_PHONE=$CONNECTID_E2E_PHONE" \
  -e "CONNECTID_E2E_PHONE_LOCAL=$CONNECTID_E2E_PHONE_LOCAL" \
  -e "CONNECTID_E2E_COUNTRY_CODE=$CONNECTID_E2E_COUNTRY_CODE" \
  -e "CONNECTID_E2E_PIN=$CONNECTID_E2E_PIN" \
  .maestro/flows/<wave-name>-to-<pause>.yaml \
  --no-ansi

# External step: fetch data that Maestro can't get on its own
OTP="$(node "$PLAYWRIGHT_SCRIPT" "$CONNECTID_E2E_PHONE")"
if ! [[ "$OTP" =~ ^[0-9]{6}$ ]]; then
  echo "ERROR: invalid OTP: '$OTP'" >&2
  exit 2
fi

# Part B: continuation with the fetched data
"$MAESTRO" test \
  -e "CONNECTID_E2E_OTP=$OTP" \
  -e "CONNECTID_E2E_BACKUP_CODE=$CONNECTID_E2E_BACKUP_CODE" \
  .maestro/flows/<wave-name>-from-<pause>.yaml \
  --no-ansi

echo "=== $(basename "$0") complete ==="
```

Waves 2-11 should each follow this shape. Only the middle section
(what external data to fetch between part A and part B) varies.

## Follow-ups

- **Add a pre-commit hook** that blocks committing `.env.e2e.local`,
  raw base64 credentials, or `+7426` phone numbers in source files
  other than fixture-user.md. Mentioned in the Phase 9 spec §8.5
  but never wired. Trivial to add with git hooks, one-time setup.
- **Document the orchestrator pattern in a README** at
  `.maestro/README.md` so future contributors to the Maestro flows
  find it without having to read commit history.
- **Add a smoke test to CI** that runs Playwright in headless mode
  with a dummy phone number and asserts the script exits with the
  expected "not authenticated" error (exit code 2). This verifies
  the Playwright dependencies stay installed and the script
  structure doesn't drift.
- **Revisit biometric enrollment on iOS simulator** for Wave 2
  (login variants). Simulator biometric is toggleable via Features
  menu (`osascript` automation possible) but has quirks — document
  the working approach when we hit it.
