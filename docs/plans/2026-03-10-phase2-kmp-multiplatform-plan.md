# Phase 2: KMP Multiplatform & App Shell — Implementation Plan

**Date:** 2026-03-10
**Status:** Draft
**Prerequisite:** Phase 1 complete (PR #31 merged). 611 .kt + 32 .java files, 710 tests passing, KMP targets configured.

---

## Goal

Make commcare-core compile and run on iOS via Kotlin/Native, then build a minimal iOS app shell that calls the engine. JVM backward compatibility must be maintained throughout.

**Exit criteria:** App launches on iOS simulator, loads KMP framework, can call core engine APIs. All 710 JVM tests still pass.

---

## Current State

All code lives in `src/main/java/` (jvmMain). The KMP build is configured with empty `commonMain` and `iosMain` source sets. iOS targets skip on Linux via `kotlin.native.ignoreDisabledTargets=true`.

### Dependency Analysis

| Category | Files | Difficulty | Strategy |
|----------|-------|------------|----------|
| Pure Kotlin (no java.*/javax.*) | 270 | Easy | Move to commonMain |
| java.io serialization (DataInput/OutputStream) | 215 | Hard | expect/actual wrapper |
| kxml2 + xmlpull (XML parsing) | 60 | Critical | expect/actual with Foundation.XMLParser on iOS |
| Guava collections | 14 | Medium | Replace with stdlib |
| org.json | 8 | Medium | Replace with kotlinx-serialization or expect/actual |
| okhttp3 + retrofit2 | 7 | Medium | expect/actual, already isolated in core.network |
| java.net (URL parsing) | 7 | Low | expect/actual URL wrapper |
| java.security + javax.crypto | 5 | Medium | expect/actual with CommonCrypto on iOS |
| java.nio (file metadata) | 4 | Low | expect/actual FileUtils |
| java.util.zip | 2 | Low | expect/actual archive handler |
| joda-time | 1 | Low | Replace with kotlinx-datetime |

### Key Constraint

The 215 files importing `java.io` serialization (`DataInputStream`/`DataOutputStream`) are the biggest blocker. CommCare's `Externalizable` persistence format uses these everywhere. We must create a cross-platform serialization abstraction before most code can move to commonMain.

---

## Architecture

### Source Set Strategy

```
src/
├── commonMain/kotlin/    ← Pure Kotlin code (no platform deps)
├── jvmMain/java/         ← Existing Java files (32 remaining)
├── jvmMain/kotlin/       ← JVM expect/actual implementations
├── iosMain/kotlin/       ← iOS expect/actual implementations
├── commonTest/kotlin/    ← Cross-platform tests (future)
├── test/java/            ← Existing JVM tests (710 tests, stay here)
├── cli/java/             ← CLI source set (Java, unchanged)
├── ccapi/                ← CCAPI source set (unchanged)
└── translate/java/       ← Translate source set (Java, unchanged)
```

### expect/actual Pattern

```kotlin
// commonMain
expect class PlatformInputStream(source: Any)
expect fun PlatformInputStream.readInt(): Int
expect fun PlatformInputStream.readUTF(): String

// jvmMain
actual class PlatformInputStream actual constructor(source: Any) {
    val stream = source as java.io.DataInputStream
}
actual fun PlatformInputStream.readInt(): Int = stream.readInt()

// iosMain
actual class PlatformInputStream actual constructor(source: Any) {
    val data = source as NSData
    var offset = 0
}
actual fun PlatformInputStream.readInt(): Int { /* read 4 bytes big-endian */ }
```

---

## Task Breakdown

### Wave 1: Replace JVM-only library dependencies (14 files)

**Goal:** Remove Guava and joda-time so those files can later move to commonMain.

**What to do:**
- Replace `com.google.common.collect.Multimap` → `Map<K, MutableList<V>>` with extension helpers
- Replace `com.google.common.collect.ImmutableList` → `List<T>` (Kotlin lists are already read-only by default)
- Replace `com.google.common.collect.ImmutableMap` → `Map<K, V>`
- Replace `com.google.common.collect.ArrayListMultimap` → `mutableMapOf<K, MutableList<V>>()`
- Replace `org.joda.time` → `kotlinx-datetime` (1 file: `XFormUtils.kt`)

**Files to modify (~15):**
- `org/commcare/session/` (5 files using Guava)
- `org/commcare/suite/model/` (4 files using Guava)
- `org/commcare/core/network/` (1 file using Guava — `ModernHttpRequester.kt` uses Multimap)
- `org/javarosa/core/model/actions/` (1 file using Guava)
- `org/javarosa/core/util/externalizable/` (2 files using Guava)
- `org/javarosa/xform/util/XFormUtils.kt` (joda-time)

**Dependencies added to build.gradle.kts:**
```kotlin
commonMain.dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
}
```

**Tests that must pass:** All 710 existing tests.

**Acceptance criteria:**
- [ ] No `com.google.common` imports remain in any .kt file
- [ ] No `org.joda.time` imports remain in any .kt file
- [ ] `./gradlew compileKotlin compileJava` passes
- [ ] `./gradlew test` passes (710 tests, 0 failures)
- [ ] Guava and joda-time removed from build.gradle.kts dependencies

---

### Wave 2: Serialization abstraction — expect/actual for I/O streams (core layer)

**Goal:** Create a cross-platform serialization layer that wraps `DataInputStream`/`DataOutputStream`, enabling the 215 files that use them to eventually move to commonMain.

**What to do:**
1. Create `commonMain` expect declarations for stream I/O:
   - `expect class PlatformDataInputStream`
   - `expect class PlatformDataOutputStream`
   - Core operations: `readInt`, `writeInt`, `readUTF`, `writeUTF`, `readByte`, `writeByte`, `readLong`, `writeLong`, `readDouble`, `writeDouble`, `readFully`, `write`, `close`
2. Create `jvmMain` actual implementations delegating to `java.io.DataInputStream`/`DataOutputStream`
3. Create `iosMain` actual implementations using `NSData`/`NSMutableData` with manual byte-level encoding (big-endian, modified UTF-8 to match Java's format)
4. Create `commonMain` expect/actual for `ByteArrayInputStream`/`ByteArrayOutputStream`
5. **Do NOT move any existing files yet** — just create the abstraction layer and verify it compiles

**New files (~8-10):**
- `commonMain/kotlin/org/javarosa/core/io/PlatformStreams.kt` (expect declarations)
- `jvmMain/kotlin/org/javarosa/core/io/PlatformStreamsJvm.kt` (actual implementations)
- `iosMain/kotlin/org/javarosa/core/io/PlatformStreamsIos.kt` (actual implementations)
- `commonMain/kotlin/org/javarosa/core/io/PlatformByteArrayStreams.kt`
- `jvmMain/kotlin/org/javarosa/core/io/PlatformByteArrayStreamsJvm.kt`
- `iosMain/kotlin/org/javarosa/core/io/PlatformByteArrayStreamsIos.kt`

**Tests that must pass:** All 710 existing tests (no existing code changes, just new files).

**Acceptance criteria:**
- [ ] expect declarations compile in commonMain
- [ ] JVM actuals compile and delegate to java.io correctly
- [ ] iOS actuals compile (Kotlin/Native, tested on macOS runner)
- [ ] `./gradlew compileKotlin compileJava` passes
- [ ] `./gradlew test` passes (710 tests, 0 failures)

---

### Wave 3: XML parsing abstraction — expect/actual for kxml2/xmlpull (60 files)

**Goal:** Create a cross-platform XML parsing interface so that the 60 files using kxml2/xmlpull can move to commonMain.

**What to do:**
1. Create `commonMain` expect interface mirroring `XmlPullParser` and `XmlSerializer`:
   - `expect interface PlatformXmlParser` with methods: `next()`, `getEventType()`, `getName()`, `getAttributeValue()`, `getText()`, `getNamespace()`, `getDepth()`, `isWhitespace()`, etc.
   - `expect interface PlatformXmlSerializer` with methods: `startDocument()`, `startTag()`, `attribute()`, `text()`, `endTag()`, `endDocument()`, etc.
   - Event type constants: `START_DOCUMENT`, `END_DOCUMENT`, `START_TAG`, `END_TAG`, `TEXT`
2. Create `jvmMain` actual implementations wrapping kxml2's `KXmlParser`/`KXmlSerializer`
3. Create `iosMain` actual implementations wrapping Foundation's `XMLParser` (SAX-style, will need adapter to pull-parser interface)
4. **Do NOT move existing files yet** — just the abstraction layer

**New files (~8-10):**
- `commonMain/kotlin/org/javarosa/xml/PlatformXml.kt` (expect interfaces + constants)
- `jvmMain/kotlin/org/javarosa/xml/PlatformXmlJvm.kt` (kxml2 wrapper)
- `iosMain/kotlin/org/javarosa/xml/PlatformXmlIos.kt` (Foundation.XMLParser wrapper)
- Factory/builder files for creating parsers from streams

**Key challenge:** Foundation's `XMLParser` is SAX (push) style while kxml2 is pull-style. The iOS implementation needs a coroutine-based or buffer-based adapter to present a pull interface.

**Tests that must pass:** All 710 existing tests.

**Acceptance criteria:**
- [ ] expect interfaces compile in commonMain
- [ ] JVM actuals wrap kxml2 and pass basic XML parsing tests
- [ ] iOS actuals wrap Foundation.XMLParser and pass basic XML parsing tests
- [ ] New cross-platform XML parsing unit tests pass on both JVM and iOS
- [ ] `./gradlew test` passes (710 tests, 0 failures)

---

### Wave 4: Platform abstractions — crypto, networking, file I/O (19 files)

**Goal:** Create expect/actual for remaining platform-specific APIs: encryption, HTTP, file system, URL parsing.

**What to do:**

**Crypto (5 files):**
- `expect object PlatformCrypto` with `encrypt`, `decrypt`, `generateKey`, `hash` (SHA-256, MD5)
- JVM actual: `javax.crypto.Cipher`, `MessageDigest`
- iOS actual: CommonCrypto (`CCCrypt`, `CC_SHA256`, `CC_MD5`)

**HTTP (7 files):**
- `expect interface PlatformHttpClient` with `execute(request): Response`
- JVM actual: OkHttp (existing code, wrapped)
- iOS actual: `URLSession` (Foundation)
- Note: All 7 HTTP files are in `org.commcare.core.network` — well-isolated

**File I/O (4 files):**
- `expect object PlatformFiles` with `readBytes`, `writeBytes`, `exists`, `delete`, `listDir`, `fileSize`
- JVM actual: `java.io.File`, `java.nio.file.Files`
- iOS actual: `NSFileManager`

**URL parsing (7 files):**
- `expect class PlatformUrl` with `scheme`, `host`, `path`, `query`
- JVM actual: `java.net.URL`
- iOS actual: `NSURL` (Foundation)

**org.json replacement (8 files):**
- Add `kotlinx-serialization-json` dependency to commonMain
- Replace `org.json.JSONObject`/`JSONArray` with `kotlinx.serialization.json.JsonObject`/`JsonArray`
- Mostly in `org.commcare.core.graph` (6 files) + `JsonUtils` + `XPathJsonPropertyFunc`

**New files (~15-20):**
- expect/actual pairs for each abstraction (crypto, http, file, url)
- Modified existing files to use new abstractions where straightforward

**Dependencies added:**
```kotlin
commonMain.dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0")
}
```

**Tests that must pass:** All 710 existing tests.

**Acceptance criteria:**
- [ ] No `javax.crypto` imports in files that will move to commonMain
- [ ] No `org.json` imports remain
- [ ] No `java.net.URL` imports in files that will move to commonMain
- [ ] expect/actual compiles on both JVM and iOS
- [ ] `./gradlew test` passes (710 tests, 0 failures)

---

### Wave 5: Move pure Kotlin files to commonMain (270 files)

**Goal:** Move the 270 files with no JVM-specific imports from `src/main/java/` to `src/commonMain/kotlin/`.

**What to do:**
1. Identify all .kt files with zero `java.*`, `javax.*`, `okhttp3.*`, `retrofit2.*`, `org.kxml2.*`, `org.xmlpull.*`, `com.google.common.*`, `org.joda.*`, `org.json.*` imports
2. Move them to `src/commonMain/kotlin/` preserving package structure
3. Fix any compilation issues (files in commonMain can't reference jvmMain files)
4. Some files may need to stay in jvmMain if they're referenced by Java files that can't move

**Estimated scope:** ~270 files across ~30 packages. Likely needs sub-waves:
- 5a: Model/data classes (smallest dependency surface)
- 5b: Interfaces and abstract classes
- 5c: Utility and service classes
- 5d: Remaining pure-Kotlin files

**Tests that must pass:** All 710 existing tests.

**Acceptance criteria:**
- [ ] 250+ files moved to commonMain (some may need to stay in jvmMain due to Java interop)
- [ ] `./gradlew compileKotlin compileJava` passes
- [ ] `./gradlew test` passes (710 tests, 0 failures)
- [ ] All 5 JAR outputs still build correctly

---

### Wave 6: Migrate serialization consumers to platform abstraction (215 files)

**Goal:** Update the 215 files using `java.io.DataInputStream`/`DataOutputStream` to use the platform abstraction from Wave 2, then move them to commonMain.

**What to do:**
1. Replace `java.io.DataInputStream` → `PlatformDataInputStream` across all files
2. Replace `java.io.DataOutputStream` → `PlatformDataOutputStream`
3. Replace `java.io.ByteArrayInputStream` → platform equivalent
4. Replace `java.io.IOException` → keep as-is (kotlinx-io provides this, or use expect/actual)
5. Move updated files to commonMain

**Key challenge:** `IOException` is used in 208 files. Options:
- a) Create `expect class PlatformIOException` (verbose, touches every file)
- b) Use `kotlinx-io` library which provides multiplatform `IOException`
- c) Keep `IOException` as a typealias in commonMain pointing to platform-specific exceptions

