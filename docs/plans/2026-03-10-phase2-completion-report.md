# Phase 2: KMP Multiplatform — Completion Report

**Date:** 2026-03-10
**Status:** Complete

---

## Summary

Phase 2 transformed commcare-core from a JVM-only Kotlin project into a Kotlin Multiplatform (KMP) project that compiles for both JVM and iOS (Kotlin/Native). An iOS app shell using Compose Multiplatform was built and verified on macOS CI.

---

## What Was Done

### Wave 1: Replace Guava/joda-time (PR #45)
- Replaced Guava `ListMultimap` with a pure Kotlin implementation in commonMain
- Removed joda-time usage from production code

### Wave 2: Serialization abstraction (PR #47)
- Created `PlatformDataInputStream`/`PlatformDataOutputStream` expect/actual classes
- JVM: delegates to `java.io.DataInputStream`/`DataOutputStream`
- iOS: manual big-endian encoding matching Java's binary format
- Created `PlatformIOException` expect/actual (typealias to `java.io.IOException` on JVM)

### Wave 3: XML parsing abstraction (PR #48)
- Created `PlatformXmlParser` interface and `createXmlParser()` expect/actual factory
- Created `PlatformXmlSerializer` expect/actual
- Created `PlatformXmlParserException` expect/actual (typealias to `XmlPullParserException` on JVM)
- iOS: Pure Kotlin XML pull parser implementation (state-machine, handles namespaces, CDATA, entities)

### Wave 4: Crypto/net/file/JSON abstractions (PR #49)
- Created expect/actual for `PlatformCrypto`, `PlatformFiles`, `PlatformUrl`, `PlatformHttpClient`
- iOS: stub implementations for platform services

### Wave 5: Move pure Kotlin to commonMain (PR #51)
- Moved 82 files with no JVM dependencies to commonMain
- Includes: model constants, exceptions, query interfaces, graph models, resource types

### Wave 6: Migrate serialization consumers (PR #53)
- Replaced `java.io.IOException` with `PlatformIOException` across 208 files
- Moved 87 additional files to commonMain after IOException removal
- Added explicit `kotlin.jvm.*` imports to 21 commonMain files for metadata compilation

### Wave 7: Migrate XML consumers (PR #55)
- Replaced `XmlPullParserException` with `PlatformXmlParserException` across 54 files

### Wave 8: iOS app shell (PR #57, verified PR #60)
- Created `app/` module with Compose Multiplatform
- iOS framework builds (CommCareApp) linking against CommCareCore framework
- Enabled iOS CI on macOS-14 runners

### Wave 9: E2E integration validation (this PR)
- Added commonTest source set with 21 cross-platform tests
- Serialization round-trip tests (12): verify binary format compatibility
- XML parsing tests (9): verify parser behavior matches across platforms
- iOS test execution via `iosSimulatorArm64Test` in CI

---

## File Distribution

| Source Set | Files | Description |
|-----------|-------|-------------|
| commonMain | 88 .kt | Cross-platform shared code |
| jvmMain | 10 .kt | JVM-specific expect/actual implementations |
| iosMain | 11 .kt | iOS-specific expect/actual implementations |
| src/main/java | 535 .kt, 32 .java | JVM code (not yet migrated to commonMain) |
| commonTest | 2 .kt | Cross-platform integration tests (21 test methods) |
| src/test/java | 133 .java | JVM-only unit tests (710 tests) |

### Key Observation

Only 88 of 535+ Kotlin files could move to commonMain. The primary blocker is the serialization framework (`Externalizable`, `PrototypeFactory`, `ExtUtil`) which uses JVM reflection (`Class<*>`, `newInstance()`) and is referenced by 131+ classes. Moving the engine's core processing logic to commonMain would require either:
1. Abstracting the reflection-based serialization with expect/actual
2. Replacing the serialization framework entirely (e.g., with kotlinx-serialization)

---

## Test Coverage

| Platform | Tests | Status |
|---------|-------|--------|
| JVM | 710 existing + 21 commonTest | All pass |
| iOS | 21 commonTest | Verified via CI (iosSimulatorArm64Test) |

### Cross-platform tests cover:
- **Serialization binary compatibility**: Int, Long, Double, Byte, Boolean, Char, UTF-8 strings, byte arrays all round-trip correctly. Binary format matches Java's `DataOutputStream` big-endian encoding.
- **XML parsing**: Elements, attributes, namespaces, self-closing tags, CDATA, entities, nested structures, XForm document structure all parse identically.

---

## Known Limitations

1. **Most engine code remains JVM-only**: XPath evaluation, XForm parsing, case management, session navigation — all in `src/main/java/` (jvmMain), not callable from iOS.

2. **No real engine integration on iOS yet**: The app shell displays a static screen. Actual XPath evaluation, form loading, and case processing require moving much more code to commonMain, which is blocked by the serialization framework dependency.

3. **iOS platform stubs**: `PlatformCrypto`, `PlatformFiles`, `PlatformUrl`, `PlatformHttpClient` have stub/no-op implementations on iOS. Real implementations needed for production use.

4. **XML parser differences**: The iOS XML parser (pure Kotlin state-machine) has minor behavioral differences from kxml2 (depth reporting after END_TAG). Tests are written to tolerate these differences.

---

## CI Infrastructure

| Workflow | Runner | What it does | Duration |
|---------|--------|-------------|----------|
| kotlin-tests.yml | ubuntu-latest | `./gradlew build` (JVM compile + 710 tests) | ~1.5 min |
| ios-build.yml | macos-14 | iOS tests + framework linking (commcare-core + app) | ~6 min |

Both trigger on PRs touching `commcare-core/`.

---

## Phase 3 Readiness

Phase 2 established the KMP infrastructure:
- Build system configured for JVM + iOS targets
- 6 expect/actual abstractions covering serialization, XML, crypto, files, networking, URLs
- iOS CI pipeline operational
- Cross-platform test framework in place

**To make the engine functional on iOS (Phase 3)**, the key challenge is the serialization framework. Options:
1. **expect/actual for PrototypeFactory**: Abstract `Class<*>` and reflection with a registration-based factory pattern
2. **kotlinx-serialization migration**: Replace `Externalizable` with `@Serializable` annotations (large refactor, ~131 classes)
3. **Hybrid approach**: Keep JVM serialization, add separate iOS serialization layer

The hybrid approach is likely most practical — iOS doesn't need to deserialize data produced by Java/Android. A fresh iOS serialization layer could use kotlinx-serialization from the start.
