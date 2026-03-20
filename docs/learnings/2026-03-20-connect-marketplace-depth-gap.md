# Connect Marketplace Implementation Is Scaffold-Level, Not Production

**Date:** 2026-03-20
**Context:** Phase 5 Wave 6 built Connect marketplace screens, but a deep dive into Android production code reveals the iOS implementation operates on a fundamentally incorrect understanding of the data model and lifecycle.

## What Happened

Wave 6 created marketplace screens (opportunities list, detail, learn/delivery progress, payments, messaging) based on high-level spec descriptions. The screens compile and the API client fetches data, but:

1. **Data models are wrong.** Our `Opportunity` is a flat 15-field model. Android has ~15 interconnected data models with payment units, assessment records, delivery records with flags, learning modules, linked app records, and verification data.

2. **The lifecycle is wrong.** We assumed: browse → claim → deliver. Android's actual flow is: browse → start learning (separate API call) → complete learning modules in a separate CommCare app → pass assessment → THEN claim for delivery → deliver using another CommCare app → payments tracked per-unit.

3. **Messaging is fundamentally different.** We built simple thread-based messaging. Android uses channel-based messaging with end-to-end AES-GCM encryption, per-channel consent, channel encryption keys, 30-second polling, and unsent message retry.

4. **The entire "app launch" concept is missing.** Connect opportunities involve downloading and launching separate CommCare apps (learn app, deliver app) with SSO auto-login. Our iOS app has no mechanism for this.

## Why This Was Missed

The Phase 5 spec was written from a high-level reading of the Android code structure (class names, file organization) rather than from reading the actual implementation logic. The spec described "Opportunities listing, job claiming, progress tracking, payment, messaging" as simple CRUD screens, when in reality each of these involves complex multi-step flows with specific API contracts.

Specific misses:
- The `start_learn_app` API was listed in the spec but its role in the lifecycle wasn't understood
- Payment units were not mentioned at all
- The assessment flow was not mentioned
- Message encryption was not mentioned
- App download/launch was listed as a non-goal but is actually core to the Connect flow

## What Needs to Be Done

### Critical (Connect won't work without these)
1. Rewrite data models to match actual server response format
2. Fix API response parsing to handle real response shapes
3. Implement correct lifecycle: Available → Learning → Assessment → Delivering → Complete
4. Add delivery record list with per-unit tracking
5. Add payment confirmation with confirm/revert windows
6. Fix messaging to use channels with per-channel consent

### Important (Core UX gaps)
7. App download + install flow for learn/deliver apps
8. SSO auto-login into learn/deliver apps
9. Learning progress screen with certificate and assessment status
10. 4-step progress bar on job detail
11. Job list sections (in-progress / new / finished)
12. Per-payment-unit breakdown

### Deferred (Can ship without)
13. End-to-end message encryption (AES-GCM)
14. Background sync / offline support
15. Push notification integration for messages
16. Working hours display and expiry warnings

## Lesson

When building a client for an existing server API:
1. **Read the actual API responses**, not just endpoint names. Use curl to call every endpoint and save the response shapes.
2. **Read the data models**, not just the UI code. The Android `ConnectJob*Record` classes define the real contract.
3. **Trace the lifecycle end-to-end** with a debugger or logs on a real device, not by reading fragments in isolation.
4. **Don't assume CRUD** — most real-world features have multi-step flows with state machines that aren't obvious from screen names.
