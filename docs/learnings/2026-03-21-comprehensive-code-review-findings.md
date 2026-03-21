# Learning: Comprehensive Code Review Reveals Systemic iOS Platform Gaps

**Date**: 2026-03-21
**Context**: Full codebase review before TestFlight submission (issues #308-#328, PRs #329-#330)
**Status**: Resolved

## Problem

A comprehensive 5-agent code review before TestFlight submission found 30 issues across the codebase, including:
- iOS `platformSynchronized` was a **no-op** (all thread synchronization disabled on iOS)
- iOS `PlatformThreadLocal` was a **shared var** (not thread-local at all)
- `IosInMemoryStorage.isEmpty()` returned the **opposite value**
- `readBytes()` had an **infinite loop on EOF** + offset bug
- Modified UTF-8 encoder **corrupted emoji** on iOS
- NSUserDefaults stored **passwords in plaintext** (not Keychain)
- 12 ViewModels **leaked CoroutineScopes** (never cancelled)
- 113 app JVM tests were **not running in CI**
- Connect ID/marketplace had **zero test coverage** (462 lines of hand-rolled JSON parsing)

## Root Cause

Three systemic patterns:
1. **iOS stubs never upgraded**: Early iOS implementations were placeholders ("iOS is single-threaded") that were never revisited when K/N's memory model changed or the app grew
2. **No iOS-specific testing**: `app/src/iosTest/` didn't exist; iOS platform code was only exercised indirectly
3. **Rapid feature development outpaced review**: Phase 5 added 28 PRs with no code review, accumulating technical debt in security (plaintext credentials), thread safety, and API correctness

## Fix / Key Takeaway

1. **Schedule periodic code reviews** — don't defer all review to pre-release. A review at Phase 3 completion would have caught the iOS concurrency issues before 50+ files depended on them
2. **iOS platform code needs dedicated tests** — every `actual` implementation in iosMain/ should have a corresponding test in iosTest/
3. **Audit placeholder implementations before shipping** — search for "TODO", "stub", "placeholder", "single-threaded" comments and verify each one is still accurate
4. **CI must run all test suites** — the 113 app JVM tests weren't in CI for months. Every test suite must have a CI gate
