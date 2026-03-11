# Phase 4: Deep Migration — Completion Report

**Date:** 2026-03-11
**Status:** Complete (ceiling reached)

---

## Summary

Phase 4 targeted the 67 Kotlin files and 13 Java files with direct JVM imports that were blocking ~390 transitively-dependent files from moving to commonMain. All 10 waves completed. The phase successfully removed JVM dependencies from most blocker files and moved 20 net new files to commonMain, but the bulk migration attempt (Wave 10) revealed that deep transitive dependency chains through the serialization framework (ExtUtil/ExtWrap*/PrototypeFactory) prevent the remaining ~450 files from moving without a fundamental serialization framework refactoring.

---

## What Was Done

### Wave 1: Quick wins (PR #122)
- Replaced `java.lang.Math` → `kotlin.math`, `Objects.equals()` → `==`, `java.util.Locale` → expect/actual
- Removed `synchronized` blocks or replaced with expect/actual where needed

### Wave 2: Convert remaining Java files to Kotlin (PR #122)
- Converted all 13 remaining `.java` files to Kotlin
- **Zero Java source files remain** in the codebase (test files excluded)

### Wave 3: Complete Date/Calendar migration (PR #125)
- Replaced remaining `java.util.Date`, `java.util.Calendar`, `java.util.TimeZone`, `java.text.SimpleDateFormat`
- Extended PlatformDate abstractions with formatting and calendar operations

### Wave 4: Migrate xmlpull/kxml2 consumers (PR #128)
- Isolated kxml2 from cross-platform code
- Extracted `XFormConstants` (string constants like LABEL_ELEMENT) from XFormParser to commonMain
- Refactored `XFormParseException` to use `String?` location instead of kxml2 `Element` → moved to commonMain
- Wrapped 31+ XFormParser call sites with `getVagueLocation(element)` conversion

### Wave 5: Abstract org.json to PlatformJson (PR #124)
- Created PlatformJson abstraction for JSON parsing
- Migrated graph config and JsonUtils

### Wave 6: Abstract io.reactivex and tracing (PR #123)
- Replaced `io.reactivex.Single` with callback/interface pattern
- Replaced `datadog`/`opentracing` tracing with no-op interface abstraction

### Wave 7: Abstract java.io and java.net (PRs #126, #127)
- Moved JVM-only files to jvmMain
- Created platform abstractions for remaining java.io/java.net patterns
- Extended PlatformFile, PlatformIO, PlatformUrl abstractions

### Wave 8: KClass conversion (PR #129)
- Replaced `Class<*>` and `.javaClass` with `KClass<*>` and `this::class` in 8 commonMain-candidate files
- Created `TypeTokenUtils` expect/actual for cross-platform type token matching (KClass on common, supporting both KClass and Class on JVM)
- Refactored `XPathCustomRuntimeFunc.matchPrototype` to use `isInstanceOfTypeToken`/`isTypeTokenEqual` helpers
- Changed `FormDef.getExtension()` from reflection-based to factory lambda pattern

### Wave 9: Move HTTP/network files to jvmMain (PR #124)
- Moved okhttp3/retrofit2 consumers to jvmMain where they belong

### Wave 10: Bulk migration attempt (PR #130)
- Attempted to move all ~445 remaining files to commonMain
- Required 6 iterative rounds of compiler-validated migration (move → compile → rollback failures → repeat)
- **9 net new files moved to commonMain**: XFormParseException, XFormConstants, Reference, ReferenceFactory, ReleasedOnTimeSupportedReference, Localizer, TimezoneProvider, SimpleNode
- Created `platformSynchronized()` expect/actual abstraction (JVM → `kotlin.synchronized`, iOS → no-op)
- Removed `@Synchronized` from 8 files for commonMain compatibility
- Removed `.initCause()` (JVM-only) from 6 files

---

## File Distribution

| Source Set | Files | Change from Phase 3 |
|-----------|-------|---------------------|
| commonMain | 204 .kt | +20 from 184 |
| jvmMain | 59 .kt | +38 from 21 |
| iosMain | 33 .kt | +12 from 21 |
| commonTest | 6 .kt | unchanged |
| src/main/java | 450 .kt, 0 .java | -20 .kt, -13 .java |

---

## Why 450 Files Remain in src/main/java

The bulk migration attempt (Wave 10) was the key test. After moving all 445 files and iteratively rolling back those with compilation errors, only 9 could stay. The root cause:

