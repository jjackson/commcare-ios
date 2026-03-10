# Wave 6-7 KMP Migration Learnings

## compileCommonMainKotlinMetadata is stricter than compileKotlinJvm

**Problem:** Files in commonMain compiled fine with `compileKotlinJvm` but failed with `compileCommonMainKotlinMetadata` (which CI runs via `./gradlew build`).

**Root cause:** `compileCommonMainKotlinMetadata` treats commonMain as true cross-platform code. The `kotlin.jvm` package is NOT auto-imported in metadata compilation, unlike JVM compilation where it's in default imports.

**Affected patterns:**
- `@JvmField` — needs explicit `import kotlin.jvm.JvmField`
- `@JvmStatic` — needs explicit `import kotlin.jvm.JvmStatic`
- `@JvmOverloads` — needs explicit `import kotlin.jvm.JvmOverloads`
- `@Synchronized` — NOT available in commonMain at all
- `Class<*>`, `newInstance()` — JVM reflection, not in common
- `Runtime`, `System` — JVM platform classes
- `String.format()` — JVM extension, not in common Kotlin
- `assert` — JVM-only
- `String(ByteArray, offset, length)` constructor — JVM-specific overload

**Fix:** Add explicit imports for `kotlin.jvm.*` annotations. Move files using JVM-only APIs back to jvmMain.

**Lesson:** Always test with `./gradlew build` (not just `compileKotlinJvm`) before pushing. The full build includes metadata compilation for all targets.

## Import-based file analysis is insufficient for commonMain moves

**Problem:** Grepping for `^import java\.` misses:
1. **Fully-qualified references:** `java.util.AbstractMap.SimpleEntry` (no import line)
2. **JVM-only Kotlin APIs:** `String.format()`, `@Synchronized`, `assert`
3. **Auto-imported JVM types:** `Class<*>`, `Runtime`, `System` (in `java.lang`, auto-imported on JVM)
4. **Platform-specific constructors:** `String(ByteArray, Int, Int)` exists on JVM but not common

**Fix:** After moving files, always compile metadata and iterate.

## PlatformIOException typealias approach works well

`actual typealias PlatformIOException = java.io.IOException` on JVM means:
- All existing code that catches/throws IOException continues to work unchanged
- `@Throws(PlatformIOException::class)` is equivalent to `@Throws(IOException::class)` on JVM
- Catching `PlatformIOException` catches any IOException on JVM
- On iOS, PlatformIOException is a simple Exception subclass

## Most files can't move to commonMain due to transitive dependencies

Of 184 files with no direct JVM imports after IOException replacement, only 2 could actually compile in commonMain. The bottleneck is that most files reference types (TreeReference, EvaluationContext, Detail, ExtUtil, etc.) that are deeply JVM-dependent and remain in jvmMain.

The serialization framework (Externalizable, PrototypeFactory, ExtUtil) is the key blocker — it uses `Class<*>` reflection throughout and is referenced by 131+ classes.

## PrototypeFactory reflection is the root cause of the serialization blocker

**Problem:** `PrototypeFactory.getInstance()` uses `Class.forName(className).newInstance()` to create objects from stored class names at deserialization time. This JVM reflection pattern has no equivalent in Kotlin/Native (iOS). Because `ExtUtil` (455 lines of serialization dispatch) depends on `PrototypeFactory`, and 131+ model classes depend on `ExtUtil`, the entire engine's data model is transitively locked to jvmMain.

**Dependency chain:**
```
TreeReference → Externalizable → ExtUtil → PrototypeFactory → Class.forName()
FormDef       → Externalizable → ExtUtil → PrototypeFactory → Class.forName()
CaseIndex     → Externalizable → ExtUtil → PrototypeFactory → Class.forName()
... (131+ classes)
```

**Fix:** Replace with expect/actual where JVM keeps reflection and iOS uses a registration-based factory (`Map<String, () -> Externalizable>`). All serializable types must be explicitly registered on iOS at app startup. The registration list can be generated from JVM's PrototypeFactory class scanning.

**Tradeoff:** Missing registrations on iOS fail at runtime, not compile time. But the set of serializable types is finite and well-known.
