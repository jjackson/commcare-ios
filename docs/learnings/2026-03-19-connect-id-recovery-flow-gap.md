# Connect ID Recovery Flow Was Missing From Wave 5 Implementation

**Date:** 2026-03-19
**Context:** Phase 5 Wave 5 (Connect ID) implemented only the new-user registration flow. The recovery flow (existing user, new device) was mentioned in a single line of the spec but never implemented.

## What Happened

The Phase 5 spec (Section 4.7) described the 8-step registration wizard in detail but only included recovery as a one-line footnote:

> "Recovery flow (existing user, new device): Phone → Biometric → Backup Code → Photo → Success."

This line was also incorrect — the actual recovery flow is Phone → OTP → Name → Backup Code (input, not set) → Biometric → Success, with no photo step.

The implementation plan (Wave 5 Tasks 1-5) never mentioned recovery. The ConnectIdViewModel was built as a linear 8-step wizard with no branching logic.

## The Actual Branching Point

The ConnectID server determines new vs recovery at the `check_name` step:
- `POST /users/check_name` returns `account_exists: true/false`
- If `true`: user already exists → recovery mode → enter existing backup code → get credentials
- If `false`: new user → set backup code → capture photo → create account

This is a single API response field that switches the entire second half of the wizard.

## Root Cause

The spec was written based on high-level Android code reading but missed this critical branching logic. The `PersonalIdBackupCodeFragment` in Android switches between "display mode" (new registration) and "input mode" (recovery) based on `sessionData.accountExists`, but this detail wasn't captured in the spec.

## Lesson

When speccing a multi-step wizard that talks to an external server:
1. Document the **response shape** of each API call, not just the endpoint
2. Identify **branching points** where server responses change the flow
3. Write acceptance criteria for BOTH the happy path AND the recovery/error paths
4. Don't bury alternate flows in footnotes — give them their own section with the same level of detail
