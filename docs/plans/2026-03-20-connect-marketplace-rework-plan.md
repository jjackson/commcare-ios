# Connect Marketplace Rework Plan

**Date:** 2026-03-20
**Status:** Draft
**Context:** Phase 5 Wave 6 built scaffold-level marketplace screens. A deep dive into Android production code reveals the data models, lifecycle, API parsing, and messaging are fundamentally different from what was implemented. This plan rewrites the marketplace to match Android production.

## Scope

Rework Connect marketplace screens to match Android CommCare Connect production behavior. Organized by priority — Critical must ship, Important is core UX, Deferred can wait.

## Wave A: Data Models + API Fixes (Foundation)

**Must do first — everything else depends on correct data models.**

### Tasks:
1. **Rewrite Opportunity data model** to match actual API response:
   - Add: `maxVisits`, `maxDailyVisits`, `startDate`, `endDate`, `dailyStartTime`, `dailyFinishTime`, `paymentAccrued`, `dateClaimed`, `isUserSuspended`, `isActive`
   - Add nested: `paymentUnits[]` (name, maxTotal, maxDaily, amount), `learnApp` (installUrl, passingScore), `deliverApp` (installUrl)
   - Add nested: `learnings[]`, `assessments[]` (score, passingScore, passed), `deliveries[]` (deliveryId, date, status, unitName, entityName, flags[]), `payments[]` (amount, date, confirmed, confirmedDate)

2. **Rewrite ConnectMarketplaceApi response parsing** — curl every endpoint with a real token to get actual response shapes, then fix parsers

3. **Add API version header** — `"Accept": "application/json; version=1.0"` on all Connect API calls

4. **Fix `startLearnApp` body format** — send `{"opportunity": jobUUID}`

5. **Fix `confirmPayment` body format** — send `{"payments": [{"id": "...", "confirmed": true}]}` array

### Acceptance:
- [ ] Opportunity list loads with all fields populated
- [ ] Tapping an opportunity shows correct detail data
- [ ] API calls use correct request/response formats

## Wave B: Correct Lifecycle + Job Detail Screens

### Tasks:
1. **Implement job lifecycle states**: Available → Learning → Delivering → Finished
   - Track status per-opportunity in local state
   - Show different UI based on status

2. **Split Job Intro vs Job Detail**:
   - Unclaimed: show full description, learn modules list, delivery details (max visits, payment per visit, days remaining)
   - Claimed/In-Progress: show 4-step progress bar (New → Learn → Review → Delivery), dynamic action button

3. **Learning Progress screen**:
   - Progress bar with percentage
   - Module completion list
   - Assessment status (not started / in progress / passed / failed)
   - Certificate display when passed
   - Dynamic button: "Continue Learning" → "Go to Assessment" → "View Delivery Details"

4. **Delivery Details screen** (pre-delivery info):
   - Total visits allowed, days remaining, max daily visits, payment per visit
   - Payment unit breakdown if multi-payment
   - "Start Delivering" button (calls `POST /api/opportunity/{id}/claim`)

5. **Delivery Progress screen** with two tabs:
   - **Progress tab**: circular progress, per-payment-unit breakdown (approved/remaining/earned), drill-down to delivery list
   - **Payment tab**: earned vs transferred summary, individual payment records

6. **Delivery List screen**:
   - Filter by: All / Approved / Rejected / Pending
   - Each delivery: entity name, date, status badge, reason/flags

7. **Payment Confirmation flow**:
   - Confirm/revert buttons with time windows (7-day confirm, 24-hour undo)
   - Confirmation dialog with payment details
   - Banner for unconfirmed payments

### Acceptance:
- [ ] Unclaimed opportunities show job intro with learn modules and delivery details
- [ ] Claimed opportunities show 4-step progress bar
- [ ] Learning progress tracks module completion
- [ ] Delivery progress shows per-unit breakdown
- [ ] Payments can be confirmed/reverted within time windows

## Wave C: Messaging Rework

### Tasks:
1. **Switch from threads to channels** — each channel has: channelId, channelName, consent status
2. **Per-channel consent** — subscribe/unsubscribe per channel, not global
3. **Channel list screen** — show channels with preview, unread count, consent status
4. **Message screen** — bubble UI with sent/received styling, 30-second polling for new messages
5. **Unsent message retry** — queue messages when offline, retry on connectivity
6. **Fix API endpoints** — use correct messaging API format

### Deferred to later:
- End-to-end encryption (AES-GCM) — complex, can ship without initially
- Push notification integration
- Message delivery confirmations

### Acceptance:
- [ ] Messaging screen shows channels
- [ ] Can subscribe/unsubscribe per channel
- [ ] Can send and receive messages
- [ ] Messages poll every 30 seconds when chat is open

## Wave D: App Download + Launch (Complex)

### Tasks:
1. **App download flow** — download a CommCare app (learn or deliver) from install URL
   - Progress bar during resource download
   - Resource validation after download

2. **App launch integration** — launch the learn/deliver app with SSO
   - Generate random password for linked app
   - Auto-login via ConnectID SSO
   - Track linked app records (which apps are installed for which opportunities)

3. **Wire into lifecycle** — "Continue Learning" button downloads + launches learn app, "Start Delivering" downloads + launches deliver app

### Note:
This is the most complex piece. On Android, it leverages the multi-app architecture (multiple CommCare apps installed simultaneously). On iOS, we already have multi-app support (Phase 5 Wave 3), so the infrastructure exists — we just need to wire the download + launch + SSO flow.

### Acceptance:
- [ ] Can download learn app from opportunity
- [ ] Can launch learn app with auto-login
- [ ] Can download deliver app
- [ ] Can launch deliver app with auto-login
- [ ] Linked app records track which apps are for which opportunities

## Wave E: Job List Polish

### Tasks:
1. **Section the job list** — In-Progress / New / Finished sections
2. **Sort by last accessed** within in-progress section
3. **Expiry warnings** — red text when < 5 days remaining
4. **Working hours display** with timezone conversion
5. **User suspended state** handling
6. **Card status messages** — max reached, daily max reached, etc.
7. **Unread badge** in nav drawer for messaging

### Acceptance:
- [ ] Job list has three sections
- [ ] Expiry warnings show in red
- [ ] Working hours displayed correctly

## Estimated Effort

| Wave | Scope | Files | Priority |
|------|-------|-------|----------|
| A: Data + API | Foundation rewrite | ~5 | Critical |
| B: Lifecycle + Screens | Core UX | ~12 | Critical |
| C: Messaging | Channel-based rework | ~5 | Important |
| D: App Download | Learn/deliver launch | ~8 | Important |
| E: Polish | List sections, warnings | ~5 | Nice-to-have |
| **Total** | | **~35** | |

## Recommended Order

A → B → C → D → E

Each wave is independently testable. Wave A must come first since all other waves depend on correct data models. Waves C and D can run in parallel after B.
