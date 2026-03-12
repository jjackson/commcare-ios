# Phase 7 Completion Report: Break Cycle & Bulk Migrate

**Date**: 2026-03-12
**PR**: #160
**Branch**: `phase7/break-cycle-bulk-migrate`

## Summary

Phase 7 broke the 392-file connected component blocking bulk migration and moved all remaining Kotlin files from `main/java` to `commonMain` or `jvmMain`.

## Final Source Set Distribution

| Source set | .kt files | Notes |
|-----------|----------|-------|
| commonMain | 636 | Cross-platform (was 264 in Phase 6) |
| jvmMain | 105 | JVM platform implementations (was 76) |
| iosMain | 42 | iOS platform implementations |
| main/java | 0 .kt + 1 .java | StorageManagerCompat (Java compat) |

## What Was Done

### Wave 1: Extract JVM deps from blocker files (6 files)
Only 6 files of the 392 had direct JVM imports:
- `LruCache.kt` — `LinkedHashMap(0, 0.75f, true)` access-order constructor
- `InstallerFactory.kt` — `XFormInstaller` import
- `DetailParser.kt` — `GraphParser` import
- `ItemSetParsingUtils.kt` — `XFormParser.getAbsRef()` call
- `StorageManager.kt` — `Class<*>` overload
- `DataModelSerializer.kt` — `createXmlSerializer(output, encoding)` overload

### Wave 2: Move JVM-only files to jvmMain (29 files)
- XFormParser cluster (8 files) — kxml2 XML parsing
- DatabaseHelper, TableBuilder — carrotsearch HPPC
- GeoPointUtils, PolygonUtils — gavaghan geodesy
- XPath geo/crypto functions (5 files) — gavaghan, javax.crypto
- XFormInstaller, FormController, ResourceReferenceFactory, Graph, GraphParser

### Wave 3: Create platform abstractions (7 new files)
- `createAccessOrderedLinkedHashMap()` — expect/actual for LRU cache
- `JvmInstallerFactory` — XFormInstaller in jvmMain
- `DetailParser.graphParserFactory` — registration pattern for GraphParser
- `JvmPlatformInit` — JVM-specific component factory registration
- `XFormParserUtils.getAbsRef()` — extracted to commonMain
- `StorageManagerCompat.java` — Java compat for Class<*> → KClass<*>
- `createXmlSerializer(output, encoding)` — expect/actual for stream-based serializer

### Wave 4: Bulk migration (369 files)
Iterative compiler-validated migration moved all remaining files to commonMain.

### iOS @Throws Fixes
Kotlin/Native requires override methods to have exactly matching `@Throws` annotations:
- 19 ElementParser subclasses: added `UnfullfilledRequirementsException` to `parse()`
- 2 ResourceInstaller implementors + 1 subclass: added `UnfullfilledRequirementsException` to `install()`
- 4 TransactionParser subclasses: added `InvalidStructureException` to `commit()`

## Key Abstractions

### Registration Pattern for Platform Factories
Instead of direct class references, use companion var factories registered at JVM init:
```kotlin
// In commonMain:
class DetailParser {
    companion object {
        var graphParserFactory: ((PlatformXmlParser) -> ElementParser<out DetailTemplate>)? = null
    }
}

// In jvmMain (registered during init):
DetailParser.graphParserFactory = { parser -> GraphParser(parser) }
```

### Connected Component Problem
All 392 files formed ONE tightly-coupled connected component — no subset could move independently. The solution was to break the dependency by extracting the 6 files with actual JVM imports, creating abstractions, then moving everything at once.

## Tests
- 800+ JVM tests pass
- iOS simulator tests pass
- iOS framework builds successfully

## Phases 5-6 Cleanup
Phase 7's bulk migration also resolved most remaining Phase 5 and Phase 6 issues:
- Phase 5 Waves 4-9: Closed (completed by bulk migration)
- Phase 6 Waves 1-5, 7-8: Closed (completed by bulk migration)
- Phase 6 Wave 6 (#152): Gavaghan geodesy — intentionally remains in jvmMain (external lib)

## Known Limitations
- 105 jvmMain files are intentionally JVM-only (kxml2, gavaghan, javax.crypto, OkHttp, database)
- iOS PrototypeFactory has a hash computation inconsistency (instance vs static method use different algorithms)
- StorageManagerCompat.java is the sole remaining Java file (needed for ccapi/cli Java callers)
