# Phase 9 Fixture User

This file documents the single Connect ID fixture user that all Phase 9 E2E
tests use. The user was created manually once and is reused across runs.

## Identity

- **Phone number:** `+74260000NNN` (see `CONNECTID_E2E_PHONE` in CI secrets / `.env.e2e.local`)
- **Display name:** `QA Test User`
- **Test opportunity:** `<opportunity-id-from-dimagi>` on Connect Worker — *TBD: filled in once Dimagi completes the pre-invite ask documented below*

## Why this user exists

Connect-id's `TEST_NUMBER_PREFIX = "+7426"` (`users/const.py`) suppresses SMS
delivery for matching phone numbers but still generates the OTP token in the
database. The `GET /users/generate_manual_otp` endpoint, protected by
OAuth2 client credentials, returns that token directly. Together these let
test automation drive Connect ID flows end-to-end without a real human
receiving an SMS.

The user is pre-invited to a test opportunity because
`send_session_otp`/`confirm_session_otp` return 403 NOT_ALLOWED unless
`check_number_for_existing_invites(phone_number)` returns True
(see `users/views.py:955` and `users/views.py:968` in connect-id).

## Secrets

The actual phone number, backup code, PIN, and OAuth credentials are
**never committed to this repository**. They live in:

- **GitHub Actions secrets** (for CI):
  - `CONNECTID_E2E_CLIENT_ID`
  - `CONNECTID_E2E_CLIENT_SECRET`
  - `CONNECTID_E2E_PHONE`
  - `CONNECTID_E2E_PHONE_LOCAL`
  - `CONNECTID_E2E_COUNTRY_CODE`
  - `CONNECTID_E2E_BACKUP_CODE`
  - `CONNECTID_E2E_PIN`
- **Local dev:** `.env.e2e.local` (gitignored). See `.env.e2e.local.example`.
- **Long-term storage:** the team's password manager.

## Adding new test phone numbers

Phase 9 deliberately uses one fixture user. Adding a second number requires
re-running Task 0.1 (Dimagi ask) and Task 0.2 (manual creation) and
documenting the new fixture here. Do not add numbers without a strong
reason — accumulating fixture users defeats the "one user, no pollution"
property.

## How to invite a new number to the test opportunity

Send a Dimagi ops request:

> Please invite phone number `+74260000NNN` to test opportunity
> `<opportunity-id>` on Connect Worker. This is for iOS E2E test
> automation; the number uses the `+7426` test prefix and will not
> receive real SMS.

## Backup-code-reset runbook

If `connect-id` returns repeated `INCORRECT_CODE` errors during the
recovery flow, the fixture user may have hit `MAX_BACKUP_CODE_ATTEMPTS`
(`users/const.py:33`), which is currently 3. Recovery procedure:

1. File a Dimagi ops request to reset `failed_backup_code_attempts` to 0
   on the fixture user's `ConnectUser` record. Connect-id's
   `reset_failed_backup_code_attempts` model method exists for this.
2. While waiting, all E2E tests using the fixture user will fail. Mark
   the affected nightly job as a known failure in `e2e-ui.yml`.
3. Once Dimagi confirms the reset, the backup code returns to working.
4. If this happens repeatedly, audit Phase 9 tests for code that triggers
   recovery failure paths and fix them — they should mock the failure,
   not actually exhaust the real fixture user.

## How to recreate the user from scratch

If the fixture user is somehow lost (account deactivated, deleted, etc.):

1. Confirm the phone number is still pre-invited to the test opportunity.
2. Re-run Task 0.2 of the Phase 9 plan (manual creation via simulator).
3. Update the GitHub secrets with the new backup code and PIN.
4. Update this doc with any changed values.
5. Run a Wave 1 nightly to verify everything still works.
