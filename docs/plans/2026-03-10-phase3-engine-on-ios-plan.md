# Phase 3: Engine on iOS — Implementation Plan

**Date:** 2026-03-10
**Status:** Draft
**Prerequisite:** Phase 2 complete (88 files in commonMain, iOS CI operational, 21 cross-platform tests passing).

---

## Goal

Move enough of commcare-core's engine code from jvmMain to commonMain so that core processing (XPath evaluation, XForm parsing, case management, session navigation) is callable from iOS. JVM backward compatibility must be maintained throughout.

**Exit criteria:** XPath evaluation, XForm parsing, and case management APIs are accessible from iOS commonMain. All 710+ JVM tests still pass. Cross-platform integration tests verify engine functionality on iOS.

---

## Current State

| Source Set | Files | Description |
|-----------|-------|-------------|
| commonMain | 88 .kt | Cross-platform shared code (mostly simple types, exceptions, interfaces) |
| jvmMain | 10 .kt | JVM-specific expect/actual implementations |
| iosMain | 11 .kt | iOS-specific expect/actual implementations |
| src/main/java | 535 .kt, 32 .java | JVM-only engine code — the target for migration |

### Dependency Analysis

| Blocker | Files Affected | Strategy |
|---------|---------------|----------|
| `java.util.Vector` / `Hashtable` | ~181 | Replace with Kotlin `MutableList` / `MutableMap` |
| `java.io.*` (streams, File) | ~160 | Extend existing expect/actual; create PlatformFile |
| `Class<*>` / reflection | ~109 | Registration-based PrototypeFactory for iOS |
| `Externalizable` interface | ~85 | expect/actual Externalizable with platform impls |
| `org.kxml2` / `org.xmlpull` | ~55 | Migrate remaining files to PlatformXmlParser |
| `java.util.Date` | ~28 | expect/actual PlatformDate or kotlinx-datetime |
| `java.util.regex` | ~4 | Use Kotlin `Regex` (available in common) |
| No direct blockers | ~209 | Move after transitive deps resolved |

Note: Many files have multiple blockers (e.g., Vector + Externalizable + java.io). Counts overlap.

---

## Wave Plan

### Wave 1: Replace JVM collections (Vector, Hashtable, Stack, Enumeration)

**Goal:** Remove the most pervasive JVM dependency — `java.util.Vector`, `Hashtable`, `Stack`, and `Enumeration` — replacing them with Kotlin stdlib equivalents.

**What to do:**
- `Vector<T>` → `MutableList<T>` (backed by `ArrayList`)
- `Hashtable<K,V>` → `MutableMap<K,V>` (backed by `HashMap`)
- `Stack<T>` → `ArrayDeque<T>` or `MutableList<T>`
- `Enumeration<T>` → `Iterator<T>`
- Update call sites: `.elements()` → `.iterator()`, `.elementAt()` → `[]`, `.addElement()` → `.add()`, etc.

**Files:** ~181 files across the codebase
**Risk:** Vector is synchronized; ArrayList is not. CommCare's usage is predominantly single-threaded but verify no concurrent access patterns exist.

**Acceptance criteria:**
- [ ] Zero imports of `java.util.Vector`, `java.util.Hashtable`, `java.util.Stack`, `java.util.Enumeration` in `src/main/java/`
- [ ] All 710 JVM tests pass
- [ ] Compilation succeeds

### Wave 2: Migrate remaining XML consumers to PlatformXmlParser

**Goal:** Replace remaining `org.kxml2` / `org.xmlpull` direct imports with `PlatformXmlParser`.

**What to do:**
- Replace `XmlPullParser` usage with `PlatformXmlParser` in remaining ~55 files
- Replace `KXmlSerializer` usage with `PlatformXmlSerializer`
- Remove direct kxml2/xmlpull imports

**Files:** ~55 files
**Depends on:** Wave 1 (some XML consumer files also use Vector)

**Acceptance criteria:**
- [ ] Zero imports of `org.kxml2` or `org.xmlpull` in Kotlin source files
- [ ] All 710 JVM tests pass

### Wave 3: Replace java.util.Date and java.util.regex

**Goal:** Remove two smaller JVM dependencies.

**What to do:**
- Create `expect/actual` for date operations or use `kotlinx-datetime`
- Replace `java.util.regex.Pattern` / `Matcher` with Kotlin `Regex` (already in common stdlib)
- Replace `java.util.Date` with platform abstraction

**Files:** ~32 files (28 Date + 4 regex)

**Acceptance criteria:**
- [ ] Zero imports of `java.util.Date`, `java.util.regex.Pattern`, `java.util.regex.Matcher`
- [ ] All 710 JVM tests pass

### Wave 4: Abstract the serialization framework

**Goal:** Make `Externalizable`, `PrototypeFactory`, and `ExtUtil` work cross-platform.

**What to do:**
- Create `expect/actual` for `Externalizable` interface
- Create `expect/actual` for `PrototypeFactory` — JVM uses `Class.forName()` + `newInstance()`; iOS uses a registration map
- Create `expect/actual` wrappers for `ExtUtil`'s stream operations
- Move the core serialization interfaces to commonMain
- Keep JVM implementations using existing reflection; iOS implementations use registered factories

