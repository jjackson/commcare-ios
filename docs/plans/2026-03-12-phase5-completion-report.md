# Phase 5: Serialization Framework Refactor — Completion Report

**Date:** 2026-03-12
**Status:** Complete
**Duration:** 1 day (2026-03-12)

---

## Goal

Replace `Class<*>` reflection patterns in the serialization framework (ExtUtil, ExtWrap*, PrototypeFactory) with `KClass<*>` + factory lambdas. Move the framework to commonMain to unblock transitively-dependent files.

---

## Results

| Metric | Target | Actual |
|--------|--------|--------|
| commonMain files | 400+ | 227 (+23 from Phase 4's 204) |
| JVM tests passing | 710+ | 800+ |
| iOS build | Pass | Pass |
| ExtUtil/ExtWrap* in commonMain | All | All 12 files moved |
| Zero `Class<*>` in commonMain | Yes | Yes |
| Core engine types in commonMain | Yes | No — blocked by non-serialization JVM deps |

### Phase-level Acceptance Criteria

- [x] All ExtUtil, ExtWrap*, Hasher, ClassNameHasher in commonMain — **12 serialization files moved**
- [ ] 400+ total files in commonMain — **227 files** (blocker: non-serialization JVM deps in 16 files)
- [ ] Core engine types in commonMain — **Blocked** by FormDef (System.getProperty), ThreadLocal singletons, java.io
- [x] All 710+ JVM tests pass — **800+ tests pass**
- [x] `compileCommonMainKotlinMetadata` succeeds
- [ ] Cross-platform serialization round-trip tests — **Deferred to Phase 6**
- [x] Zero `Class<*>` imports in commonMain files

---

## What Was Done

### Waves 1-7: Serialization Framework Refactor (PR #145)

Refactored and moved 12 serialization files from `src/main/java` to `src/commonMain`:

| File | Key Changes |
|------|-------------|
| ExtUtil.kt | `KClass<*>` dispatch, `defaultPrototypeFactory()`, `PlatformDate.getTime()` |
| ExtWrapBase.kt | `type: KClass<*>?` field, `Class<*>` constructor removed |
| ExtWrapList.kt | `KClass<*>` constructors, `LIST_FACTORIES` internal |
| ExtWrapListPoly.kt | No changes needed |
| ExtWrapMap.kt | `createOrderedHashMap()`/`isOrderedHashMap()` abstractions |
| ExtWrapMapPoly.kt | Same OrderedHashtable abstraction |
| ExtWrapMultiMap.kt | `Class<*>` constructor removed |
| ExtWrapNullable.kt | `KClass<*>` constructor |
| ExtWrapTagged.kt | `obj::class`, `getClassHashForType(KClass)`, `classNameToKClass()`, `WRAPPER_CODES` KClass keys |
| SerializationLimitationException.kt | Moved as dependency |
| ExtWrapIntEncodingSmall.kt | Moved as dependency |
| Persistable.kt | Moved as dependency |

### New Abstractions Created

| File | Location | Purpose |
|------|----------|---------|
| ExtUtilJvm.kt | jvmMain | `Class<*>` backward-compat overloads for `read`/`deserialize` |
| ExtWrapJvmCompat.kt | jvmMain | Factory functions for Java callers: `ExtWrapBase(Class)`, `ExtWrapList(Class)`, etc. |
| ClassNameToKClass.kt | commonMain (expect) + jvmMain/iosMain (actual) | `classNameToKClass(String): KClass<*>` |
| OrderedMap.kt | commonMain (expect) + jvmMain/iosMain (actual) | `createOrderedHashMap()`, `isOrderedHashMap()` |
| DefaultPrototypeFactory.kt | commonMain (expect) + jvmMain/iosMain (actual) | `defaultPrototypeFactory(): PrototypeFactory` |

### Wave 8: Bulk Migration Attempt (PR not created — 0 files moved)

Applied iterative compiler-validated migration to all 430 remaining files in `src/main/java`. Result: **0 additional files could move**. All 430 files form a single tightly-coupled connected component, blocked by 16 files with direct JVM imports.

### Wave 9: Additional Files Moved

11 additional files moved to commonMain that were unblocked by the serialization framework move:
ColorUtils.kt, IDataPointer.kt, StreamsUtil.kt, IAnswerDataSerializer.kt, PointerAnswerData.kt, ByteArrayPayload.kt, DataPointerPayload.kt, IDataPayload.kt, IDataPayloadVisitor.kt, MultiMessagePayload.kt, MD5.kt

---

## File Distribution (Final)

| Source Set | Files | Change |
|-----------|-------|--------|
| commonMain | 227 .kt | +23 from Phase 4 |
| jvmMain | 65 .kt | +6 new abstractions |
| iosMain | 36 .kt | +3 new actuals |
| src/main/java | 430 .kt | -23 moved out |
| src/main/java | 0 .java | — |

---

## Why 400+ Target Was Not Met

The Phase 5 plan assumed that `Class<*>` in the serialization framework was the **sole** blocker for bulk migration. In reality, 16 files in `src/main/java` have direct JVM dependencies that are **not** serialization-related:

### Direct JVM Blocker Files (16 total)

**Group 1: java.io imports (8 files)**
- XFormParser.kt — `java.io.InputStreamReader`, `java.io.Reader`
- XFormParserFactory.kt — `java.io.Reader`
- XFormParserReporter.kt — `java.io.PrintStream`
- XFormSerializer.kt — `java.io.DataOutputStream`, `java.io.OutputStreamWriter`
- XFormUtils.kt — `java.io.InputStreamReader`
- XFormInstaller.kt — `java.io.InputStreamReader`
- LocalizationUtils.kt — `java.io.BufferedReader`, `java.io.InputStreamReader`
- ResourceTable.kt — `java.io.FileNotFoundException`

**Group 2: ThreadLocal (3 files)**
- PrototypeManager.kt — `ThreadLocal<PrototypeFactory?>`
- ReferenceHandler.kt — `ThreadLocal<ReferenceManager>`
- LocalizerManager.kt — `ThreadLocal<Localizer?>`

**Group 3: System.getProperty (1 file)**
- FormDef.kt — `System.getProperty("...enableOpenTracing")`

**Group 4: External libraries (4 files)**
- GeoPointUtils.kt — `org.gavaghan.geodesy.GlobalCoordinates`
- PolygonUtils.kt — `org.gavaghan.geodesy.*`
- XPathClosestPointOnPolygonFunc.kt — `org.gavaghan.geodesy.GlobalCoordinates`
- XPathIsPointInsidePolygonFunc.kt — `org.gavaghan.geodesy.GlobalCoordinates`

These 16 files block everything because the 430 files form one connected dependency graph — removing even one blocker from the graph can cascade and unlock many files.

---

## PRs

| PR | Description | Status |
|----|-------------|--------|
| #144 | iOS fix: Property.kt @Throws mismatch | Merged |
| #145 | Phase 5 Wave 8: Serialization framework to commonMain | Merged |
| #146 | Phase 5 Wave 8 learnings doc | Merged |

---

## Key Learnings

1. **LinkedHashMap is final in Kotlin/Native** — OrderedHashtable can't move to commonMain. Solved with `createOrderedHashMap()`/`isOrderedHashMap()` expect/actual.
2. **Top-level factory functions replace Java constructors** — Java callers use `import static` and call without `new`.
3. **@Throws must exactly match parent interface** — iOS metadata compilation catches mismatches JVM ignores.
4. **Serialization was necessary but not sufficient** — Moving ExtUtil/ExtWrap* unblocked the framework itself but the transitive dependency cluster remains blocked by java.io, ThreadLocal, System.getProperty, and gavaghan.

---

## Next Steps → Phase 6

Phase 6 should target the 16 direct JVM blocker files using expect/actual abstractions, then re-attempt bulk migration. The highest-ROI targets:

1. **FormDef.kt** (1 line change, unblocks 17+ files directly)
2. **ThreadLocal managers** (3 files, simple expect/actual)
3. **java.io Reader/Writer abstractions** (8 files)
4. **gavaghan replacement** (4 files)

See `docs/plans/2026-03-12-phase6-deep-platform-abstraction-plan.md`.