**This is the largest wave.** Will need sub-waves by package:
- 6a: `org.javarosa.core.util.externalizable` (serialization framework itself)
- 6b: `org.javarosa.core.model` (model classes using Externalizable)
- 6c: `org.javarosa.xpath` (XPath AST nodes using Externalizable)
- 6d: Remaining packages

**Tests that must pass:** All 710 existing tests.

**Acceptance criteria:**
- [ ] No direct `java.io.DataInputStream`/`DataOutputStream` imports in commonMain
- [ ] All migrated files compile in commonMain
- [ ] `./gradlew test` passes (710 tests, 0 failures)

---

### Wave 7: Migrate XML consumers to platform abstraction (60 files)

**Goal:** Update the 60 files using kxml2/xmlpull to use the platform abstraction from Wave 3, then move them to commonMain.

**What to do:**
1. Replace `org.kxml2.io.KXmlParser` → `PlatformXmlParser` factory
2. Replace `org.xmlpull.v1.XmlPullParser` → `PlatformXmlParser` interface
3. Replace `org.kxml2.io.KXmlSerializer` → `PlatformXmlSerializer` factory
4. Move updated files to commonMain

**Sub-waves:**
- 7a: `org.javarosa.xform.parse` (8 files — XForm parser)
- 7b: `org.commcare.xml` (35 files — app XML parsing)
- 7c: Remaining XML consumers

