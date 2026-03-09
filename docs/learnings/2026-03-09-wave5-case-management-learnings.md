# Wave 5: Case-Management Conversion Learnings

**Date**: 2026-03-09
**Wave**: 5 — case-management (60 files)
**PR**: #24

## New Pitfalls

### 1. JVM signature clash: constructor `val` vs interface method

When a Kotlin class implements an interface that defines `fun getMult(): Int`, and the class constructor has `val mult: Int`, the Kotlin compiler generates a `getMult()` getter for the property that clashes with the override method.

**Symptom**: `Inherited platform declarations clash: The following declarations have the same JVM signature (getMult()I)`

**Fix**: Use a private/protected backing name for the constructor parameter:
```kotlin
// BAD — generates getMult() that clashes with the interface override
abstract class StorageBackedChildElement(
    protected val mult: Int
) : AbstractTreeElement {
    override fun getMult(): Int = mult  // clash!
}

// GOOD — no clash
abstract class StorageBackedChildElement(
    protected val _mult: Int
) : AbstractTreeElement {
    override fun getMult(): Int = _mult
}
```

**Root cause**: Kotlin properties auto-generate getters with the same JVM signature as interface methods. This only happens when the interface defines a `fun` (not a `val`), typically because the interface was converted from Java where it was a method.

### 2. JVM signature clash: field getter vs explicit method

Similarly, a `var` field generates a getter that clashes with an explicit method of the same name.

**Symptom**: `Platform declaration clash: The following declarations have the same JVM signature (getQueryPlanner())`

**Fix**: Rename the backing field:
```kotlin
// BAD
protected var queryPlanner: QueryPlanner? = null
protected open fun getQueryPlanner(): QueryPlanner { ... }

// GOOD
private var _queryPlanner: QueryPlanner? = null
protected open fun getQueryPlanner(): QueryPlanner { ... }
```

### 3. Java boxed types in Kotlin generics

When Java code uses boxed types in generics (e.g., `ArrayList<Pair<Integer, Integer>>`), Kotlin requires primitive types: `ArrayList<Pair<Int, Int>>`. Using `Integer` in Kotlin generics causes a type mismatch with methods that expect `Pair<Int, Int>`.

**Fix**: Always use Kotlin primitive types (`Int`, `Long`, `Boolean`) in generic type arguments, never Java boxed types (`Integer`, `Long`, `Boolean`).

### 4. Kotlin-to-Kotlin: `fun` stays as function call

When calling existing Kotlin code that defines `fun getOriginalContext()`, you must call it as `getOriginalContext()`, not property-style `originalContext`. This is different from calling Java getters from Kotlin, where property syntax works.

**Why**: Kotlin only synthesizes property access for Java getters. Kotlin `fun` declarations are always function calls, even if they follow the `getFoo()` naming pattern.

**Applies to**: `EvaluationContext.getOriginalContext()`, `EvaluationContext.getCurrentQueryContext()`, `CacheHost.getCachePrimeGuess()`, `QueryContext.getScope()`

### 5. `override` when hiding superclass methods

When a superclass defines `open fun copy()` and a subclass has its own `fun copy()`, Kotlin requires `override` — the method won't silently hide the parent like in Java.

**Symptom**: `'copy' hides member of supertype 'ExternalDataInstance' and needs an 'override' modifier`

## Observations

- Wave 5 had more JVM interop issues than prior waves because the `cases` package has deep inheritance hierarchies mixing interfaces (still using `fun` methods from Java-era conversions) with Kotlin constructor properties.
- The `AbstractTreeElement` interface defines many methods (`getMult()`, `getName()`, `isLeaf()`) that were converted to `fun` in earlier waves. Child elements that use constructor properties for these values will always hit the JVM signature clash pattern.
- Consider converting `AbstractTreeElement` method-style declarations to `val` properties in a future cleanup pass — this would eliminate the clash pattern entirely.
