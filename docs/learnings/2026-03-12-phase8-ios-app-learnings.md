# Phase 8: iOS App Implementation Learnings

**Date**: 2026-03-12

## 1. NSURLSession Synchronous Pattern

The `PlatformHttpClient` interface requires synchronous `execute()`. On iOS, `NSURLSession` is callback-based. The solution uses `dispatch_semaphore_t`:

```kotlin
val semaphore = dispatch_semaphore_create(0)
val task = session.dataTaskWithRequest(request) { data, response, error ->
    // capture results
    dispatch_semaphore_signal(semaphore)
}
task.resume()
dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER)
```

**Caveat**: This blocks the calling thread. Must never call from the main/UI thread.

## 2. NSJSONSerialization Over Regex

Initial iOS JSON parser used regex (`"key"\s*:\s*"value"`). This broke on:
- Nested objects
- Arrays of strings
- Escaped quotes
- Numeric values

`NSJSONSerialization.JSONObjectWithData()` handles all these correctly and is available in Foundation.

## 3. Git Branch Checkout from Subdirectories

When working directory is inside a subdirectory (e.g., `commcare-core/`), `git checkout -b branch-name` may appear to work but subsequent commits can end up on the wrong branch. Always verify with `git branch --show-current` after checkout, or use absolute paths.

## 4. Case API Nullability

In KMP commonMain, `Case.getCaseId()`, `Case.getName()`, and `Case.getTypeId()` all return `String?`. This differs from the JVM-only behavior where Java interop masked nullability. Always use null-safety operators when consuming Case API from Kotlin.

## 5. Text.evaluate() Without EvaluationContext

`Text.evaluate()` works without an `EvaluationContext` parameter for:
- Flat text (hardcoded strings)
- Locale text (resolved from current locale)

It only needs `EvaluationContext` for xpath-based text that references instance data. Menu labels are typically flat/locale text, so this simplification works for menu navigation.

## 6. IStorageUtilityIndexed vs DummyIndexedStorageUtility

`DummyIndexedStorageUtility` couldn't be moved to commonMain because its constructor takes `Class<T>`, used by 10+ Java callers. Creating a separate `IosInMemoryStorage` in iosMain that takes `KClass<*>` + factory lambda was the pragmatic solution. Both implement the same `IStorageUtilityIndexed<T>` interface.

## 7. IndexedFixtureIdentifier Constructor

`IndexedFixtureIdentifier(baseName, childName, rootAttributes)` — the third parameter is `ByteArray?` (serialized attributes), NOT `TreeElement`. Pass `null` if no serialized attributes are available. The JVM `MockUserDataSandbox` follows the same pattern.
