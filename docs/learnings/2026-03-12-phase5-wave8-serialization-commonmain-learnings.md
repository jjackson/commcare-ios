# Phase 5 Wave 8: Serialization Framework to commonMain — Learnings

## Date: 2026-03-12

## Context
Moved ExtUtil, ExtWrap*, and related serialization classes from `main/java` to `commonMain` to unblock transitive dependency migration.

## Key Learnings

### 1. LinkedHashMap is final in Kotlin/Native
OrderedHashtable extends `LinkedHashMap`, which is **final** in Kotlin/Native. This means:
- OrderedHashtable cannot be moved to commonMain directly
- Solution: Keep in main/java + create `expect/actual` functions (`createOrderedHashMap()`, `isOrderedHashMap()`) for cross-platform map creation/checking
- JVM actual uses OrderedHashtable, iOS actual uses LinkedHashMap (which is inherently ordered)

### 2. Top-level Kotlin functions can't replace Java constructors
When moving classes to commonMain and removing `Class<*>` constructors, Java callers that use `new ExtWrapFoo(SomeType.class)` cannot simply call top-level Kotlin factory functions — Java requires `new` for constructors.

**Solution:** Define top-level factory functions in jvmMain with `@file:JvmName("ExtWrapJvmCompat")`, then Java callers use `static import`:
```java
import static org.javarosa.core.util.externalizable.ExtWrapJvmCompat.*;
// Then: ExtWrapList(Integer.class) — no `new` keyword
```

### 3. Class<*>→KClass<*> migration pattern for serialization
When converting serialization code from JVM-specific `Class<*>` to cross-platform `KClass<*>`:

1. **Constructor access**: `Class.newInstance()` → `PrototypeFactory.createInstance(KClass<*>)` (expect/actual)
2. **Hash computation**: `PrototypeFactory.getClassHash(Class<*>)` → `PrototypeFactory.getClassHashForType(KClass<*>)` (expect/actual)
3. **Default factory**: `PrototypeManager.getDefault()` → `defaultPrototypeFactory()` (expect/actual)
4. **Class name lookup**: `Class.forName(name).kotlin` → `classNameToKClass(name)` (expect/actual)
5. **Runtime type**: `obj.javaClass` → `obj::class` (built-in Kotlin)
6. **KClass property access**: `kclass.java` (JVM-only) → not needed in commonMain

### 4. Backward-compat layer structure
For JVM backward compatibility with Class<*> APIs:
- `ExtUtilJvm` — contains `read(Class<*>)` and `deserialize(Class<*>)` overloads
- `ExtWrapJvmCompat` — contains factory functions for all ExtWrap* Class<*> constructors
- Both in `jvmMain`, not `commonMain`

### 5. @Throws filter must EXACTLY match in commonMain
On JVM, a mismatched `@Throws` is a warning. On Kotlin/Native (commonMain metadata compilation), it's an **error**:
```
Member overrides different '@Throws' filter from 'interface Externalizable : Any'
```
If the interface declares `@Throws(A::class, B::class)`, the override MUST have both — not just `@Throws(A::class)`.

### 6. Serialization framework alone doesn't unblock bulk migration
Moving ExtUtil/ExtWrap* to commonMain unblocked only ~11 additional files (beyond the 12 serialization files themselves). The remaining ~430 files are blocked by **core model classes** (TreeReference, TreeElement, FormDef, EvaluationContext) which have deeper JVM dependencies:
- DateUtils (PlatformDate property patterns)
- OrderedHashtable (final LinkedHashMap)
- ThreadLocal singletons
- System/Runtime APIs

The next migration wave needs to target these core model classes directly.

## File Distribution After Wave 8
- commonMain: 227 .kt files (was 204)
- main/java: 430 .kt files (was 449)
- jvmMain: ~65 .kt files (includes new compat files)
