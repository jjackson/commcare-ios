# Bug: Connect ID state inconsistency between SetupScreen and Connect menu

**Found by:** Phase 9 Wave 2 E2E scouting, 2026-04-08
**Severity:** Medium — user-visible, reproducible, not crashing
**Affects:** iOS, CommCare.app post-recovery flow

## Reproduce

1. Run `.maestro/scripts/run-recovery.sh` successfully (user recovers via backup code).
2. Tap `done_button` on the Success screen.
3. Observe SetupScreen: "GO TO CONNECT MENU" button AND "Signed in to Personal ID ✓" text are both visible.
4. Tap "GO TO CONNECT MENU".
5. Observe: the Connect menu screen shows `errorMessage = "Not signed in to ConnectID"`.

Two UI surfaces disagree about whether the same user is signed in.

## Root cause (hypothesis)

Two sources of truth for "is the user signed in":

| Source | Used by | Logic |
|---|---|---|
| `ConnectIdRepository.isRegistered()` | `SetupScreen` via `App.kt:49,63,141` | `getUser() != null` — true if any user record exists |
| `ConnectIdTokenManager.getConnectIdToken()` | `OpportunitiesViewModel`, `MessagingViewModel` | Returns cached token if unexpired, else calls `refreshConnectIdToken()` which needs `KEY_CONNECT_USERNAME` + `KEY_CONNECT_PASSWORD` from the keychain. Returns null on any failure. |

After recovery, the app stores a user record but does NOT persist a usable ROPC password pair. Recovery authenticates via backup code, not via the standard password grant. Consequence: `isRegistered()` returns true, but `getConnectIdToken()` returns null, and the two UIs show contradictory states.

## Suggested fix directions

Not a full design — just pointers:

- **Option A:** unify on `getConnectIdToken() != null` as the single "signed in" signal. Forces SetupScreen to show the logged-out variant after recovery, which is wrong in its own way (the user just finished recovering).
- **Option B:** on successful recovery, call `refreshConnectIdToken()` explicitly with the appropriate credentials. Requires the recovery flow to have enough material to mint a token — it may not (recovery trades a backup code for session credentials, which may or may not include what ROPC needs).
- **Option C:** extend the token manager with a "session token from recovery" path that stores a short-lived token without requiring ROPC password. Matches how `send_session_otp` / `confirm_session_otp` already work on the server side.

Prefer C. Least invasive, most honest about what recovery actually produces.

## Test coverage

`.maestro/flows/connect-menu-entry.yaml` documents the current buggy behavior — it asserts the error string is visible. When the bug is fixed, this test will fail and the fixer should update the assertion to the corrected state (e.g., assert the opportunities list is visible instead).

## Links

- SetupScreen: `app/src/commonMain/kotlin/org/commcare/app/ui/SetupScreen.kt:96-117,184-202`
- App state wiring: `app/src/commonMain/kotlin/org/commcare/app/App.kt:49,63,141`
- ConnectIdRepository.isRegistered: `app/src/commonMain/kotlin/org/commcare/app/storage/ConnectIdRepository.kt:58`
- ConnectIdTokenManager: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/ConnectIdTokenManager.kt:49-76,174`
- OpportunitiesViewModel error path: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/OpportunitiesViewModel.kt:80-85`
