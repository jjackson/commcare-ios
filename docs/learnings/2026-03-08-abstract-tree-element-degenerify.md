# Learning: Remove type parameter from AbstractTreeElement

**Date**: 2026-03-08
**Context**: Wave 2 of commcare-core Java-to-Kotlin conversion
**Status**: Design approved, pending implementation

## Problem

`AbstractTreeElement<T>` is a core interface in commcare-core. When converted to Kotlin, every method returning `T` (e.g., `getChild(): T?`) generates a JVM `checkcast` instruction to the concrete type. The original Java code had none of these because of type erasure and raw type usage.

This caused ~96 test failures with `ClassCastException`:
```
CaseInstanceTreeElement cannot be cast to TreeElement
```

The root cause: classes like `InstanceBase implements AbstractTreeElement<TreeElement>` but actually store arbitrary `AbstractTreeElement` subtypes (like `CaseInstanceTreeElement`). Java allowed this via raw types; Kotlin enforces the type at runtime.

## Decision

**Remove the type parameter entirely.** Change `interface AbstractTreeElement<T>` to `interface AbstractTreeElement`.

### Why not keep the generic with a corrected type parameter?

- **The type parameter was always a lie.** `InstanceBase<TreeElement>` stored `CaseInstanceTreeElement`. Java let this slide via erasure; Kotlin exposes the lie.
- **F-bounded polymorphism (`T extends AbstractTreeElement<T>`) doesn't translate to Kotlin.** Kotlin's "Finite Bound Restriction" prevents `interface AbstractTreeElement<T : AbstractTreeElement<*>>`.
- **Widening the bound (Option 2) cascades everywhere.** Changing `InstanceBase` to `AbstractTreeElement<AbstractTreeElement<*>>` forces changes to `DataInstance.getBase()`, all callers that chain method calls, etc. And `AbstractTreeElement<*>` produces `Any?` on nested calls — worse than no generics.
- **Performance penalty.** Every generic method call inserts a `checkcast` instruction that wasn't in the original Java. Removing the parameter eliminates these.

### What the change looks like

- `AbstractTreeElement.kt`: Remove `<T>`, methods return `AbstractTreeElement?` instead of `T?`
- `InstanceBase.kt`: Remove `<TreeElement>` from implements, remove `as TreeElement` casts
- `ConcreteTreeElement.kt`: Remove type parameter
- `TreeElement.kt`: Override methods can still return `TreeElement` (covariant return)
- 6 Java implementor files: Remove type parameters from extends/implements
- ~25 Java/Kotlin caller sites: Change variable types or add explicit casts
- Remove all workarounds: `AbstractExternalDataInstance.java` bridge, `TypeErasureHelper.java`, `@Suppress("UNCHECKED_CAST")` annotations

### What stays generic

`DataInstance<T : AbstractTreeElement>` keeps its type parameter — it's meaningful. `FormInstance` genuinely stores `TreeElement` roots, `ExternalDataInstance` genuinely stores `AbstractTreeElement` roots.

## Key Takeaway for Future Waves

When converting Java generics to Kotlin, watch for:

1. **Raw type usage in Java** — Kotlin doesn't have raw types. If Java code uses raw types to bypass generics, the Kotlin conversion will fail at runtime.
2. **F-bounded polymorphism** (`T extends Foo<T>`) — Kotlin has the "Finite Bound Restriction" that prevents `T : Foo<*>` on the declaring interface.
3. **Type parameters only used in output positions** — if T is never an input parameter, consider whether the generic adds any real safety. If not, remove it.
4. **`checkcast` overhead** — Kotlin generates runtime type checks that Java's erasure avoided. This is both a correctness issue (ClassCastException) and a performance issue.

The pipeline's AI converter should be configured to detect and flag these patterns during conversion rather than fixing them post-hoc.