**Tests that must pass:** All 710 existing tests.

**Acceptance criteria:**
- [ ] No `org.kxml2` or `org.xmlpull` imports in commonMain
- [ ] XML parsing tests pass on JVM
- [ ] `./gradlew test` passes (710 tests, 0 failures)

---

### Wave 8: iOS app shell — Compose Multiplatform project

**Goal:** Create a minimal Compose Multiplatform iOS app that imports the CommCareCore framework and calls engine APIs.

**What to do:**
1. Create top-level `app/` directory with Compose Multiplatform project structure
2. Configure Gradle for iOS target (Xcode project generation)
3. Create minimal UI: single screen that initializes the engine and displays version info
4. Wire KMP engine dependency (`commcare-core` commonMain)
5. Verify app launches on iOS simulator

**New directory structure:**
```
app/
├── build.gradle.kts
├── src/
│   ├── commonMain/kotlin/    ← Shared app code (Compose UI)
│   ├── iosMain/kotlin/       ← iOS entry point
│   └── androidMain/kotlin/   ← Android entry point (future)
├── iosApp/                   ← Xcode project wrapper
│   ├── iosApp.xcodeproj
│   └── iosApp/
│       ├── AppDelegate.swift
│       └── Info.plist
└── gradle.properties
```

**Dependencies:**
```kotlin
commonMain.dependencies {
    implementation(project(":commcare-core"))
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
}
```

