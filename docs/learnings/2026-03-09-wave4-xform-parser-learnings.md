# Learning: Wave 4 XForm Parser Conversion (27 files)

**Date**: 2026-03-09
**Context**: Converting `org.javarosa.xform.parse`, `org.javarosa.xform.util`, `org.javarosa.model.xform`, `org.javarosa.form.api` (27 files) from Java to Kotlin — Wave 4 of Phase 1
**Status**: Active — these patterns apply to remaining waves

## New Pitfalls Discovered

### 1. Kotlin companion object methods don't inherit to subclasses

**Problem:** In Java, `FormInstance.unpackReference(ref)` works even though `unpackReference` is a static method on `DataInstance` (the superclass). Java treats static methods as "inherited" — callers can reference them through any subclass name. In Kotlin, companion object methods belong to the specific class and are NOT accessible through subclass names.

**Example:** `XFormSerializingVisitor` called `FormInstance.unpackReference(ref)`. After `DataInstance` was converted to Kotlin with `unpackReference` in its companion object, calling it via `FormInstance.unpackReference()` stopped compiling.

**Fix:** Always call companion object methods on the class that defines them, not a subclass. Change `FormInstance.unpackReference(ref)` → `DataInstance.unpackReference(ref)`.

**How to detect:** Grep for static method calls on subclass names. If the method is defined in a superclass's companion object, the call will fail.

### 2. `@JvmField` cannot be used on `open` properties

**Problem:** When a protected field needs both `@JvmField` (for Java subclass field access) and `open` (for Kotlin subclass override), they conflict. Kotlin does not allow `@JvmField` on `open` properties because JVM fields can't be overridden.

**Example:** `FormEntryCaption.element` needed `@JvmField` because `DummyFormEntryPrompt` (Java test) does `this.element = q` as direct field access. But `element` also seemed like it should be `open` since `FormEntryPrompt` extends the class.

**Fix:** Drop `open` from the property. `@JvmField` exposes the actual JVM field, which subclasses access directly (both Java and Kotlin). The field doesn't need to be `open` because subclasses access the inherited field, not override it.

### 3. Companion object members cannot be `protected`

**Problem:** Kotlin does not allow `protected` visibility on companion object members. When a Java class has `protected static final` constants accessed by subclasses, the Kotlin companion object equivalent cannot be `protected`.

**Example:** `XFormParserReporter.TYPE_ERROR` was `protected static final String` in Java, accessed by `JSONReporter` (a subclass in `src/translate/`). Kotlin companion objects don't support `protected`.

**Fix:** Use `internal` for constants that need to be visible to same-module subclasses. Since `const val` auto-inlines for Java callers, `internal const val` works for both Kotlin and Java access within the module.

### 4. Smart cast fails on mutable (`var`) properties

**Problem:** Kotlin's smart cast (automatic null check → non-null usage) does not work on `var` properties because another thread could change the value between the null check and usage.

**Example:** `XFormParseException.element` is a `var`. The `message` property getter checked `if (element == null)` then used `element` in the else branch. Kotlin rejected this because `element` could change between check and use.

**Fix:** Capture the mutable property to a local `val` before the null check:
```kotlin
val el = element
return if (el == null) super.message else super.message + XFormParser.getVagueLocation(el)
```

### 5. `const val` auto-inlines — no `@JvmField` needed for primitives/Strings

**Problem:** Early in the wave, we were adding `@JvmField` to companion object constants unnecessarily. For `String`, `Int`, `Long`, `Boolean`, and other primitive-typed constants, `const val` in a companion object compiles to a Java `public static final` field automatically.

**Fix:** Use `const val` (not `@JvmField val`) for compile-time constants in companion objects. Reserve `@JvmField` for non-const properties (mutable fields, non-primitive types) that Java code accesses as fields.

### 6. Large files (3,000+ lines) need dedicated handling

**Problem:** XFormParser.java (3,049 lines) exceeded AI agent token limits when converting in one pass. The agent ran out of output tokens before it could write the file.

**Fix for AI pipelines:** Treat files over ~1,000 lines as dedicated tasks. Instruct agents to minimize text output and use Write/Edit tools for all file content. If the agent still fails, break the conversion into structural phases (companion object first, then methods, then fix-up pass).

## Pre-Existing Platform-Sensitive Tests

Two test failures discovered during Wave 4 verification that are NOT caused by the conversion:

1. **`DateRangeUtilsTest.testDateConversion`** — Off-by-one day due to timezone-sensitive logic in `DateRangeUtils.java`. Uses `Date.getTimezoneOffset()` which produces incorrect results in UTC-negative timezones. Passes on CI (Ubuntu/UTC), fails locally on Windows (US Central).

2. **`XmlUtilTest.testPrettifyXml`** — Uses `System.lineSeparator()` which returns `\r\n` on Windows but the expected output file has Unix `\n` line endings. Passes on CI (Linux), fails on Windows.

Both are in untouched Java code with no dependency on converted Kotlin files.

## Checklist Additions for Future Waves

Before committing converted files, also check:
- [ ] Companion object method calls use the defining class, not a subclass
- [ ] `@JvmField` properties are not also `open`
- [ ] `protected static` constants become `internal const val` in companion (not `protected`)
- [ ] Mutable properties used in null checks are captured to local `val` first
- [ ] `const val` used for primitives/Strings (not `@JvmField val`)
