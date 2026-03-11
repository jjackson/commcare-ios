# Phase 5: Serialization Framework Refactor — Implementation Plan

**Date:** 2026-03-11
**Status:** Draft
**Prerequisite:** Phase 4 complete (204 commonMain files, 450 .kt in main/java). ExtUtil/ExtWrap* using `Class<*>` identified as the sole blocker for bulk migration.

---

## Goal

Replace `Class<*>` reflection patterns in the serialization framework (ExtUtil, ExtWrap*, PrototypeFactory, Hasher) with `KClass<*>` + factory lambda alternatives. This enables moving the framework to commonMain, which unblocks the ~450 transitively-dependent files.

**Exit criteria:** ExtUtil, all ExtWrap* classes, Hasher, and PrototypeFactory are in commonMain. 400+ total files in commonMain. All 710+ JVM tests pass. `compileCommonMainKotlinMetadata` succeeds.

---

## Current State

| Source Set | Files |
|-----------|-------|
| commonMain | 204 .kt |
| jvmMain | 59 .kt |
| iosMain | 33 .kt |
| src/main/java | 450 .kt |

### What's Already Cross-Platform

- `Externalizable` interface (commonMain)
- `ExternalizableWrapper` base class (commonMain)
- `ExtWrapIntEncoding*` classes (commonMain)
- `PrototypeFactory` expect/actual class (commonMain/jvmMain/iosMain)
- `SerializationHelpers` expect/actual (commonMain/jvmMain/iosMain) — lambda-based API, no `Class<*>`
- `NumericUtils` (commonMain)

### What Needs Refactoring (10 files)

| File | Location | JVM-Only Pattern |
|------|----------|-----------------|
| ExtUtil.kt | main/java | `read(in, type: Class<*>, pf)`, `data.javaClass.name` |
| ExtWrapBase.kt | main/java | `type: Class<*>` field, `isAssignableFrom(type)` |
| ExtWrapList.kt | main/java | `listImplementation: Class<out List<*>>`, `.newInstance()`, `Class.forName()` |
| ExtWrapMap.kt | main/java | Delegates to `ExtWrapBase(Class<*>)` |
| ExtWrapMapPoly.kt | main/java | Delegates to `ExtWrapBase(Class<*>)`, `ExtWrapTagged` |
| ExtWrapMultiMap.kt | main/java | Delegates to `ExtWrapBase(Class<*>)` |
| ExtWrapNullable.kt | main/java | Wraps `ExtWrapBase(Class<*>)` |
| ExtWrapListPoly.kt | main/java | Uses `ExtWrapTagged` |
| ExtWrapTagged.kt | main/java | `.javaClass`, `PrototypeFactory.getClassHash(Class<*>)`, `WRAPPER_CODES: HashMap<Class<*>, Int>` |
| Hasher.kt + ClassNameHasher.kt | main/java | `getHash(c: Class<*>)`, `c.name` |

### Other Files with `Class<*>` (8 files)

| File | Usage | Strategy |
|------|-------|---------|
| StorageManager.kt | `registerStorage(key, Class<*>)` overload | Add KClass overload, keep Class for JVM compat |
| DummyIndexedStorageUtility.kt | Already uses KClass | No change needed |
| TableBuilder.kt | `Class<*>` for DB mapping | Keep in main/java (JVM-only DB layer) |
| QueryContext.kt | `getQueryCache(Class<T>)` | Already has jvmMain extension |
| FormDef.kt | `getExtension(KClass<X>, factory)` | Already converted in Phase 4 |
| RestoreUtils.kt | `getDataType/applyDataType(KClass<*>)` | Already converted in Phase 4 |
| XFormParser.kt | `Class.forName` for custom actions | Keep in main/java (kxml2-dependent) |
| LivePrototypeFactory.kt | Extends PrototypeFactory | Will follow PrototypeFactory refactor |

---

## Wave Plan

### Wave 1: Refactor ExtUtil to remove Class<*>

**Files:** 1 (ExtUtil.kt)
**What:**
- Replace `read(in, type: Class<*>, pf)` with a cross-platform `read(in, type: KClass<*>, pf)` that dispatches on KClass
- Replace the `isAssignableFrom` check with KClass-based checks
- Replace `java.lang.Byte::class.java` etc. with `Byte::class`, `Int::class`, etc.
- Replace `data.javaClass.name` with `data::class.qualifiedName` in error message
- Add JVM-only `read(in, type: Class<*>, pf)` overload in jvmMain extension for backward compatibility
- Move core ExtUtil to commonMain

