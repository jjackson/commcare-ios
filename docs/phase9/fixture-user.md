# Phase 9 Fixture User

This file documents the single Connect ID fixture user that all Phase 9 E2E
tests use. The user is created manually once and reused across runs via the
Connect ID recovery flow (never registration — that would pollute prod).

## Identity

- **Phone number:** `+74260000042` (see `CONNECTID_E2E_PHONE` in `.env.e2e.local`)
- **Display name:** `QA Test User`
- **Test opportunity:** pre-invited to a Connect Worker test opportunity (confirmed by JJ, 2026-04-08)

## Why this user exists

Connect-id's `TEST_NUMBER_PREFIX = "+7426"` (`users/const.py`) suppresses SMS
delivery for matching phone numbers but still generates the OTP token in the
database. A human or test harness can then fetch the current OTP by looking
it up in the Connect admin UI or calling the `generate_manual_otp` endpoint.
Together these let test automation drive Connect ID flows end-to-end without
a real phone ever receiving an SMS.

The user is pre-invited to a test opportunity because
`send_session_otp`/`confirm_session_otp` return 403 NOT_ALLOWED unless
`check_number_for_existing_invites(phone_number)` returns True
(see `users/views.py:955` and `users/views.py:968` in connect-id).

## OTP fetch — two modes

The helper script `.maestro/scripts/fetch-otp.sh` supports two modes:

**Interactive mode (current default for local dev).** Opens
`https://connect.dimagi.com/users/connect_user_otp/` in the default browser,
prompts the developer to paste the OTP from the browser back into the
terminal, and returns it to Maestro. Requires a Dimagi SSO session in the
browser (e.g. `jjackson@dimagi.com`). Works for one-off local runs and for
the one-time fixture user registration. Does **not** work unattended in CI
because it requires a TTY and human input.

**Automated mode (dormant — requires Dimagi provisioning).** If the env vars
`CONNECTID_E2E_CLIENT_ID` and `CONNECTID_E2E_CLIENT_SECRET` are set, the
script skips the interactive path and instead calls `/o/token/` with a
client-credentials grant, then `GET /users/generate_manual_otp`. This path
is intended for unattended CI. It is not currently used — when Dimagi
provisions a dedicated OAuth2 application for Phase 9 E2E testing, set the
secrets and the script picks them up automatically.

## Secrets

Never committed to this repo. They live in:

- **Local dev:** `.env.e2e.local` (gitignored). See `.env.e2e.local.example`.
- **Long-term storage:** the team's password manager.
- **GitHub Actions secrets (future, not yet wired):** if and when Wave 1
  moves to unattended CI, add `CONNECTID_E2E_CLIENT_ID` and
  `CONNECTID_E2E_CLIENT_SECRET` (Dimagi-provisioned) plus `CONNECTID_E2E_PHONE`,
  `CONNECTID_E2E_PHONE_LOCAL`, `CONNECTID_E2E_COUNTRY_CODE`,
  `CONNECTID_E2E_BACKUP_CODE`, `CONNECTID_E2E_PIN`.

For local interactive runs, the minimal required env vars are:

- `CONNECTID_E2E_PHONE` (full E.164, e.g. `+74260000042`)
- `CONNECTID_E2E_PHONE_LOCAL` (digits after country code, e.g. `4260000042`)
- `CONNECTID_E2E_COUNTRY_CODE` (e.g. `+7`)
- `CONNECTID_E2E_BACKUP_CODE` (captured during fixture user registration)

The OAuth2 client vars and `CONNECTID_E2E_PIN` are not required for local
interactive runs.

## Adding new test phone numbers

Phase 9 deliberately uses one fixture user. Adding a second number requires
a new Dimagi pre-invite request and a fresh manual registration walk-through.
Do not add numbers without a strong reason — accumulating fixture users
defeats the "one user, no pollution" property.

## How to invite a new number to a test opportunity

Send a Dimagi ops request:

> Please invite phone number `+74260000NNN` to a test opportunity on Connect
> Worker. This is for iOS E2E test automation; the number uses the `+7426`
> test prefix and will not receive real SMS.

(This was already done for `+74260000042` on 2026-04-08.)

## Backup-code-reset runbook

If `connect-id` returns repeated `INCORRECT_CODE` errors during the recovery
flow, the fixture user may have hit `MAX_BACKUP_CODE_ATTEMPTS`
(`users/const.py:33`), which is currently 3. Recovery procedure:

1. File a Dimagi ops request to reset `failed_backup_code_attempts` to 0 on
   the fixture user's `ConnectUser` record. Connect-id's
   `reset_failed_backup_code_attempts` model method exists for this.
2. While waiting, Wave 1 runs will fail at backup code entry. Do not run
   Wave 1 locally until the reset lands.
3. Once Dimagi confirms, the backup code returns to working.
4. If this happens repeatedly, audit the test for code that triggers the
   failure path against the real fixture user — it should mock the failure,
   not exhaust the real user.

## How to recreate the user from scratch

If the fixture user is somehow lost (account deactivated, deleted, etc.):

1. Confirm the phone number is still pre-invited to a test opportunity.
2. Run the one-time registration procedure from the Phase 9 plan (Task 0.2).
3. Update `.env.e2e.local` with the new backup code (and PIN if applicable).
4. Update this doc with any changed values.
5. Run `maestro test .maestro/flows/connect-id-recovery.yaml` locally to verify.
