# Phase 6: Deep Platform Abstraction — Completion Report

**Date:** 2026-03-12
**Status:** Complete (ceiling reached)
**Duration:** 1 day (2026-03-12)
**PR:** #156 (Waves 1-13), #158 (learnings)

---

## Goal

Remove JVM dependencies from the 16 direct blocker files identified in Phase 5 to unblock bulk migration of 430 remaining files to commonMain.

**Exit criteria:** 500+ total files in commonMain. Core engine types in commonMain.

---

## Results

| Metric | Target | Actual |
|--------|--------|--------|
| commonMain files | 500+ | 264 (+37 from Phase 5's 227) |
| jvmMain files | — | 76 (+11 from Phase 5's 65) |
| iosMain files | — | 40 (+4 from Phase 5's 36) |
| main/java files | <100 | 392 |
| JVM tests passing | 800+ | 800 (0 failures) |
| iOS build | Pass | Pass |
| Core engine types in commonMain | Yes | **No** — blocked by circular dependency |

### Phase-level Acceptance Criteria

- [x] 6 of 16 direct JVM blocker files resolved
- [ ] 500+ total files in commonMain — **264 files** (ceiling: circular dependency)
- [ ] Core engine types in commonMain — **Blocked** by FunctionUtils ↔ EvaluationContext ↔ XPathNodeset cycle
- [x] All 800 JVM tests pass
- [x] `compileCommonMainKotlinMetadata` succeeds
- [x] iOS CI passes

---

## What Was Done

### PR #156: Waves 1-13

#### Waves 1-2: FormDef System.getProperty + DateRangeUtils (2 files)
- Created `PlatformSystemProperty` expect/actual — `System.getProperty()` on JVM, stub on iOS
- Replaced `System.getProperty` in FormDef.kt
- Replaced `java.text.Normalizer` in DateRangeUtils.kt with Kotlin's `kotlin.text.Normalizer` (available in 1.9+, but DateRangeUtils required different approach)

#### Waves 3-5: java.io abstractions (3 files)
- Created `PlatformInputStreamReader` expect/actual — wraps `InputStreamReader` on JVM, Foundation on iOS
- Replaced `java.io` in LocalizationUtils.kt, NodeEntityFactory.kt, PerformanceTuningUtil.kt
- Created `PlatformLock` expect/actual with `isLocked` extension property (typealias compatibility)
- Created `PlatformRuntime` for `Runtime.getRuntime().availableProcessors()`
- Created `BackgroundThread` expect/actual for thread management

#### Wave 6: OrderedHashtable rewrite
- Rewrote from `extends LinkedHashMap` to composition (`MutableMap` interface + `LinkedHashMap` backing)
- Required for KMP since LinkedHashMap is final in Kotlin/Native
- Widened SerializationHelpers: `HashMap` → `Map`/`MutableMap` across 25+ consumer files
- Converted `OrderedMap` from expect/actual to regular commonMain functions

#### Wave 7: SizeBoundVector
- Created commonMain version (was already a simple class with no JVM deps)

#### Waves 8-9: Locale/Reference system migration (22 files to commonMain)
- Reference system: ReferenceManager, RootTranslator, PrefixedRootFactory, ReferenceHandler, ReferenceDataSource
- Date/time: DateData, DateTimeData, TimeData, DateUtils, CalendarUtils
- XML parsing: DataModelPullParser, TransactionParser, TransactionParserFactory, ElementParser, ActionableInvalidStructureException
- CommCare XML: BestEffortBlockParser, LedgerXmlParsers, RootParser
- Other: ResourceLocation, Base64, LocaleArrayDataSource, Localization

#### Wave 10-11: PrototypeFactory + PrototypeManager
- Added secondary constructor to PrototypeFactory expect/actual for HashSet<String>
- Moved PrototypeManager to commonMain
- Replaced `clone() as HashSet<String>` with `HashSet(globalPrototypes)`

#### Wave 12: iOS CI fixes
- Fixed @Throws filter mismatches (3 files: BestEffortBlockParser, LedgerXmlParsers, RootParser)
- Fixed PlatformDate Foundation import (wildcard `import platform.Foundation.*`)

#### Wave 13: JVM-only files to jvmMain (24 files)
- Reference: JavaFileRoot, JavaHttpRoot
- Serialization: Hasher, ClassNameHasher, LivePrototypeFactory
- Storage: DummyIndexedStorageUtility, DummyStorageIterator
- Database: TableBuilder, DatabaseHelper
- Geo: GeoPointUtils, PolygonUtils, XPathClosestPointOnPolygonFunc, XPathIsPointInsidePolygonFunc
- XForm: XFormParser, XFormSerializingVisitor, IElementHandler, QuestionExtensionParser, UploadQuestionExtensionParser, XFormSerializer, XFormUtils, InterningKXmlParser, DataModelSerializer, XFormParserFactory, XFormInstaller

### Post-PR: Bulk migration sweep (0 additional files moved)

Applied iterative compiler-validated migration to all 392 remaining main/java files. Result: **0 additional files could move**. All 392 files form a single connected component blocked by circular dependencies between core types.

---

## The Circular Dependency Ceiling

The remaining 392 main/java files cannot move to commonMain because they form one tightly-coupled connected component. The critical circular chain:

```
EvaluationContext → FunctionUtils → XPathNodeset → EvaluationContext
                 → TreeElement → TreeReference → ...
                 → DataInstance → FormInstance → ...
```

### Why bulk migration fails

1. **291 files** directly reference one of: `EvaluationContext`, `TreeElement`, `TreeReference`, `DataInstance`, `FormInstance`, `AbstractTreeElement`, `FunctionUtils`, `XPathNodeset`, `XPathExpression`, `FormDef`, `IFormElement`
2. **101 files** don't reference these directly, but depend on types that do (same-package references, transitive deps)
3. Moving any subset fails because they reference types still in main/java

### What would need to happen

To move the core types, the circular dependency must be broken. Options:

1. **Move the entire cluster at once** — would require resolving all JVM imports in all 392 files simultaneously. The remaining JVM deps are:
   - `StorageManager.Class<*>` methods (Java callers can't use Kotlin extensions)
   - `LruCache` (needs access-ordered LinkedHashMap — JVM-specific)
   - `EncryptionUtils` (javax.crypto)
   - `XFormParser`/`IElementHandler` (kxml2, already in jvmMain)
   - Various `org.commcare.xml.*` parsers using kxml2

2. **Break the cycle** — Extract interfaces or split files to break EvaluationContext → FunctionUtils → XPathNodeset → EvaluationContext. This is the serialization ceiling from Phase 4, now manifesting as a type-dependency ceiling.

3. **Accept the current distribution** — 264 files in commonMain is sufficient for iOS app development if the app uses the engine through a well-defined API layer. The main/java files are accessible from jvmMain, and iOS implementations can be provided as needed.

---

## New Platform Abstractions Created

| Abstraction | commonMain (expect) | jvmMain (actual) | iosMain (actual) |
|-------------|-------------------|-----------------|-----------------|
| PlatformSystemProperty | `get(key): String?` | `System.getProperty()` | returns null |
| PlatformThreadLocal | `get()`, `set()` | `java.lang.ThreadLocal` | simple property |
| PlatformLock | `lock()`, `unlock()`, `isLocked` | `ReentrantLock` typealias | `NSRecursiveLock` |
| PlatformRuntime | `availableProcessors()` | `Runtime.getRuntime()` | `NSProcessInfo` |
| BackgroundThread | `start()`, `join()` | `java.lang.Thread` | `NSThread` |
| PlatformInputStreamReader | `readLine()`, `close()` | `InputStreamReader` | Foundation |
| PlatformFileNotFoundException | Exception class | `FileNotFoundException` typealias | Custom class |

---

## File Distribution (Final)

| Source Set | Files | Change from Phase 5 |
|-----------|-------|-------------------|
| commonMain | 264 .kt | +37 |
| jvmMain | 76 .kt | +11 |
| iosMain | 40 .kt | +4 |
| main/java | 392 .kt, 0 .java | -38 |

---

## Key Learnings

1. **Kotlin compiler ICE with expect/actual in jvmMain**: Moving files from `main/java` to `jvmMain` triggers `IrFakeOverrideSymbol.getOwner: should not be called` when classes transitively reference expect/actual types. Workaround: keep non-actual files in `main/java`.

2. **Extension property on typealias**: When JVM actual is a `typealias`, new members must be extension properties, not class members, to avoid expect/actual mismatch.

3. **iOS Foundation wildcard imports**: Individual method imports like `secondsFromGMTForDate` don't resolve on Kotlin/Native. Must use `import platform.Foundation.*`.

4. **@Throws filter strictness**: KMP metadata compilation requires EXACT match of @Throws annotations on overrides. JVM is lenient but iOS/metadata is not.

5. **OrderedHashtable rewrite**: Can't extend `LinkedHashMap` in KMP (final in Kotlin/Native). Composition pattern with `MutableMap` interface works, but requires widening `HashMap` → `Map`/`MutableMap` across 25+ consumer files.

6. **The connected component problem**: Even with 0 direct JVM imports, files can't move to commonMain if they reference types still in main/java through same-package or transitive dependencies. All 392 remaining files form one component.

---

## Recommendations for Next Phase

**Option A: Break the cycle (high effort, high reward)**
- Create interfaces/abstractions to break EvaluationContext → FunctionUtils → XPathNodeset cycle
- e.g., extract `XPathEvaluator` interface, make FunctionUtils depend on it instead of EvaluationContext directly
- Would unlock ~300+ files to move to commonMain

**Option B: iOS API layer (medium effort, practical)**
- Define a clean API boundary between commonMain (264 files) and main/java (392 files)
- iOS app interacts only through commonMain types
- JVM-specific code (XPath evaluation, XML parsing, case processing) stays in main/java
- Build iOS-specific implementations for needed functionality

**Option C: Pause migration, build app features (low effort, immediate value)**
- 264 commonMain files include: serialization, reference system, localization, date handling, XML parsing interfaces, utilities
- Sufficient to build iOS app shell with form rendering, data entry, basic case management
- Migration can resume later when specific engine features are needed on iOS
