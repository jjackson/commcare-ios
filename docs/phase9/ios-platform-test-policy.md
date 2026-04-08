# Phase 9 iOS Platform Test Policy

## Rule

Any new iOS platform code (anything in `app/src/iosMain/` or
`commcare-core/src/iosMain/`) touched in pursuit of a Phase 9 E2E flow
gets a paired unit test in `iosTest/` **before** the E2E flow that
depends on it can land.

## Why

`docs/learnings/2026-03-18-ios-platform-test-gap-learnings.md` documents
hours of Maestro debugging spent finding a one-line bug in
`IosXmlParser.skipWhitespaceAndComments()`. A unit test would have caught
it instantly. Phase 9 E2E flows are slow, hard to debug, and have many
potential failure points. We do not use Maestro to discover bugs in iOS
platform code; we use unit tests. Maestro's job is to verify the wiring,
not debug the implementations.

## What counts as "platform code"

Anything implementing an `expect`/`actual` declaration on the iOS side.
Examples:
- `PlatformKeychainStore` (iosMain implementation)
- `PlatformBiometricAuth` (iosMain implementation)
- `PlatformBarcodeScanner` (iosMain implementation)
- `PlatformHttpClient` (iosMain implementation)
- Any new `Platform*` class added during a wave

NOT platform code (so this rule does not apply):
- Compose UI in `commonMain/`
- ViewModels in `commonMain/`
- Network clients in `commonMain/` (already pure Kotlin)
- Maestro flows themselves

## Enforcement

This is a code-review rule, not a CI rule. The reviewer of any wave PR
checks the diff for `iosMain/` changes and rejects the PR if there is no
matching `iosTest/` test. Future Phase 9 waves may add a custom Detekt
or ktlint check, but for now the discipline is human.

## Exceptions

If a wave needs to touch `iosMain/` code that is genuinely untestable in
isolation (e.g., requires real hardware or a real iCloud session), the
PR description must explain why and the reviewer must explicitly accept
the exception. Do not silently skip the rule.
