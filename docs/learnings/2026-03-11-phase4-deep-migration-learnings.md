# Phase 4: Deep Migration — Learnings

**Date:** 2026-03-11

---

## 1. ExtUtil/ExtWrap* Creates an Insurmountable commonMain Ceiling

**Problem:** Nearly every model class implements `Externalizable` and delegates serialization to `ExtUtil`, which uses `Class<*>`, `Class.forName()`, and `newInstance()` — all JVM-only APIs. The `ExtWrap*` family (ExtWrapList, ExtWrapMap, ExtWrapTagged) uses `Class<out List<*>>` type tokens that trigger Kotlin compiler internal errors when compiled for Kotlin/Native.

**Impact:** Even files with zero direct JVM imports can't move to commonMain because they import types that transitively depend on ExtUtil. ~420 files are blocked by this single dependency chain.

**Lesson:** In a large codebase migration to KMP, the serialization framework is the critical path blocker. It should be addressed early, not last. The iterative bulk migration approach (move all → compile → rollback failures) is effective at *discovering* the ceiling but can't *break through* it.

## 2. Iterative Compiler-Validated Migration Is Effective but Limited

**Pattern:** Move N files to commonMain → run `compileCommonMainKotlinMetadata` → files that error get moved back → repeat until stable.

**Pros:** Safely identifies which files can move without manual dependency analysis. Each round resolves the outermost layer of failures.

**Cons:** Required 6 rounds for 445 files, and only 9 survived. The approach discovers *that* there's a ceiling but doesn't help *break through* it. Each round takes ~30 seconds of compile time.

**Lesson:** Use this approach for the final "sweep" after targeted refactoring, not as the primary migration strategy.

## 3. platformSynchronized: Inline Expect/Actual for Zero-Cost Abstraction

**Pattern:**
```kotlin
// commonMain
expect inline fun <R> platformSynchronized(lock: Any, block: () -> R): R

// jvmMain
actual inline fun <R> platformSynchronized(lock: Any, block: () -> R): R =
    kotlin.synchronized(lock, block)

// iosMain - no-op (single-threaded K/N default)
actual inline fun <R> platformSynchronized(lock: Any, block: () -> R): R = block()
```

**Why inline:** Avoids function call overhead on the hot JVM path. The iOS no-op compiles away entirely.

**Why not @Synchronized:** `@Synchronized` is `kotlin.jvm.Synchronized` — available in commonMain with explicit import but generates no code on non-JVM targets. `platformSynchronized` wraps code blocks rather than methods, which is what most call sites need.

**Lesson:** For JVM threading primitives, inline expect/actual is the cleanest KMP pattern. Don't try to make `@Synchronized` work cross-platform.

## 4. TypeTokenUtils: Bridging KClass and Class in Mixed Codebases

**Problem:** `IFunctionHandler.getPrototypes()` returns `ArrayList<Array<Any>>` where each `Any` is a type token (originally `Class<*>`). Some handlers now return `KClass<*>` tokens, others still return `Class<*>`. The matching code needs to handle both.

**Solution:** `TypeTokenUtils` expect/actual with two functions:
- `isInstanceOfTypeToken(token: Any, value: Any): Boolean` — checks if value is instance of type described by token
- `isTypeTokenEqual(token: Any, target: KClass<*>): Boolean` — checks if token represents the same type as target

JVM implementation handles both `KClass<*>` and `Class<*>`. iOS only handles `KClass<*>`.

**Lesson:** During migration, type token systems need a bridge layer that accepts both old (Class) and new (KClass) token types. Don't force all callers to convert at once.

## 5. XFormParseException: String Location Instead of kxml2 Element

**Problem:** `XFormParseException` stored a kxml2 `Element` reference, making it JVM-only. But the exception is thrown from dozens of places in cross-platform code.

**Solution:** Changed from `Element?` to `String?` (element location). Created `getVagueLocation(element: Element): String` helper in XFormParser to convert at throw sites. The string contains element name and attributes — enough for debugging.

**Lesson:** Exception classes are high-value migration targets because they're imported widely. Making them cross-platform unblocks many transitive dependents. Replace platform-specific objects with String descriptions where the object is only used for error messages.

## 6. XFormConstants: Breaking Import Cycles with Constant Extraction

**Problem:** Many files import XFormParser just for string constants like `LABEL_ELEMENT`, `VALUE_ELEMENT`. XFormParser itself is deeply JVM-bound (kxml2, java.io). These imports create false transitive dependencies.

**Solution:** Extracted constants to a standalone `XFormConstants` object in commonMain. Changed imports in consumer files.

**Lesson:** Look for "false dependency" patterns where files import a large class just for constants. Extracting constants to a standalone object breaks import cycles and enables migration of consumer files.

## 7. Kotlin Compiler Internal Error with Class<out List<*>>

**Problem:** When `ExtWrapList` (which uses `Class<out List<*>>` constructor parameter) was compiled for commonMain/Kotlin Native, the compiler crashed with "Shouldn't be here" internal error.

**Lesson:** Some JVM generic patterns using `Class<*>` with variance are fundamentally incompatible with Kotlin/Native compilation. This isn't a missing API — it's a compiler limitation. Don't attempt to move files with these patterns; refactor them first.

## 8. .initCause() Is JVM-Only

**Problem:** `Throwable.initCause()` exists in JVM but not in Kotlin common. Files using it can't move to commonMain.

**Solution:** Remove `initCause()` calls. If the cause information is needed, include it in the exception message string or use a constructor that accepts `cause` parameter.

**Lesson:** Check for JVM-only `Throwable` methods: `initCause()`, `getStackTrace()`, `setStackTrace()`, `getSuppressed()`. These have no common Kotlin equivalents.
