# iOS Platform Code Requires Unit Tests Before Integration

**Date:** 2026-03-18
**Context:** App install failed silently on iOS; took hours of Maestro E2E debugging to find a one-line fix in the iOS XML parser.

## What Happened

When implementing iOS HTTP reference resolution (`IosHttpRoot`/`IosHttpReference`) and wiring it into the app installer for HQ round-trip testing, the full app install flow failed with:

> "No external or local definition could be found for resource Application Descriptor with id commcare-application-profile"

The error was opaque. `println` debugging in Kotlin/Native doesn't show in simulator log stream. Maestro screenshots showed "Installation Failed" but gave no root cause. Hours were spent building, installing, running Maestro flows, and reading the engine resource table code.

## Root Cause

The iOS XML parser (`IosXmlParser`) had an empty `skipWhitespaceAndComments()` method. When parsing profile XML downloaded from HQ, whitespace between the `<?xml?>` declaration and the `<profile>` root element was treated as a TEXT event. The `ProfileParser.checkNode("profile")` then saw TEXT instead of START_TAG and threw `InvalidStructureException`, which was silently caught by `ProfileInstaller.install()` and returned `false`.

**The fix was one line**: implement `skipWhitespaceAndComments()` to skip whitespace at document level (depth 0).

## The Testing Gap

The new iOS platform code had **zero tests**:

| Component | Lines of Code | Tests When Written | Tests That Should Have Existed |
|-----------|--------------|-------------------|-------------------------------|
| `IosHttpRoot` | 17 | 0 | Factory registration, URL derivation, integration with ReferenceManager |
| `IosHttpReference` | 109 | 0 | Property tests (isReadOnly, getURI), stream reading, error handling |
| `PlatformXmlParserIos` changes | 15 | 0 | Profile XML with whitespace, XML declaration handling |
| `createHttpReferenceFactory()` | 1 | 0 | expect/actual wiring |

The `commcare-core/src/iosTest/` directory was **completely empty** despite 47 iOS source files in `iosMain/`.

## When Tests Should Have Been Written

**Rule: Every new iOS platform implementation (actual fun/class) gets a unit test in iosTest/ before integration.**

Specifically:

1. **When `IosHttpRoot`/`IosHttpReference` were created** — should have had tests verifying:
   - Factory registers for http:// and https://
   - `DeriveReference` produces `IosHttpReference`
   - Relative reference resolution works (./suite.xml relative to profile URL)
   - Read-only properties are correct

2. **When `PlatformXmlParserIos` was originally written** — the existing `XmlParserTest` in commonTest should have included a test with whitespace between XML declaration and root element. This would have caught the bug immediately on both platforms.

3. **When `ProfileParser` was ported** — a commonTest `ProfileParserTest` should have verified that a minimal profile XML can be parsed on both JVM and iOS. This is the test that actually caught the bug.

## Tests Added

| File | Tests | Platform |
|------|-------|----------|
| `commcare-core/src/commonTest/.../ReferenceManagerTest.kt` | 5 | JVM + iOS |
| `commcare-core/src/commonTest/.../ProfileParserTest.kt` | 4 | JVM + iOS |
| `commcare-core/src/iosTest/.../IosHttpRootTest.kt` | 10 | iOS only |
| `commcare-core/src/commonTest/.../TestStorageUtilities.kt` | (shared test infra) | JVM + iOS |

## Lessons

1. **Platform code without tests is a landmine.** iOS-specific code can't be tested by running JVM tests. If `iosTest/` is empty, that's a red flag.

2. **Cross-platform tests (commonTest) catch platform divergence.** The `ProfileParserTest` passed on JVM but failed on iOS — exactly the kind of bug that commonTest exists to catch.

3. **Silent failure paths are debugging nightmares.** `ProfileInstaller.install()` catches `InvalidStructureException` and returns `false` without propagating. Combined with no `println` visibility on iOS, the root cause was invisible.

4. **Write the lower-level test before the E2E test.** The Maestro flow (`login-with-app.yaml`) was the wrong place to discover an XML parser bug. A unit test would have found it in seconds, not hours.

5. **K/N test runner limitations:** `println` output is not visible in simulator logs. NSURLSession in the test runner fails TLS validation (system trust store not available). Design tests accordingly — use assertions, not log inspection.
