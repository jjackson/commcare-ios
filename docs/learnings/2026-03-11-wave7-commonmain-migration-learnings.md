# Wave 7: commonMain Migration Learnings

## Iterative Compiler-Validated File Move

The most effective approach for moving files to commonMain is an iterative compiler-validated process:

1. Identify candidates (files with no direct JVM imports)
2. Move ALL candidates to `src/commonMain/kotlin`
3. Run `compileCommonMainKotlinMetadata`
4. Roll back files mentioned in error messages
5. Repeat steps 3-4 until build passes

This is necessary because static import analysis misses same-package references — Kotlin files in the same package reference each other without explicit imports.

## Key Finding: Transitive Dependency Cascade

~95% of candidate files (no direct JVM imports) are blocked by transitive dependencies. Of 395 candidate files, only ~5-6 could actually compile in commonMain. The root cause: they reference core classes (EvaluationContext, TreeElement, FormDef, Text) that are still in main/java due to JVM dependencies.

**Top cascade blockers** (classes referenced by many others):
- `Text` (126 refs) — `java.util.Calendar` + `io.reactivex.Single`
- `CommCarePlatform` (96 refs) — `::class.java` for storage registration
- `QueryContext` (81 refs) — `Class<T>` overload for Java compatibility
- `IStorageUtilityIndexed` (75 refs) — `getPrototype(): Class<*>`
- `FormDef` (37 refs) — `io.opentracing.util.GlobalTracer`

## KClass<T> + Factory Lambda Pattern

Converting from `Class<T>` to `KClass<T>` for KMP compatibility:

**Before (JVM-only):**
```kotlin
fun <T : Cache> getCache(type: Class<T>): T {
    val t = map[type] as? T ?: type.newInstance()
    map[type] = t
    return t
}
// Called as: getCache(MyCache::class.java)
```

**After (KMP-compatible):**
```kotlin
fun <T : Cache> getCache(type: KClass<T>, factory: () -> T): T {
    val t = map[type] as? T ?: factory()
    map[type] = t
    return t
}
// Called as: getCache(MyCache::class) { MyCache() }
```

For Java backward compatibility, add an overload in JVM-specific code:
```kotlin
fun <T : Cache> getCache(type: Class<T>): T {
    return getCache(type.kotlin) {
        type.getDeclaredConstructor().newInstance()
    }
}
```

## kotlin.jvm.JvmField/JvmStatic in commonMain

`@JvmField`, `@JvmStatic`, and `@JvmOverloads` ARE available in commonMain, but require explicit imports:
```kotlin
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmOverloads
```

On JVM these are real annotations; on other platforms they're silently ignored. This is because they're in the `kotlin.jvm` package which is available in common code (unlike `java.*`).

137 files needed these imports added — the annotations were used without explicit imports since JVM auto-imports `kotlin.jvm.*`.

## kotlin.math Replacements

`java.lang.Math` methods have Kotlin equivalents in `kotlin.math`:

| Java | Kotlin |
|------|--------|
| `Math.min(a, b)` | `min(a, b)` (import kotlin.math.min) |
| `Math.abs(x)` | `abs(x)` |
| `Math.pow(a, b)` | `a.pow(b)` (extension function, not standalone!) |
| `Math.log(x)` | `ln(x)` (note: renamed!) |
| `Math.PI` | `PI` (import kotlin.math.PI) |

**Gotcha:** `kotlin.math.pow` is an extension function on `Double`, not a standalone function like `Math.pow`. Use `a.pow(b)` not `pow(a, b)`.

## java.lang.Double.valueOf() Removal

`java.lang.Double.valueOf(expr)` can be removed in most cases — Kotlin auto-boxes. But watch for:
- Nested parentheses: regex `\(([^)]+)\)` doesn't handle `valueOf(foo.bar().toDouble())`
- String parsing: `Double.valueOf(stringValue)` → `stringValue.toDouble()`

## fun interface for SAM Conversion

When converting a Java interface with a single abstract method to Kotlin, add `fun` keyword to preserve SAM conversion:

```kotlin
// Java: interface Foo { void bar(String s); }
// Kotlin callers use: Foo { s -> ... }

// Must be:
fun interface Foo {
    fun bar(s: String)
}
// NOT just 'interface Foo' — that breaks SAM lambdas in Kotlin callers
```

## Direct JVM Blocker Categories

For the ~80 files with direct JVM dependencies:

| Category | Files | Requires |
|----------|-------|----------|
| `Class<>` / `::class.java` | 38 | KClass conversion or expect/actual |
| `java.io` (InputStream, etc.) | 16 | Platform IO abstraction (PlatformInputStream exists) |
| `synchronized{}` | 9 | Platform concurrency abstraction |
| `kxml2/xmlpull` | 9 | PlatformXmlParser migration |
| `okhttp3/retrofit2` | 11 | Platform network abstraction |
| `io.reactivex` | 4 | Platform async abstraction |
| `java.util.Calendar/Date` | 4 | PlatformDate extension |
| `javax.crypto/java.security` | 6 | Platform crypto abstraction |