**Acceptance criteria:**
- [ ] `./gradlew :app:iosSimulatorArm64Test` passes (or equivalent)
- [ ] App launches on iOS simulator (screenshot evidence)
- [ ] App successfully calls a commcare-core API (e.g., XPath evaluation)
- [ ] JVM tests still pass: `cd commcare-core && ./gradlew test` (710 tests)

---

### Wave 9: End-to-end integration validation

**Goal:** Verify the full stack works: iOS app → Compose UI → KMP engine → XPath/XForm processing.

**What to do:**
1. Add integration test: load a real XForm, parse it, evaluate XPath expressions
2. Add integration test: create a case, serialize it, deserialize it
3. Verify on iOS simulator
4. Generate Phase 2 completion report

**Acceptance criteria:**
- [ ] XForm parsing works end-to-end on iOS
- [ ] Case serialization round-trips correctly on iOS
- [ ] All 710 JVM tests still pass
- [ ] Phase 2 completion report generated

---

## Ordering & Dependencies

```
Wave 1 (Guava/joda-time removal)
  ↓
Wave 2 (serialization abstraction) ──→ Wave 6 (migrate serialization consumers)
  ↓                                      ↓
Wave 3 (XML abstraction) ──────────→ Wave 7 (migrate XML consumers)
  ↓                                      ↓
Wave 4 (crypto/net/file abstractions)    │
  ↓                                      │
Wave 5 (move pure Kotlin to commonMain) ←┘
  ↓
Wave 8 (iOS app shell) — needs macOS runner
  ↓
Wave 9 (E2E validation)
```

