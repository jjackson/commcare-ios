# Wave 4: Serialization Framework Learnings

**Date**: 2026-03-11
**Context**: Phase 3 Wave 4 — Abstract serialization framework (Issue #67)

## Key Finding: Class<*> is the fundamental blocker for ExtUtil

The original plan assumed we could create an expect/actual abstraction for the serialization framework (ExtUtil, ExtWrap* classes, Hasher). Investigation revealed that `Class<*>` (Java reflection) is deeply embedded and cannot be abstracted in commonMain.

### Where Class<*> is used

1. **ExtUtil.read(in, Class<*>, pf)** — Type dispatch via `isAssignableFrom()` for deserialization
2. **ExtUtil.write(out, data)** — Uses `data.javaClass.name` for error messages
3. **ExtUtil.deserialize(data, Class<*>, pf)** — Legacy API wrapping `read`
4. **PrototypeFactory** — `Class.forName()` + `newInstance()` for instantiation, hash-based lookup
5. **Hasher** — `getClassHashValue(Class<*>)` interface
6. **All ExtWrap* classes** — Use `Class<*>` for type references in serialization metadata

### What we accomplished instead

Rather than moving the full serialization framework, we:

1. **Expanded PrototypeFactory expect/actual** — Added companion utility methods (`compareHash`, `getWrapperTag`, `getClassHashSize`) that commonMain code needs for serialization boundaries
2. **Inlined simple ExtUtil calls** — For files moving to commonMain, replaced `ExtUtil.readString(in)` → `in.readUTF()` and `ExtUtil.writeString(out, s)` → `out.writeUTF(s)`. These are thin wrappers (readString adds JVM string interning, an optimization not functional)
3. **Moved data types that only use primitive serialization** — IAnswerData, UncastData, BooleanData, InvalidData, InvalidDateData, EntityFilter all moved to commonMain

## Kotlin Extension Functions Don't Solve the Split

We attempted splitting ExtUtil: portable methods in commonMain, JVM-only methods as extension functions on `ExtUtil.Companion` in src/main/java. This failed because:

**Kotlin member functions shadow extension functions with the same name.** When callers in other packages called `ExtUtil.read(in, SomeClass::class.java, pf)`, Kotlin found the companion member `read(in, ExternalizableWrapper, pf)` first, reported a type mismatch, and never tried the extension function `read(in, Class<*>, pf)`. This caused ~30 compilation errors across the codebase.

The shadowing rule is absolute — you cannot have a member `read` and an extension `read` with different parameter types and expect overload resolution to work across both.

## Kotlin-to-Kotlin fun calls don't synthesize property access

iOS `PlatformDate` defines `fun getTime(): Long`. In Kotlin, property-style access (`.time`) is only synthesized for Java getters, not Kotlin `fun` declarations. Code in commonMain must use the actual field name (`millis`) or call `getTime()` explicitly.

## Practical Strategy for commonMain Migration

Files can move to commonMain if they:
1. Only use primitive serialization (`in.readUTF()`, `out.writeBoolean()`, `in.readInt()`, etc.)
2. Don't reference `Class<*>`, `ExtUtil`, or `ExtWrap*` classes
3. Or can have ExtUtil calls inlined to their primitive equivalents

Files that use `ExtUtil.read(in, SomeType::class.java, pf)` or `ExtWrapTagged`/`ExtWrapList`/`ExtWrapMap` **cannot** move until the full serialization framework is abstracted (Phase 3 Wave 4's deferred work).

## PrototypeFactory Architecture

The expect/actual split works well for PrototypeFactory:
- **commonMain**: Declares `expect open class PrototypeFactory()` with companion utilities
- **jvmMain**: Full implementation using `Class.forName()`, `newInstance()`, hash-based class registry
- **iosMain**: Registration-based approach with factory lambdas keyed by class name hash

This pattern can serve as the template for future expect/actual abstractions of JVM-reflection-heavy classes.
