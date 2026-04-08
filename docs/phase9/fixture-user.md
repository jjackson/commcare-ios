# Phase 9 Fixture User

This file documents the single Connect ID fixture user that Phase 9
E2E tests use and the Playwright-based OTP fetch that makes the
orchestrators work.

## Identity

- **Phone number:** `+74260000042` (pre-invited to a Connect Worker test opportunity, 2026-04-08)
- **Display name:** Hal Test
- **Test opportunity:** confirmed pre-invited on 2026-04-08

The backup code, PIN, and other credentials live in `.env.e2e.local`
(gitignored) and the team password manager. They are never committed
to source control.

## Why this user exists

Connect-id's `TEST_NUMBER_PREFIX = "+7426"` (`users/const.py`)
suppresses SMS delivery for matching phone numbers but still generates
the OTP token in the database. Staff at `@dimagi.com` can look up the
current OTP via `https://connect.dimagi.com/users/connect_user_otp/`
(signed in via Dimagi SSO). Phase 9 uses Playwright to automate that
lookup from a persisted Chromium session.

The fixture user is pre-invited to a Connect Worker test opportunity
because `send_session_otp`/`confirm_session_otp` return 403 NOT_ALLOWED
unless `check_number_for_existing_invites(phone_number)` returns True
(see `users/views.py:955,968` in connect-id).

## OTP fetch — Playwright with persisted Dimagi SSO

`.maestro/scripts/playwright/fetch-otp.js` uses Playwright's
`launchPersistentContext` to maintain a Chromium user-data directory
at `.maestro/scripts/playwright/userdata/`. The first run (in headed
mode) requires a manual Dimagi SSO login; all subsequent runs reuse
the cached session headlessly.

**First-time setup** (one-time per workstation):

```bash
cd .maestro/scripts/playwright
npm install  # installs playwright
PHASE9_HEADED=1 node fetch-otp.js +74260000042
```

A headed Chromium window opens and navigates to
`https://connect.dimagi.com/users/connect_user_otp/`. Sign in via
Dimagi SSO, then press Enter in the terminal. The script navigates
to the OTP page, scrapes the current OTP, prints it to stdout, and
exits. The browser profile is saved to `userdata/` for subsequent
runs.

**Subsequent runs** (headless, fast):

```bash
node .maestro/scripts/playwright/fetch-otp.js +74260000042
# prints: 123456
```

**Environment variables (optional):**

- `PHASE9_HEADED=1` — run headed (for debugging or first-time login)
- `PHASE9_URL=<url>` — override the OTP lookup URL
- `PHASE9_USER_DATA_DIR=<path>` — override the user data dir

**Dormant automated mode:** if `CONNECTID_E2E_CLIENT_ID` and
`CONNECTID_E2E_CLIENT_SECRET` are ever provisioned by Dimagi (an
OAuth2 client with permission to call `generate_manual_otp`),
`.maestro/scripts/fetch-otp.sh` provides an alternative path. That
script is not currently used — the Playwright approach works without
Dimagi provisioning.

## Secrets

Everything is stored in:

- **Local dev:** `.env.e2e.local` (gitignored). See `.env.e2e.local.example`.
- **Long-term:** team password manager.
- **GitHub Actions secrets:** not currently wired. Phase 9 is local-only.

Minimum required env vars for the Wave 0 and Wave 1 orchestrators:

```bash
CONNECTID_E2E_PHONE=+74260000042        # full E.164
CONNECTID_E2E_PHONE_LOCAL=4260000042    # digits-only, no country code
CONNECTID_E2E_COUNTRY_CODE=+7
CONNECTID_E2E_PIN=<6 digits>
CONNECTID_E2E_BACKUP_CODE=<6 digits>
```

## Running the orchestrators

**One-time registration** (creates the fixture user on first run;
errors on subsequent runs because the account already exists):

```bash
.maestro/scripts/run-registration.sh
```

**Recovery** (idempotent — runs as many times as you want against
the same server-side fixture user):

```bash
.maestro/scripts/run-recovery.sh
```

Each script:

1. Reads `.env.e2e.local`
2. Erases and re-installs the app on the booted iPhone simulator
3. Runs a Maestro "part A" flow (SetupScreen → phone entry → PIN → waits at OTP screen)
4. Runs the Playwright fetch-otp.js to grab the current OTP
5. Runs a Maestro "part B" flow (types OTP → verify → name → backup code → Success)

Runtime: about 60–90 seconds end-to-end.

## Adding new test phone numbers

Phase 9 deliberately uses one fixture user. Adding a second number
requires a new Dimagi pre-invite request and a fresh registration
walkthrough. Do not add numbers without a strong reason —
accumulating fixture users defeats the "one user, no pollution"
property.

## How to invite a new number to a test opportunity

Send a Dimagi ops request:

> Please invite phone number `+74260000NNN` to a test opportunity on
> Connect Worker. This is for iOS E2E test automation; the number
> uses the `+7426` test prefix and will not receive real SMS.

## Backup-code-reset runbook

If connect-id returns repeated `INCORRECT_CODE` errors during the
recovery flow, the fixture user may have hit
`MAX_BACKUP_CODE_ATTEMPTS` (`users/const.py:33`, currently 3).
Recovery procedure:

1. File a Dimagi ops request to reset
   `failed_backup_code_attempts` to 0 on the fixture user's
   `ConnectUser` record. Connect-id has a
   `reset_failed_backup_code_attempts` model method for this.
2. While waiting, `.maestro/scripts/run-recovery.sh` will fail at
   backup code entry. Do not run it locally until the reset lands.
3. Once Dimagi confirms, the backup code works again.
4. If this happens repeatedly, audit any test that actually triggers
   the failure path against the real fixture user — such tests
   should mock the failure, not exhaust the real user.

## How to recreate the user from scratch

If the fixture user is somehow lost (account deactivated, deleted,
etc.):

1. Confirm the phone number is still pre-invited to the test
   opportunity.
2. Run `.maestro/scripts/run-registration.sh`. This performs the full
   one-time registration walkthrough and completes in about a
   minute.
3. Update `.env.e2e.local` with the new backup code and PIN (the
   orchestrator prints them; paste into the file manually).
4. Run `.maestro/scripts/run-recovery.sh` to verify the fixture is
   usable.
