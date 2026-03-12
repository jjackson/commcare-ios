# Phase 5 Serialization Framework Migration Learnings

**Date:** 2026-03-11
**Context:** Phase 5 Waves 1-7 — Refactoring serialization framework from Class<*> to KClass<*> and attempting bulk migration to commonMain.

## Learning 1: LiveHasher has side effects in getHash()

**Problem:** `LivePrototypeFactory.LiveHasher.getHash(c: Class<*>)` registers classes in the factory as a side effect. When `Hasher.getClassHashValue()` was changed to delegate to `getClassHashValueByName(type.name)` (string-based path), it bypassed `getHash()` and broke registration.

**Fix:** Keep `getClassHashValue(type: Class<*>)` calling `getHash(c)` directly. Add `getClassHashValueByName(className: String)` as a SEPARATE path that calls `getHashByName(className)`. Don't unify them — the Class path has side effects the String path must not have.

## Learning 2: KClass.qualifiedName differs from Class.getName() for inner classes

**Problem:** Java's `Class.getName()` uses `$` for inner classes (`Outer$Inner`), while Kotlin's `KClass.qualifiedName` uses `.` (`Outer.Inner`). Using `qualifiedName` for hash computation produces different hashes than the factory registered with `Class.getName()`.

**Fix:** For JVM code that must match existing serialized data, always use `javaClass.name` or `type.java.name`, never `::class.qualifiedName`. The string-based cross-platform path should use qualified names, but JVM backward-compat code must use Java names.

## Learning 3: ExtUtil.read(Class<*>) uses Class.newInstance(), not PrototypeFactory

**Problem:** The original `ExtUtil.read(in, type: Class<*>, pf)` creates instances via `type.newInstance()` directly — no PrototypeFactory needed. The new KClass-based `read` tried to use factory-based creation, which fails when tests pass `pf = null` and the default PrototypeManager doesn't have the class registered.

**Fix:** Keep two separate read paths:
- `read(in, type: Class<*>, pf)` — JVM path using `PrototypeFactory.getInstance(type)` (reflection)
- `read(in, type: KClass<*>, pf)` — Cross-platform path using `createExternalizableInstance(type)` (expect/actual)

## Learning 4: Only 23 of 419 "clean" files could actually migrate

**Problem:** Of 450 files in main/java, only 31 had direct JVM-only imports. The other 419 appeared clean. But when moved to commonMain, they fail because they reference other main/java files transitively. The tight coupling between model classes (TreeElement ↔ FormDef ↔ EvaluationContext ↔ XPathExpression) means they must move as a cluster, and the cluster depends on ExtUtil/ExtWrap* which have Class<*>.

**Key insight:** The serialization framework (10 files with Class<*>) is the ROOT BLOCKER. Moving it to commonMain would unblock ~400 files, but it requires:
1. `expect fun createExternalizableInstance(type: KClass<*>)` for instance creation
2. Restructuring ExtWrapTagged.writeTag/readTag for cross-platform use
3. All consumers calling `new ExtWrapBase(SomeClass.class)` need JVM-compat constructors

## Learning 5: Consumer files don't import ExtUtil directly

**Discovery:** All 91 files using serialization go through `SerializationHelpers` (already in commonMain). Zero files outside the externalizable package import ExtUtil or ExtWrap* directly. The serialization framework is self-contained — but it blocks the model classes that implement `Externalizable` and call `ExtUtil.read/write` in their `readExternal/writeExternal` methods.

## Learning 6: Iterative compiler-validated migration works but cascades

**Process:** Moved all 419 candidate files → compiled → got 48 failures → moved them back → compiled → got 102 more failures (new dependencies exposed) → moved back → got 188 more → moved back → got 47 → moved back → got 11 → moved back → 0 errors. Each round exposes files that depended on what was just moved back.

**Result:** Only 23 files survived all iterations. The remaining 396 form one big transitive dependency cluster that must move together.
