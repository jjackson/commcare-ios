# Design: Remove AbstractTreeElement Type Parameter

**Date**: 2026-03-08
**Learning**: [docs/learnings/2026-03-08-abstract-tree-element-degenerify.md](../learnings/2026-03-08-abstract-tree-element-degenerify.md)

## Summary

Remove the type parameter `T` from `interface AbstractTreeElement<T>` in commcare-core. All child-returning methods (`getChild`, `getChildAt`, `getChildrenWithName`) change from returning `T` to returning `AbstractTreeElement`. This eliminates JVM checkcast instructions that cause ClassCastException at runtime.

## Implementation Steps

### 1. Modify `AbstractTreeElement.kt`

```diff
-interface AbstractTreeElement<T> {
-    fun getChild(name: String, multiplicity: Int): T?
-    fun getChildrenWithName(name: String): Vector<T>
-    fun getChildAt(i: Int): T?
+interface AbstractTreeElement {
+    fun getChild(name: String, multiplicity: Int): AbstractTreeElement?
+    fun getChildrenWithName(name: String): Vector<AbstractTreeElement>
+    fun getChildAt(i: Int): AbstractTreeElement?
```

All other methods stay unchanged (they don't use T).

### 2. Modify Kotlin implementors

**`ConcreteTreeElement.kt`**: Remove `<T : AbstractTreeElement<*>>`, implement `AbstractTreeElement` directly.

**`TreeElement.kt`**: Extends `ConcreteTreeElement`. Override methods can return `TreeElement` (covariant return types are valid in Kotlin).

**`InstanceBase.kt`**: Change `AbstractTreeElement<TreeElement>` to `AbstractTreeElement`. Remove all `as TreeElement` casts from `getChild`, `getChildAt`, `getChildrenWithName`.

### 3. Modify Java implementors

| File | Change |
|------|--------|
| `StorageBackedTreeRoot.java` | Remove `<T extends AbstractTreeElement>` from implements clause |
| `QuerySensitiveTreeElement.java` | Same |
| `QuerySensitiveTreeElementWrapper.java` | Same |
| `StorageInstanceTreeElement.java` | Same |
| `StorageBackedChildElement.java` | Remove type param from `implements QuerySensitiveTreeElement<TreeElement>` |
| `CaseChildElement.java` | Override methods return `AbstractTreeElement` |

### 4. Simplify `DataInstance.kt`

```diff
-abstract class DataInstance<T : AbstractTreeElement<*>> : Persistable {
+abstract class DataInstance<T : AbstractTreeElement> : Persistable {
```

`DataInstance` keeps its own `T` — it's meaningful for distinguishing `FormInstance` (T=TreeElement) from `ExternalDataInstance` (T=AbstractTreeElement).

### 5. Remove workarounds

- Delete `AbstractExternalDataInstance.java` (Java bridge class)
- Delete `TypeErasureHelper.java`
- `ExternalDataInstance.kt`: Extend `DataInstance` directly, implement `getRoot()` directly
- Remove `@Suppress("UNCHECKED_CAST")` annotations in: InstanceBase, EvaluationContext, DataInstance, SendAction, FormDef

### 6. Fix caller sites

Callers that assign `getChild()`/`getChildAt()` results to `TreeElement` variables need either:
- Change variable type to `AbstractTreeElement` (preferred, if they only use AbstractTreeElement methods)
- Add explicit `(TreeElement)` cast in Java / `as TreeElement` in Kotlin (if they need TreeElement-specific methods)

### 7. Verify locally, then push

```bash
export JAVA_HOME="$(cygpath -w '/c/Program Files/Android/Android Studio/jbr')"
bash ./gradlew compileKotlin compileJava  # ~30s after warm
bash ./gradlew test                        # full verification
```

## Files Changed

~20 files total across interface, implementors, callers, and workaround removals.

## Risk

Zero behavioral change — the code does exactly what the original Java did. The only difference is that casts that were implicit via Java erasure become explicit where needed.
