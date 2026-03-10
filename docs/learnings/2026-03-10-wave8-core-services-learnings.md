# Wave 8: CommCare Core Services Learnings

**Date:** 2026-03-10
**Wave:** 8 (commcare-core-services, 71 files)
**PR:** #29

## New Patterns

### 1. `@JvmField protected` for cross-source-set Java subclasses

Kotlin `protected val/var` generates a private backing field + protected getter/setter. Java subclasses in other Gradle source sets (cli, test) that access `super.field` directly will fail because the field is private in bytecode.

**Fix:** Add `@JvmField` to expose the field directly:
```kotlin
@JvmField
protected val mPlatform: CommCarePlatform?

@JvmField
protected var caseParser: TransactionParserFactory? = null
```

This is distinct from the `@JvmField` + `open` incompatibility (checklist item 12) — here the properties are not `open`.

### 2. OkHttp 4 Kotlin property syntax

OkHttp 4 deprecated Java-style method accessors in favor of Kotlin properties:
- `response.code()` → `response.code`
- `request.url()` → `request.url`
- `url.scheme()` → `url.scheme`
- `url.host()` → `url.host`
- `HttpUrl.parse(str)` → `str.toHttpUrlOrNull()`

### 3. Okio 2 extension functions

Okio 2 moved factory methods to extension functions:
- `Okio.buffer(Okio.source(stream))` → `stream.source().buffer()`

### 4. `const val` requires compile-time constants

`const val` in companion objects requires a compile-time constant initializer. Method calls like `TimeUnit.MINUTES.toMillis(2)` are not constant. Use `@JvmField val` instead:
```kotlin
@JvmField val CONNECTION_TIMEOUT = TimeUnit.MINUTES.toMillis(2)
```

### 5. Kotlin interfaces are not SAM types for Kotlin callers

When a Kotlin interface has a single abstract method, Kotlin callers cannot pass a lambda — only Java callers can use SAM conversion with Kotlin interfaces. Kotlin callers must use explicit `object : InterfaceName { ... }` syntax.

## Reinforced Patterns

- Property getter/setter clash (checklist item 17/20): `var enforceSecureEndpoint` + explicit `fun setEnforceSecureEndpoint()` — remove the explicit method
- Nullable parameters from Java (checklist item 1): `CaseIndexTable?` needed because test passes null
