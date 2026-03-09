# Learning: Kotlin Conversion Pitfalls from Waves 1-2

**Date**: 2026-03-08
**Context**: Converting javarosa-utilities (115 files) and javarosa-model (82 files) from Java to Kotlin
**Status**: Active — these patterns apply to all remaining waves

## Recurring Issues

### 1. Nullable parameters from Java callers

**Problem:** Kotlin's `!!` (non-null assertion) on parameters that Java callers pass as `null` causes `NullPointerException` at runtime.

**Example:** `fun getText(locale: String)` — Java callers pass `null` for "use default locale". Kotlin crashes on entry.

**Fix:** Use `?` types for any parameter that could receive `null` from Java code. When converting, check all Java call sites for the method. If any passes `null`, the Kotlin parameter must be nullable.

### 2. Generic type parameters with raw type usage

**Problem:** Java raw types (e.g., `DataInstance` without `<T>`) don't exist in Kotlin. When a Java interface has `AbstractTreeElement<T>` and implementations use raw types, Kotlin enforces the type at runtime via `checkcast` — causing `ClassCastException`.

**Example:** `InstanceBase implements AbstractTreeElement<TreeElement>` but actually stores `CaseInstanceTreeElement`. Java's erasure hid this; Kotlin exposes it.

**Fix:** Evaluate whether the type parameter is meaningful. If it's consistently bypassed via raw types, remove it entirely. See [AbstractTreeElement degenerification](./2026-03-08-abstract-tree-element-degenerify.md) for the full analysis.

### 3. `Vector<SubType>` return type invariance

**Problem:** When an interface method returns `Vector<AbstractTreeElement>`, a Java subclass returning `Vector<TreeElement>` compiles due to erasure. In Kotlin, `Vector<TreeElement>` is NOT a subtype of `Vector<AbstractTreeElement>` — Java generics are invariant.

**Fix:** Use `Vector<out AbstractTreeElement>` (covariant) in Kotlin interface declarations where subclasses need to return more specific types. Or change the return type to match exactly.

### 4. `open` keyword needed for Java subclasses

**Problem:** Kotlin classes are `final` by default. Any class that is subclassed by Java code (in unconverted groups) must be marked `open`, along with any methods that are overridden.

**Example:** `FormInstance` is subclassed by `AndroidSandbox.FormInstance` in commcare-android. Without `open`, Java compilation fails.

**Fix:** Before converting a class, check if it's subclassed anywhere (including in downstream projects like commcare-android and FormPlayer). Mark `open` proactively.

### 5. `@JvmField` on protected fields accessed by Java subclasses

**Problem:** Kotlin properties generate getters/setters by default. Java subclasses that access `super.field` directly (not via getter) get a compilation error because the field doesn't exist — only the getter method does.

**Fix:** Add `@JvmField` to any `protected` or `public` property that Java subclasses access as a field. Also use `@JvmStatic` on companion object methods called from Java.

### 6. Local build verification before CI

**Problem:** Pushing to CI and waiting for results adds 5-10 minutes per iteration. With 10+ fix iterations per wave, this wastes hours.

**Fix:** Always run `./gradlew compileKotlin compileJava` locally before pushing. This catches 90%+ of interop issues in seconds. Only push to CI after local compilation succeeds. Run `./gradlew test` locally for the final verification.

## Pipeline Recommendations

The AI converter should be configured to:
1. Auto-detect nullable parameters by scanning Java call sites before conversion
2. Flag classes with Java subclasses and add `open` automatically
3. Flag generic interfaces with raw type usage in implementations
4. Run `compileKotlin compileJava` after each file conversion, not just at the end of the group
5. Add `@JvmField`/`@JvmStatic` annotations proactively based on usage analysis
