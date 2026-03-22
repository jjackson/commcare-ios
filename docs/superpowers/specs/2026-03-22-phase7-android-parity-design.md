# Phase 7: Full Android Parity — Design Spec

**Date:** 2026-03-22
**Status:** Design
**Prerequisite:** Phases 1-6 complete. App on TestFlight. 1,162+ tests passing.

## Problem Statement

A systematic comparison of the iOS app against Android CommCare revealed 15 missing features and 22 areas where existing features lack test coverage. While the app handles the core form entry/case management/sync loop, field workers encounter broken UIs (wrong select widget appearances), missing workflows (form chaining dead-ends), and security gaps (XOR cipher placeholder). Test coverage gaps mean regressions in features like PIN login, multi-app switching, and repeat groups would go undetected.

## Goals

- Achieve full feature parity with Android CommCare for all features used in production CommCare apps
- Eliminate the XOR cipher security placeholder with production-grade AES encryption
- Close all test coverage gaps for existing implemented features
- Maintain backward compatibility — no breaking changes to existing working features

## Non-Goals

- Android-specific integrations (Intent callouts, Simprints, NFC, USSD, Content Providers)
- CommCare Sense Mode (low-literacy UI — not used in current deployments)
- Push notifications (polling architecture is sufficient for MVP)
- Enterprise MDM provisioning

## Architecture

### Design Principles

1. **Additive changes only** — New widgets, new test files, new sync logic. No rewriting existing working code unless required (e.g., AES replacing XOR).
2. **Test-first for new features** — Each new feature wave includes tests alongside implementation.
3. **Platform abstraction pattern** — New platform features (video capture, document picker) follow the existing `expect`/`actual` pattern with JVM stubs and iOS implementations.
4. **Appearance-driven rendering** — Select widget variants are driven by the `appearance` attribute already parsed from XForms. The rendering layer in `FormEntryScreen.kt` branches on appearance strings.

### Wave Structure

Ten waves organized by dependency and risk:

| Wave | Name | Type | Items | Risk |
|------|------|------|-------|------|
| 1 | AES Encryption at Rest | Security | 1 feature | High — touches credential storage |
| 2 | Select Appearance Variants | Form UI | 3 features | Medium — many widget variants |
| 3 | Form Workflow Completeness | Navigation | 3 features | High — session stack changes |
| 4 | Sync & Update Hardening | Data | 2 features | High — data integrity |
| 5 | Case Management Completeness | UI/Data | 2 features | Medium |
| 6 | Multimedia Capture | Platform | 3 features | Medium — iOS-specific |
| 7 | Connect Messaging Refinement | API | 1 feature | Low |
| 8 | Test Coverage: Form Entry & Navigation | Tests | 7 test gaps | Low |
| 9 | Test Coverage: Auth & Multi-App | Tests | 6 test gaps | Low |
| 10 | Test Coverage: Connect & Infrastructure | Tests | 9 test gaps | Low |

### Key Technical Decisions

1. **AES-GCM via platform expect/actual** — iOS uses CommonCrypto/CryptoKit, JVM uses javax.crypto. No third-party dependency needed.
2. **Select appearances in FormEntryScreen** — Extend the existing `when (question.dataType)` block with nested appearance checks. No new screen files — just new `@Composable` functions in the form entry module.
3. **Incremental sync** — Use the existing `syncToken` header mechanism but add response hash comparison to skip full re-parse when data hasn't changed.
4. **Inline multimedia** — Parse `itext` media references from form labels. Render images inline and add audio play buttons within question label composables.

## Success Criteria

- All 15 missing features implemented and tested
- All 22 test coverage gaps filled
- Total test count increases from 1,162+ to ~1,250+
- `./gradlew jvmTest` passes for both commcare-core and app
- `compileCommonMainKotlinMetadata` passes
- No regressions in existing tests