**Acceptance criteria:**
- [ ] ExtUtil.kt compiles in commonMain
- [ ] JVM backward-compatible overloads exist in jvmMain
- [ ] All 710+ JVM tests pass

### Wave 2: Refactor ExtWrapBase to remove Class<*>

**Files:** 1 (ExtWrapBase.kt)
**What:**
- Replace `type: Class<*>?` with `type: KClass<*>?`
- Replace `isAssignableFrom` check with `KClass.isSubclassOf()`
- Add secondary constructor accepting `Class<*>` in jvmMain extension for backward compat
- Move ExtWrapBase to commonMain

**Depends on:** Wave 1 (ExtWrapBase calls `ExtUtil.read`)

**Acceptance criteria:**
- [ ] ExtWrapBase.kt compiles in commonMain
- [ ] All 710+ JVM tests pass

### Wave 3: Refactor ExtWrapList to remove Class<*> and reflection

**Files:** 1 (ExtWrapList.kt)
**What:**
- Replace `listImplementation: Class<out List<*>>?` with a factory lambda `listFactory: (() -> MutableList<Any?>)?`
- Default factory to `{ ArrayList() }`
- Remove `Class.forName()` in `metaReadExternal` — replace with a registry of known list implementations
- Remove `.newInstance()` — use factory lambda instead
- `metaWriteExternal`: write a string tag for the list type instead of `listImplementation!!.name`
- Add JVM backward-compat constructors accepting `Class<*>` in jvmMain
- Move ExtWrapList to commonMain

**Depends on:** Wave 2 (ExtWrapList uses ExtWrapBase)

**Acceptance criteria:**
- [ ] ExtWrapList.kt compiles in commonMain
- [ ] Serialization round-trip for lists works correctly
- [ ] All 710+ JVM tests pass

### Wave 4: Refactor ExtWrapTagged and Hasher

**Files:** 3 (ExtWrapTagged.kt, Hasher.kt, ClassNameHasher.kt)
**What:**
- **Hasher**: Change `getHash(c: Class<*>)` to `getHash(className: String)`. Since ClassNameHasher only uses `c.name`, pass the name directly.
- **ClassNameHasher**: Implement `getHash(className: String)` instead of `getHash(c: Class<*>)`
- **ExtWrapTagged**: Replace `.javaClass` with `this::class`, replace `WRAPPER_CODES: HashMap<Class<*>, Int>` with `HashMap<KClass<*>, Int>`, replace `PrototypeFactory.getClassHash(Class<*>)` with KClass-based equivalent
- Add `PrototypeFactory.getClassHash(className: String)` to common expect
- Move all three to commonMain

**Depends on:** Wave 1 (uses ExtUtil), Wave 2 (uses ExtWrapBase indirectly)

**Acceptance criteria:**
- [ ] ExtWrapTagged, Hasher, ClassNameHasher compile in commonMain
- [ ] Hash values are compatible (reversed class name bytes)
- [ ] All 710+ JVM tests pass

### Wave 5: Refactor remaining ExtWrap* classes

**Files:** 4 (ExtWrapMap.kt, ExtWrapMapPoly.kt, ExtWrapMultiMap.kt, ExtWrapNullable.kt)
**What:**
- Update constructors to accept `KClass<*>` instead of `Class<*>`
- These mostly delegate to ExtWrapBase, so changes should be minimal after Wave 2
- Add JVM backward-compat constructors in jvmMain where needed
- Move all to commonMain

**Depends on:** Waves 2, 4 (these wrap ExtWrapBase and use ExtWrapTagged)

**Acceptance criteria:**
- [ ] All ExtWrap* files compile in commonMain
- [ ] All 710+ JVM tests pass

### Wave 6: Refactor PrototypeFactory and PrototypeManager

