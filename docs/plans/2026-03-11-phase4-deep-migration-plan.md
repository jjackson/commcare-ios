# Phase 4: Deep Migration — Implementation Plan

**Date:** 2026-03-11
**Status:** Draft
**Prerequisite:** Phase 3 complete (184 files in commonMain, 470 .kt + 13 .java in main/java).

---

## Goal

Remove JVM dependencies from the 67 Kotlin files and 13 Java files that are blocking ~390 transitively-dependent files from moving to commonMain. This unlocks moving the core engine (EvaluationContext, TreeReference, TreeElement, XPath, case management, session) to commonMain.

**Exit criteria:** 400+ files in commonMain. Core engine APIs (XPath evaluation, case management) accessible from iOS. All 710+ JVM tests pass.

---

## Current State

| Source Set | Files |
|-----------|-------|
| commonMain | 184 .kt |
| jvmMain | 21 .kt |
| iosMain | 21 .kt |
| src/main/java | 470 .kt, 13 .java |

### Blocker Analysis

67 Kotlin files have direct JVM imports. 13 Java files need conversion. The remaining 390 Kotlin files have zero JVM imports but are transitively blocked.

| Blocker Category | Files | Strategy |
|-----------------|-------|----------|
| xmlpull/kxml2 (9) | XFormParser, XFormSerializer, etc. | Migrate to PlatformXmlParser |
| org.json (8) | Graph config, JsonUtils, XPathJsonPropertyFunc | Create PlatformJson abstraction |
| java.io (12) | ElementParser, ResourceTable, FileUtils, etc. | Extend PlatformFile/PlatformIO |
| okhttp3/retrofit2 (7) | CommCareNetworkService, ModernHttpRequester, etc. | Move to jvmMain (JVM-only) |
| java.util.Date/Calendar (7) | DateUtils, CalendarUtils, StringUtils, etc. | Complete PlatformDate migration |
| java.net (6) | PostRequest, RemoteQueryDatum, etc. | Extend PlatformUrl |
| io.reactivex (4) | Text, Entry, Menu, MenuDisplayable | Abstract to interface |
| javax.crypto (3) | CryptUtil, EncryptionUtils, FileBitCache | Complete PlatformCrypto migration |
| datadog/opentracing (3) | FormDef, FormEntryController, FormEntryPrompt | Abstract to interface |
| java.util.Locale/concurrent (6) | EntitySortUtil, ColorUtils, etc. | Targeted replacements |
| Class<*>/.javaClass (16) | ExtUtil, ExtWrap*, Hasher, etc. | KClass conversion |
| Java files (13) | TransactionParser, Reference, etc. | Convert to Kotlin |

---

## Wave Plan

### Wave 1: Quick wins — java.lang, java.util.Locale, synchronized, Objects
**Files:** ~10
**What:** Replace `java.lang.Math` → `kotlin.math`, `java.util.Locale` → expect/actual, `synchronized` → expect/actual or remove, `Objects.equals` → `==`
**Risk:** Low

### Wave 2: Convert remaining Java files to Kotlin
**Files:** 13 Java → Kotlin
**What:** TransactionParser, SimpleNode, TreeBuilder, DataModelPullParser, VirtualInstances, Reference, ReferenceHandler, ReferenceManager, ResourceReference, ReleasedOnTimeSupportedReference, ClassNameHasher, IDataPointer, TreeElementParser
**Risk:** Medium — must maintain Java interop for test files that import these

### Wave 3: Complete Date/Calendar migration
**Files:** ~7
**What:** Replace remaining `java.util.Date`, `java.util.Calendar`, `java.util.TimeZone`, `java.text.SimpleDateFormat` with PlatformDate abstractions
**Risk:** Medium — date formatting is tricky cross-platform

### Wave 4: Migrate xmlpull/kxml2 consumers
**Files:** 9
**What:** Replace remaining `org.kxml2`/`org.xmlpull` imports with PlatformXmlParser
**Risk:** Medium — XFormParser is large and complex

### Wave 5: Abstract org.json to PlatformJson
**Files:** 8
**What:** Create expect/actual PlatformJson abstraction, migrate graph config and JsonUtils
**Risk:** Medium — need iOS JSON implementation

### Wave 6: Abstract io.reactivex and tracing
**Files:** 7
**What:** Replace `io.reactivex.Single` with callback interface or expect/actual. Replace `datadog`/`opentracing` with no-op interface.
**Risk:** Medium — reactive patterns are architectural

### Wave 7: Abstract java.io and java.net
**Files:** ~18
**What:** Replace remaining java.io.File, InputStream, OutputStream, java.net.URL with platform abstractions
**Risk:** Medium — many file I/O patterns

### Wave 8: KClass conversion for serialization
**Files:** ~16
**What:** Replace `Class<*>`, `.javaClass`, `Class.forName()` in ExtUtil, ExtWrap*, Hasher, LivePrototypeFactory
**Risk:** High — serialization is deeply embedded

### Wave 9: Move HTTP/network files to jvmMain
**Files:** 7
**What:** Move okhttp3/retrofit2 consumers to jvmMain. Create commonMain interfaces if needed.
**Risk:** Low — these are inherently JVM-specific

### Wave 10: Bulk migration attempt
**Files:** 390+
**What:** Move all remaining pure files to commonMain. Iterate compiler-validation until stable.
**Risk:** Medium — may discover additional hidden dependencies

---

## Dependency Graph

```
Wave 1 (quick wins) ─────────────────────────────────────┐
Wave 2 (Java→Kotlin) ────────────────────────────────────│
Wave 3 (Date) ────────────────────────────────────────────├─→ Wave 10 (bulk move)
Wave 4 (xmlpull) ─────────────────────────────────────────│
Wave 5 (json) ────────────────────────────────────────────│
Wave 6 (rxjava+tracing) ─────────────────────────────────│
Wave 7 (java.io+net) ────────────────────────────────────│
Wave 8 (KClass) ──────────────────────────────────────────│
Wave 9 (jvmMain move) ───────────────────────────────────┘
```

Waves 1-9 can largely run in parallel. Wave 10 depends on all of them.

---

## Acceptance Criteria

- [ ] 400+ files in commonMain
- [ ] Zero JVM-specific imports in commonMain files
- [ ] All 710+ JVM tests pass
- [ ] `compileCommonMainKotlinMetadata` succeeds
- [ ] Core engine types (EvaluationContext, TreeReference, TreeElement, XPathExpression) are in commonMain
