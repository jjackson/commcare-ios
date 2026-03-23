# Phase 7: Full Android Parity — Completion Report

**Date:** 2026-03-23
**Duration:** 1 day (plan + implementation)
**PRs:** #360-#370 (11 PRs, all merged)
**Issues:** #350-#359 (all closed)

## Summary

Phase 7 closed all feature and test coverage gaps between the iOS app and Android CommCare, identified via a systematic comparison of both codebases. 15 missing features were implemented and 22 previously untested features received test coverage.

## What Was Done

### Feature Waves (1-7)

| Wave | Feature | Key Changes |
|------|---------|-------------|
| 1 | AES Encryption | Replaced XOR cipher with AES-GCM (JVM) / AES-CBC+HMAC (iOS), PBKDF2 PIN hashing, legacy migration |
| 2 | Select Appearances | 7 appearance variants (minimal, compact, quick, combobox, label, list-nolabel, collapsible), DateTime widget |
| 3 | Form Workflow | Post-form navigation via session stack, inline multimedia labels, auto-send queued forms |
| 4 | Sync & Update | Incremental sync via SHA-256 hash comparison, staged upgrade with rollback |
| 5 | Case Management | Tabbed case detail view, improved image field display |
| 6 | Multimedia Capture | Video capture + document upload expect/actual with iOS implementations |
| 7 | Connect Messaging | Per-channel consent toggle with optimistic UI update |

### Test Coverage Waves (8-10)

| Wave | Tests Added | Areas Covered |
|------|-------------|---------------|
| 8 | 7 files, 42 tests | Swipe nav, form drafts, repeat groups, field-list, grid menu, language switching, calendars |
| 9 | 6 files, 30 tests | PIN login, biometric auth, multi-app switching, demo mode, tiered cases, form chaining |
| 10 | 9 files, 41 tests | SSO, heartbeat, auto-update, offline sync, messaging polling, diagnostics, recovery, quarantine, remote search |

## Code Review Findings

Every PR received a code review. Issues found and fixed:

| PR | Issue | Fix |
|----|-------|-----|
| #361 | Key migration overwrites old keychain key before XOR decrypt | Save legacy key under separate alias |
| #362 | DATETIME unreachable (missing mapControlType mapping) | Add DATATYPE_DATE_TIME mapping |
| #362 | Quick appearance auto-advances past constraint violations | Gate nextQuestion() on answerQuestionString() return value |
| #362 | Combobox text state goes stale on external answer changes | Use remember(question.answer) |
| #365 | selectedTabIndex not reset on loadDetail causing crash | Reset to 0 at start of loadDetail() |
| #366 | Missing isSourceTypeAvailable guard in PlatformVideoCapture | Add camera availability check |
| #366 | Missing security-scoped resource access in PlatformDocumentPicker | Acquire scope, copy to temp, release |
| #367 | JSON injection in updateChannelConsent (missing escapeJson) | Add escapeJson() to channelId |
| #367 | Optimistic update not reverted when token is null | Revert UI and set error message |
| #367 | Silent exception swallowing in catch block | Set errorMessage in catch |

## Metrics

- **Total test count:** 343 (up from ~270 pre-Phase 7)
- **New test files:** 22
- **New platform files:** 4 (PlatformVideoCapture, PlatformDocumentPicker — expect + actual each)
- **Code review issues found:** 10 (all fixed before merge)
- **Lines added:** ~3,500 across all waves

## Notable Technical Decisions

1. **AES-CBC+HMAC on iOS instead of AES-GCM** — CommonCrypto's GCM APIs are SPI (not public). CBC+HMAC (Encrypt-then-MAC) provides equivalent authenticated encryption via the public API. Format is device-local, so cross-platform compatibility is not required.

2. **Session-scoped restore hash** — The incremental sync hash is not persisted to disk. The sync token (which IS persisted) already handles the common case via 412 responses. The hash catches the edge case of 200 responses with unchanged data.

3. **Optimistic UI updates for consent** — Per-channel consent uses optimistic updates with revert-on-failure, providing instant UI feedback while handling API failures gracefully.