### The ExtUtil/ExtWrap Dependency Chain

Nearly every model class implements `Externalizable` and uses `ExtUtil` for serialization. `ExtUtil` has deep dependencies on `Class<*>` (JVM reflection):

```
EvaluationContext → DataInstance → ExtUtil → Class<*>
TreeReference → ExtUtil → Class<*>
FormDef → ExtUtil → Class<*>
ResourceTable → ExtUtil → Class<*>
```

The `ExtWrap*` family (ExtWrapList, ExtWrapMap, ExtWrapTagged, etc.) uses `Class<out List<*>>`, `Class.forName()`, and `Class.newInstance()` extensively. When `ExtWrapList` was moved to commonMain, it triggered a **Kotlin compiler internal error** ("Shouldn't be here"), indicating these patterns are fundamentally incompatible with Kotlin/Native compilation.

### Transitive Cascade

Because `ExtUtil` is used by ~131 classes, and those classes are imported by ~300+ others, the entire dependency graph collapses when ExtUtil can't move. Files with zero direct JVM imports still fail because they import types that import types that use ExtUtil.

### What Would Unblock Further Migration

1. **Replace ExtUtil serialization framework** with KClass-based alternatives (no `Class.forName()`, no `newInstance()`)
2. **Replace ExtWrap* classes** with Kotlin-native serialization patterns
3. **Or**: Accept a hybrid architecture where serialization stays JVM-only and iOS uses a separate serialization layer (e.g., kotlinx-serialization)

---

## New Platform Abstractions Created

| Abstraction | commonMain | jvmMain | iosMain |
|------------|-----------|---------|---------|
| `platformSynchronized()` | expect fun | `kotlin.synchronized()` | no-op (single-threaded) |
| `TypeTokenUtils` | expect funs | Handles both KClass and Class | KClass-only |
| `XFormConstants` | String constants | — | — |

---

## Key Technical Decisions

1. **platformSynchronized as inline expect/actual**: Preserves zero-cost synchronization on JVM while being a no-op on iOS (single-threaded Kotlin/Native default).
2. **TypeTokenUtils bridge**: Allows `IFunctionHandler.getPrototypes()` to return `Array<Any>` containing either `KClass<*>` (new code) or `Class<*>` (legacy code), checked at runtime.
3. **Factory lambda over reflection**: `FormDef.getExtension(KClass<X>, factory: () -> X)` replaces `getExtension(Class<X>)` which used `newInstance()`.
4. **Iterative compiler-validated migration**: Move all candidates → `compileCommonMainKotlinMetadata` → rollback failures → repeat. Discovered the ExtUtil ceiling efficiently.

---

## Tests

- **710+ JVM tests passing** (`./gradlew test` — BUILD SUCCESSFUL)
- **21 cross-platform tests passing** (commonTest)
- No regressions introduced across any wave

---

## PRs

| PR | Wave(s) | Description |
|----|---------|-------------|
| #122 | 1-2 | JVM pattern removal + Java→Kotlin conversion |
| #125 | 3 | Date/Calendar/TimeZone abstractions |
| #128 | 4 | kxml2 isolation |
| #124 | 5, 9 | org.json abstraction + HTTP/graph to jvmMain |
| #123 | 6 | io.reactivex and tracing abstractions |
| #126 | 7 (partial) | JVM-only file moves + simple pattern fixes |
| #127 | 7 | java.io/net/text/concurrent platform abstractions |
| #129 | 8 | KClass conversion |
| #130 | 10 | Bulk migration + platformSynchronized |

---

## Assessment

Phase 4 achieved its goal of removing direct JVM dependencies from blocker files, but discovered that the serialization framework (ExtUtil/ExtWrap*) creates an insurmountable ceiling for bulk migration. The ~450 files in src/main/java are transitively locked by this dependency.

**Options for further progress:**
1. **Serialization framework rewrite** — Replace ExtUtil/ExtWrap* with KClass-based registration factory (large scope, ~131 classes affected)
2. **Hybrid architecture** — Accept that serialization stays JVM-only; iOS uses kotlinx-serialization independently
3. **Interface extraction** — Create commonMain interfaces for core engine types, with JVM implementations staying in main/java

The current 204 commonMain files + 59 jvmMain + 33 iosMain represent a stable KMP architecture. The core question for Phase 5 is which approach to the serialization framework yields the best path to iOS engine functionality.
