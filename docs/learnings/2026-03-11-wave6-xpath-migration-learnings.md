# Wave 6 XPath Migration Learnings

**Date:** 2026-03-11
**Context:** Phase 3 Wave 6 — Move XPath engine to commonMain

## Key Findings

### 1. The Serialization Framework is the Critical Path Blocker

The XPath engine can't move to commonMain because of a circular dependency chain that bottlenecks on JVM reflection in the serialization framework:

```
TreeReference ←→ XPathExpression ←→ EvaluationContext (circular)
     ↓                    ↓
ExtWrapListPoly    DataInstance, QueryContext
     ↓
Class.forName() / .javaClass / .newInstance()
```

Every `Externalizable` class that needs to serialize polymorphic collections or nullable typed fields depends on `ExtWrapListPoly`, `ExtWrapNullable`, or `ExtWrapTagged`, all of which use `Class<*>` JVM reflection. Until the serialization framework (Wave 4) is abstracted, TreeReference can't move, and nothing depending on TreeReference can move.

**Lesson:** Wave ordering in the Phase 3 plan is correct. Don't try to skip ahead to Wave 6 before completing Wave 4.

### 2. Logger Migration Requires Full Dependency Chain

Moving Logger to commonMain required moving 8 files total:
- Logger.kt, ILogger (Java→Kotlin), WrappedException (Java→Kotlin), FatalException (Java→Kotlin)
- LogEntry (Java→Kotlin), IFullLogSerializer (Java→Kotlin), StreamLogSerializer (Java→Kotlin)
- SortedIntSet.kt (already Kotlin, needed serialization rewrite)

**Key changes:**
- `System.err.println()` → `platformStdErrPrintln()` (expect/actual using NSLog on iOS)
- `Thread { throw e }.start()` / `Thread.sleep()` → `platformStartCrashThread()` / `platformSleep()` (expect/actual)
- `Math.min()` → `kotlin.math.min()`
- `e.getClass().getName()` → `e::class.simpleName ?: e::class.toString()`
- `java.util.Date` → `PlatformDate` in ILogger and LogEntry
- `java.io.IOException` → `PlatformIOException` in ILogger and StreamLogSerializer

**Lesson:** When moving a file to commonMain, trace its full dependency chain first. Don't move the file until all dependencies are resolved.

### 3. SortedIntSet Serialization Can Bypass ExtWrapList

SortedIntSet used `ExtWrapList(Integer::class.java)` for serialization, which depends on JVM reflection. Since it's just serializing a list of ints, the serialization was rewritten to use `ExtUtil.writeNumeric`/`readNumeric` directly — simpler and reflection-free.

**Lesson:** Not every `Externalizable` needs the ExtWrap* framework. Simple types can serialize directly with `ExtUtil.readNumeric`/`writeNumeric`/`readString`/`writeString`.

### 4. Wave 3 (Date/regex) Was Already Complete

When checking Wave 3 acceptance criteria, found zero `java.util.Date` and zero `java.util.regex` imports in Kotlin source files. This work was done incrementally during Phase 2 and earlier Wave 6 prep (PlatformDate abstraction, DateUtils migration).

**Lesson:** Check acceptance criteria before starting work — the wave may already be done.

### 5. "Zero Import" Files Often Have Hidden Dependencies

The Explore agent initially reported ~50 "zero import" leaf files that could move immediately. On closer inspection, most referenced types in the same package that were still blocked (e.g., `InstanceRoot` references `AbstractTreeElement`, `ExpressionCacher` references `ExpressionCacheKey` which references `TreeReference`).

**Lesson:** A file having no `import` statements doesn't mean it has no dependencies — it may reference same-package types. Always check every type reference in the file body, not just imports.

### 6. Object Without @JvmStatic Pattern

When moving Kotlin `object` declarations to commonMain, `@JvmStatic` must be removed (not available in common). Java callers must change from `ClassName.method()` to `ClassName.INSTANCE.method()`. `const val` in companion objects auto-inlines to `public static final` in bytecode, so Java callers can still access constants directly.

Files affected: Logger, TraceSerialization, and any other `object` declarations.
