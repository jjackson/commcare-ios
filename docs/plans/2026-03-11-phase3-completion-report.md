# Phase 3: Engine on iOS — Completion Report

**Date:** 2026-03-11
**Status:** Partially complete (ceiling reached)

---

## Summary

Phase 3 aimed to move core engine code from `src/main/java` to `src/commonMain/kotlin` so that XPath evaluation, XForm parsing, case management, and session navigation would be callable from iOS. The phase achieved significant JVM dependency removal and moved 96 additional files to commonMain, but hit a natural ceiling: the core model classes (TreeReference, TreeElement, EvaluationContext, FormDef, etc.) have deep transitive dependencies on JVM APIs that require targeted refactoring rather than bulk migration.

---

## What Was Done

### Wave 1: Replace JVM collections (PR #74)
- Replaced `Vector<T>` → `MutableList<T>`, `Hashtable<K,V>` → `MutableMap<K,V>`, `Stack<T>` → `ArrayDeque<T>`, `Enumeration<T>` → `Iterator<T>` across 265 files
- Zero imports of java.util.Vector/Hashtable/Stack/Enumeration remaining

### Waves 2-6: Platform abstractions and dependency removal (PRs #88, #91, #95, #101-105)
- Migrated XML consumers to PlatformXmlParser (partial — 9 files still use kxml2/xmlpull directly)
- Replaced java.util.Date with PlatformDate abstraction
- Replaced java.util.regex with Kotlin Regex
- Extended serialization framework (Externalizable → expect/actual, PrototypeFactory with KClass)
- Extended java.io abstractions (PlatformDataInputStream/OutputStream)
- Moved XPath engine components to commonMain (partial)

### Wave 7: Move XForm/cases/session to commonMain (PRs #105, #109)
- Moved 27 files to commonMain (bringing total from 157 → 184)
- Converted 8 Java files to Kotlin (32 → 13 Java files remaining)
- Converted storage interfaces from `Class<*>` to `KClass<*>` with factory lambda pattern
- Added explicit `kotlin.jvm.*` imports to 137 files for commonMain compatibility
- Replaced `Math.xxx` with `kotlin.math.xxx` in 22 files
- Replaced `java.lang.Double.valueOf()` with Kotlin idioms in 12 files
- Moved `QueryContext.getQueryCache(Class<T>)` to jvmMain extension function

### Waves 8-9: Blocked
- Real iOS platform implementations and integration testing could not proceed because core engine classes remain in main/java

---

## File Distribution (end of Phase 3)

| Source Set | Files | Change from Phase 2 |
|-----------|-------|---------------------|
| commonMain | 184 .kt | +96 from 88 |
| jvmMain | 21 .kt | +11 from 10 |
| iosMain | 21 .kt | +10 from 11 |
| commonTest | 6 .kt | unchanged |
| src/main/java | 470 .kt, 13 .java | -97 .kt, -19 .java |

---

## Remaining JVM Dependencies in src/main/java

| Blocker | Files | Notes |
|---------|-------|-------|
| kxml2/xmlpull direct imports | 9 | XForm parser, serializers |
| java.io.* direct imports | 16 | Streams, File, URI |
| java.net.* imports | 7 | URL, URLConnection |
| okhttp3/retrofit2 imports | 7 | HTTP client layer |
| io.reactivex (RxJava) | 4 | Menu, Text, Entry, MenuDisplayable |
| javax.crypto/java.security | 3 | Crypto operations |
| io.opentracing | 1 | FormDef |
| Transitive deps (no direct JVM imports) | ~420 | Blocked by core model classes |

---

## Why Further Migration Stalled

The ~420 "pure" files (no direct JVM imports) can't move because they depend on core model classes that themselves depend on JVM APIs:

1. **EvaluationContext** → depends on TreeReference, XPath, java.io
2. **TreeReference / TreeElement** → depend on XPath engine, serialization with Class<*>
3. **FormDef** → depends on io.opentracing, java.io
4. **DataInstance / FormInstance** → depend on serialization framework
5. **Text / Entry / Menu** → depend on io.reactivex

Moving these requires targeted refactoring of each core class to abstract its JVM dependencies.

---

## Key Technical Decisions

1. **KClass + factory lambda pattern**: Replaced `Class<T>` reflection with `KClass<T>` + `() -> T` lambdas. Java callers use jvmMain extension functions.
2. **Iterative compiler-validated migration**: Move all candidates → compile → rollback failures → repeat. Safely identified moveable files.
3. **`fun interface`** for SAM conversion: Used for `TransactionParserFactory` to preserve Java lambda compatibility.
4. **`kotlin.jvm.*` explicit imports**: Required in commonMain files — the annotations work cross-platform when explicitly imported.

---

## Tests

- **710+ JVM tests passing** (`./gradlew jvmTest` — BUILD SUCCESSFUL)
- **21 cross-platform tests passing** (commonTest)
- No regressions introduced

---

## Assessment

Phase 3 achieved ~50% of its goal. The "easy" files moved (leaf types, interfaces, simple models), but the core engine graph remains JVM-bound. Further progress requires a different approach: instead of bulk migration, targeted refactoring of the ~10 core blocker classes to abstract their JVM dependencies. This is the focus of Phase 4.