**Files:** 3 (PrototypeFactory JVM impl, PrototypeManager.kt, LivePrototypeFactory.kt)
**What:**
- Expand PrototypeFactory expect to include `addClass(className: String, factory: () -> Externalizable)` and `getClassHash(className: String)`
- JVM actual: Bridge `addClass(Class<*>)` to string-based API, keep `Class.forName` + `newInstance` as implementation detail
- Move PrototypeManager to use KClass-based registration where possible
- LivePrototypeFactory: Ensure it works with the refactored base class
- Keep JVM-specific constructors (Hasher, HashSet<String>) in jvmMain

**Depends on:** Wave 4 (Hasher refactored)

**Acceptance criteria:**
- [ ] PrototypeFactory expect API is sufficient for commonMain consumers
- [ ] JVM backward compatibility preserved
- [ ] All 710+ JVM tests pass

### Wave 7: Move ExtUtil/ExtWrap* consumers to commonMain

**Files:** ~50-100
**What:**
- With ExtUtil/ExtWrap* in commonMain, their consumers can now be moved
- Use iterative compiler-validated migration: move all candidates → compile → rollback failures → repeat
- Files using `SerializationHelpers` (53 files) should move easily since SerializationHelpers is already in commonMain

**Depends on:** Waves 1-6 (serialization framework in commonMain)

**Acceptance criteria:**
- [ ] 100+ additional files moved to commonMain
- [ ] `compileCommonMainKotlinMetadata` succeeds
- [ ] All 710+ JVM tests pass

### Wave 8: Bulk migration sweep

**Files:** remaining ~300+
**What:**
- Repeat iterative compiler-validated migration for all remaining files
- Identify any new blockers that weren't visible before
- Move JVM-only files (XFormParser, DB layer, HTTP, etc.) to jvmMain if not already there

**Depends on:** Wave 7

**Acceptance criteria:**
- [ ] 400+ total files in commonMain
- [ ] Core engine types (EvaluationContext, TreeReference, TreeElement, FormDef) in commonMain
- [ ] All 710+ JVM tests pass

### Wave 9: Validation and cleanup

**Files:** ~5 new
**What:**
- Add commonTest serialization round-trip tests for ExtWrap* types
- Verify binary format compatibility between JVM and iOS
- Clean up any remaining jvmMain backward-compat shims that are no longer needed

**Depends on:** Wave 8

**Acceptance criteria:**
- [ ] New cross-platform serialization tests pass on both JVM and iOS
- [ ] All 710+ JVM tests pass
- [ ] No unused backward-compat shims remain

---

## Dependency Graph

```
Wave 1 (ExtUtil) ──────────────────────────┐
                                            ├→ Wave 7 (consumer migration) → Wave 8 (bulk) → Wave 9 (validation)
Wave 2 (ExtWrapBase) ──┬→ Wave 3 (ExtWrapList)─┤
                       ├→ Wave 5 (remaining wraps)─┤
Wave 4 (Tagged+Hasher) ┴→ Wave 6 (PrototypeFactory)┘
```

Waves 1, 2, 4 can start in parallel. Waves 3, 5, 6 follow. Waves 7-9 are sequential.

---

## Risk Analysis

| Risk | Mitigation |
|------|-----------|
| Hash format incompatibility | ClassNameHasher uses `c.name` which is the FQN — same as `KClass.qualifiedName`. Verify with test. |
| ExtWrapList list implementation registry | Only ArrayList and LinkedList are used in practice. Hardcode a small registry. |
| JVM backward compat breakage | Keep `Class<*>` overloads as jvmMain extensions. Java callers won't notice the change. |
| Binary format change | The serialized format doesn't encode Class objects — it encodes hash bytes. Hashes are derived from class names, which don't change. |
| Kotlin compiler internal error with variance | Avoid `Class<out List<*>>` — use factory lambdas instead of generic Class types. |
| Large PR size | Split into 9 waves. Each wave has clear boundaries. |

---

## Acceptance Criteria (Phase-level)

- [ ] All ExtUtil, ExtWrap*, Hasher, ClassNameHasher in commonMain
- [ ] 400+ total files in commonMain
- [ ] Core engine types (EvaluationContext, TreeReference, TreeElement, XPathExpression) in commonMain
- [ ] All 710+ JVM tests pass
- [ ] `compileCommonMainKotlinMetadata` succeeds
- [ ] Cross-platform serialization round-trip tests pass on JVM and iOS
- [ ] Zero `Class<*>` imports in commonMain files
