# Learning: Wave 3 XPath Engine Conversion (134 files)

**Date**: 2026-03-09
**Context**: Converting `org.javarosa.xpath` (134 files) from Java to Kotlin — Wave 3 of Phase 1
**Status**: Active — these patterns apply to remaining waves

## New Pitfalls Discovered

### 1. KDoc block comments containing `*/` in XPath patterns

**Problem:** XPath documentation naturally contains patterns like `/data/*/to`. When this appears inside a `/** ... */` KDoc comment, the `*/` in the wildcard path prematurely closes the block comment. Everything after that line becomes unparseable, causing dozens of cascade "unresolved reference" errors in completely unrelated files.

**Root cause in Wave 3:** Line 283 of `XPathPathExpr.kt` had ` * /data/*/to` in a KDoc comment. The `*/` closed the comment, making the rest of the file invalid Kotlin. This produced 22 cascade errors.

**Fix:** Escape wildcards in KDoc: `` /data/`*`/to `` (backtick-escaped). Always grep converted files for `*/` inside block comments before committing.

**Key insight for debugging:** When you see many "unresolved reference" errors for symbols that are clearly defined (e.g., companion object constants), the problem is usually the TARGET class failing to compile, not the calling class. Trace the cascade to find the root compilation error.

### 2. Abstract classes must stay abstract

**Problem:** When converting `abstract class` from Java to Kotlin, if the converter produces `open class` instead, tests that use reflection (e.g., `Modifier.isAbstract()`) or classpath scanning will fail. The class itself works fine at runtime, but test infrastructure that distinguishes abstract from concrete breaks.

**Example:** `XPathFuncExpr` was `abstract` in Java. The converter made it `open class`, and a test that finds "all non-abstract subclasses of XPathFuncExpr" started finding `XPathFuncExpr` itself, failing the test.

**Fix:** Always preserve `abstract` when converting. Check the original Java source if unsure. A class with methods that throw `UnsupportedOperationException("not implemented")` is almost certainly abstract in the original.

### 3. Nullable `model` parameter threading

**Problem:** In Java, `null` passes silently through method chains without crashing until the null is actually dereferenced. In Kotlin, a `model!!` non-null assertion at any point in the chain causes an immediate NPE, even if no method in that particular call path ever uses `model`.

**Example:** `XPathFuncExpr.evalRaw()` receives `model: DataInstance<*>?` from the abstract `XPathExpression.evalRaw()`. It then calls `evalBody(model!!, ...)`, but many XPath functions (like `true()`, `false()`, `random()`, `now()`) never use `model`. In Java, `null` flowed through harmlessly. In Kotlin, `model!!` crashed 64 tests.

**Fix:** When a parameter is nullable in a parent method, keep it nullable through the entire call chain. Don't add `!!` just to satisfy a child method's non-null signature — instead make the child method also accept nullable. Applied across 74 `evalBody` implementations.

### 4. Java `protected` means package-private + subclass access

**Problem:** Java's `protected` allows access from any class in the same package AND from subclasses. Kotlin's `protected` only allows subclass access. When a `protected` Java method is called from another class in the same package (not a subclass), the Kotlin version becomes inaccessible.

**Example:** `XPathStep.matches()` was `protected` in Java. `XPathPathExpr` (same package, not a subclass) called it. After conversion, Kotlin's `protected` blocked the call.

**Fix:** Use `internal` for Java `protected` methods that are called from same-package non-subclass code. This preserves the package-level access semantic.

### 5. Kotlin `Class<*>` maps to Java `Class<?>`, not raw `Class`

**Problem:** When a Kotlin method returns `HashMap<String, Class<*>>`, Java sees it as `HashMap<String, Class<?>>`. Java code using raw `Class` (no type parameter) gets a compilation error.

**Fix:** Update Java test code to use `Class<?>` when calling Kotlin methods that return `Class<*>`.

## Compilation Fix Cycle Efficiency

Wave 3 required 6 CI iterations (262 → 79 → 24 → 22 → 2 → 64 tests → 0 errors). Key efficiency insights:

1. **Cascade errors are misleading**: 22 errors from one KDoc `*/` bug. Always find the root compilation error first.
2. **Bulk fixes via sed**: When a signature change affects 74 files, use `sed` or `find -exec` instead of editing one-by-one.
3. **Test failures after compilation passes**: Compilation success doesn't mean tests pass. Kotlin's stricter null checking surfaces runtime NPEs that Java silently ignored.

## Checklist for Future Waves

Before committing converted files:
- [ ] Grep for `*/` inside `/** ... */` block comments
- [ ] Verify `abstract` classes remain `abstract`
- [ ] Check nullable parameter threading (no `!!` on params that could be null)
- [ ] Check `protected` methods called from same-package non-subclasses → use `internal`
- [ ] Verify Java test files compile against new Kotlin signatures (Class<?> vs Class)
