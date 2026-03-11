# Wave 7: Serialization Migration Learnings

Date: 2026-03-11

## Key Findings

### 1. `@JvmField`/`@JvmStatic` are NOT available in commonMain

Despite being Kotlin annotations, `@JvmField`, `@JvmStatic`, `@JvmOverloads`, and `@Synchronized` are in the `kotlin.jvm` package which is **not available in commonMain**. This means:

- 185+ files that use these annotations cannot be directly moved to commonMain
- They must be stripped before moving, or replaced with expect/actual patterns
- Removing `@JvmField` changes API for Java callers (field access → getter/setter)
- This is the #1 overlooked blocker for batch commonMain migration

### 2. ExtUtil → SerializationHelpers pattern

The `ExtUtil.read(in, SomeClass::class.java, pf)` pattern uses JVM reflection (`Class<*>`) which doesn't exist in commonMain. The replacement pattern is:

```kotlin
// Before (JVM-only):
val ref = ExtUtil.read(in, TreeReference::class.java, pf) as TreeReference

// After (cross-platform):
val ref = SerializationHelpers.readExternalizable(in, pf) { TreeReference() }
```

The lambda factory `{ TreeReference() }` replaces reflection-based instantiation. Wire format is identical since `readExternalizable` just calls `creator()` then `readExternal()`.

### 3. ExtWrap patterns require dedicated methods

Each `ExtWrap` pattern needs its own SerializationHelpers method because the wire format differs:

| Pattern | SerializationHelpers method |
|---------|---------------------------|
| `ExtWrapNullable(SomeClass::class.java)` | `readNullableExternalizable(in, pf) { SomeClass() }` |
| `ExtWrapNullable(ExtWrapTagged())` | `readNullableTagged(in, pf)` |
| `ExtWrapNullable(String::class.java)` | `readNullableString(in, pf)` |
| `ExtWrapNullable(PlatformDate::class.java)` | `readNullableDate(in)` |
| `ExtWrapList(SomeClass::class.java)` | `readList(in, pf) { SomeClass() }` |
| `ExtWrapList(String::class.java)` | `readStringList(in)` |
| `ExtWrapMap(String::class.java, String::class.java)` | `readStringStringMap(in)` |

### 4. `ExtUtil.writeString` accepts `String?` but `SerializationHelpers.writeString` expects `String`

When converting, nullable string fields must be wrapped with `emptyIfNull()`:

```kotlin
// Before: ExtUtil.writeString(out, nullableField) // accepted null
// After: SerializationHelpers.writeString(out, emptyIfNull(nullableField))
```

### 5. Dependency closure analysis must account for implicit references

Simple import-based analysis misses:
- Same-package class references (no explicit import needed in Kotlin)
- Star imports
- Extension functions and properties

A file may appear "safe" based on its imports but still reference classes in the same package that are JVM-only. The only reliable way to verify is to actually move the file and compile.

### 6. EvaluationContext is the #1 migration blocker

118 files depend on `EvaluationContext`, which itself has complex dependencies (TreeUtilities, DataInstance, IFunctionHandler). Until EvaluationContext can move to commonMain, most of the XPath engine and model code is blocked.

### 7. Batch moves need compilation verification per file

Moving 95 files at once without per-file validation caused a cascade of errors. Better approach:
- Move files in small batches (5-10)
- Compile after each batch
- Revert failures immediately

### 8. `synchronized()` function is JVM-only in Kotlin Common

The `synchronized()` function is not available in commonMain. For code that used it, options are:
- Remove if the synchronization isn't critical (like EvaluationTraceReduction)
- Use `kotlinx.atomicfu` for lock-free alternatives
- Use expect/actual for platform-specific locking

## Migration Statistics After This Wave

| Metric | Before | After |
|--------|--------|-------|
| Files with ExtUtil calls | 88 | 65 |
| Files free of JVM patterns | ~200 | 341 |
| SerializationHelpers methods | 16 | 27 |
| Files in commonMain | 88 | 93 |