**Files:** ~10 new expect/actual files + modifications to ExtUtil, PrototypeFactory, Externalizable
**Risk:** This is the hardest wave. PrototypeFactory's reflection pattern is deeply embedded. The iOS registration approach means all serializable types must be explicitly registered at app startup.

**Acceptance criteria:**
- [ ] `Externalizable`, `PrototypeFactory`, `ExtUtil` core interfaces in commonMain
- [ ] JVM implementation passes all 710 tests unchanged
- [ ] iOS implementation compiles and can register/instantiate types

### Wave 5: Extend java.io abstractions

**Goal:** Abstract remaining `java.io` usage beyond what Phase 2 covered (DataInputStream/DataOutputStream already done).

**What to do:**
- Create expect/actual for commonly used stream patterns (ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream)
- Create expect/actual for File operations (PlatformFile for path-based operations)
- Migrate files that only need these stream abstractions

**Files:** ~160 files use java.io, but many overlap with serialization (Wave 4). After Wave 4, the remaining java.io count should be lower.

**Acceptance criteria:**
- [ ] Core java.io patterns abstracted with expect/actual
- [ ] All 710 JVM tests pass

### Wave 6: Move XPath engine to commonMain

**Goal:** Move the XPath evaluation engine (expressions, functions, parser) to commonMain.

**What to do:**
- Move XPath expression classes (~40 files) to commonMain
- Move XPath functions (~30 files)
- Move XPath parser
- Many XPath files have no JVM dependencies after Waves 1-5; the ones that do need targeted fixes

**Files:** ~80 files in `org.javarosa.xpath`
**Depends on:** Waves 1-5 (collection replacement, serialization abstraction)

**Acceptance criteria:**
- [ ] XPath expression evaluation works on iOS
- [ ] Cross-platform test: parse and evaluate XPath expressions
- [ ] All 710 JVM tests pass

### Wave 7: Move XForm parser and case management to commonMain

**Goal:** Move form definition and case management code to commonMain.

**What to do:**
- Move `org.javarosa.core.model` (form model) to commonMain
- Move `org.javarosa.xform` (XForm parser) to commonMain
- Move `org.commcare.cases` (case management) to commonMain
- Move `org.commcare.session` (session navigation) to commonMain

**Files:** ~150+ files
**Depends on:** Waves 1-6

**Acceptance criteria:**
- [ ] XForm loading and parsing works on iOS
- [ ] Case creation and querying works on iOS
- [ ] Cross-platform integration tests verify end-to-end
- [ ] All 710 JVM tests pass

### Wave 8: Real iOS platform implementations

**Goal:** Replace stub implementations with functional iOS platform services.

**What to do:**
- `PlatformCrypto`: Implement using CommonCrypto (MD5, SHA-256, AES)
- `PlatformFiles`: Implement using Foundation file system APIs
- `PlatformUrl`: Implement URL handling with Foundation NSURL
- `PlatformHttpClient`: Implement using NSURLSession
- `PlatformDate`: Real date implementation using Foundation NSDate

**Files:** ~5-10 iOS actual implementations

**Acceptance criteria:**
- [ ] Crypto operations work on iOS (MD5, SHA-256)
- [ ] File read/write works on iOS
- [ ] HTTP requests work on iOS
- [ ] All cross-platform tests pass

### Wave 9: Integration testing and validation

**Goal:** Comprehensive end-to-end testing of the engine on iOS.

**What to do:**
- Load a real CommCare XForm XML on iOS, parse it, navigate questions
- Create cases, serialize them, query them on iOS
- Verify session navigation works on iOS
- Performance benchmarking (XPath evaluation, form loading)
- Generate Phase 3 completion report

**Acceptance criteria:**
- [ ] Real XForm loads and navigates on iOS
- [ ] Case CRUD operations work on iOS
- [ ] Session navigation works on iOS
- [ ] All 710+ JVM tests pass
- [ ] Phase 3 completion report generated

---

## Ordering and Dependencies

```
Wave 1 (collections) ──┬── Wave 2 (XML consumers)
                        ├── Wave 3 (Date + regex)
                        └── Wave 4 (serialization) ── Wave 5 (java.io)
                                                           │
                                                    Wave 6 (XPath)
                                                           │
                                                    Wave 7 (XForm + cases)
                                                           │
                                        Wave 8 (iOS impls) + Wave 9 (validation)
```

Waves 2 and 3 can run in parallel with Wave 4. Wave 1 must complete first as it's the most pervasive dependency.

---

## Risk Mitigations

1. **Vector→ArrayList thread safety**: Audit `synchronized` blocks before replacing. If any pattern depends on Vector's synchronization, wrap with `Collections.synchronizedList()` on JVM.

2. **PrototypeFactory registration**: The iOS registration approach requires enumerating all serializable types. Generate the registration list from the JVM PrototypeFactory's class scanning. Missing registrations will fail at runtime, not compile time.

3. **Transitive dependency cascades**: Files with no direct JVM imports may reference types still in jvmMain. Use `compileCommonMainKotlinMetadata` to validate each batch of moves.

4. **ExtUtil complexity**: ExtUtil is 455 lines of serialization dispatch. Consider splitting into common interface + platform-specific implementations rather than trying to make it fully cross-platform.

5. **Test coverage gap**: JVM tests use JUnit 4 and remain in jvmTest. New cross-platform tests in commonTest should cover the most critical paths. Target: 50+ cross-platform tests by Wave 9.