Waves 1-4 can partially overlap (they create abstractions, don't move files).
Waves 5-7 move files to commonMain (must follow their respective abstraction waves).
Wave 8 needs a macOS environment for iOS simulator testing.

---

## CI Requirements

- **Linux runners:** Waves 1-7 (Kotlin compilation, JVM tests). Same as Phase 1.
- **macOS runner:** Waves 3+ (iOS Kotlin/Native compilation verification), Wave 8-9 (iOS simulator).
- Update `.github/workflows/ios-build.yml` to build the app module.

---

## Risk Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| SAX→pull parser adapter complexity (iOS XML) | Blocks Wave 3 | Prototype early. Consider kotlinx-io XML or ktor-xml as alternatives to hand-rolled adapter |
| Serialization binary format mismatch (iOS) | Data corruption | Test with existing serialized fixtures. Java's modified UTF-8 and big-endian ints must match exactly |
| IOException multiplatform story unclear | Touches 208 files | Decide in Wave 2: kotlinx-io, typealias, or expect/actual. Commit to one approach |
| 215-file serialization migration (Wave 6) too large | Stalls progress | Split into 4 sub-waves by package. Each sub-wave independently testable |
| Compose Multiplatform iOS stability | App crashes | Pin to stable CMP release. Test on multiple iOS versions. Have UIKit fallback plan |
| macOS CI runner cost | Slow/expensive CI | Run iOS compilation checks only on PRs touching iosMain or app/ |

---

## Estimated Effort

| Wave | Description | Files | Estimated Size |
|------|-------------|-------|---------------|
| 1 | Replace Guava/joda-time | ~15 modified | Small |
| 2 | Serialization abstraction | ~10 new | Medium |
| 3 | XML parsing abstraction | ~10 new | Large (SAX→pull complexity) |
| 4 | Crypto/net/file abstractions | ~20 new, ~19 modified | Medium |
| 5 | Move pure Kotlin to commonMain | ~270 moved | Large (volume) |
| 6 | Migrate serialization consumers | ~215 modified + moved | Very Large |
| 7 | Migrate XML consumers | ~60 modified + moved | Large |
| 8 | iOS app shell | ~15 new | Medium |
| 9 | E2E validation | ~5 new | Small |

---

## Issue Template

Each wave gets a GitHub issue following Phase 1's format:

```markdown
## Context
<what this wave does and why>

## Files to Read
<relevant existing files for context>

## What to Do
<step-by-step instructions>

## Tests That Must Pass
- [ ] `./gradlew compileKotlin compileJava` passes
- [ ] `./gradlew test` passes (710 tests, 0 failures)
- [ ] <wave-specific criteria>
```
