# Phase 6: Deep Migration Learnings

## PlatformLock.isLocked with typealias

When adding members to an expect class whose JVM actual is a `typealias`, the new member must be an **extension** (not a class member) because the typealias target class doesn't have the matching member.

```kotlin
// commonMain - extension property, NOT class member
expect class PlatformLock() {
    fun lock()
    fun unlock()
}
expect val PlatformLock.isLocked: Boolean

// jvmMain - ReentrantLock already has isLocked, but extension avoids recursion
actual typealias PlatformLock = java.util.concurrent.locks.ReentrantLock
// Helper to avoid infinite recursion (extension shadows member in getter body)
private fun ReentrantLock.checkLocked(): Boolean = isLocked
actual val PlatformLock.isLocked: Boolean get() = this.checkLocked()

// iosMain - extension accesses internal state
actual class PlatformLock {
    internal var _locked = false
    actual fun lock() { lock.lock(); _locked = true }
    actual fun unlock() { _locked = false; lock.unlock() }
}
actual val PlatformLock.isLocked: Boolean get() = this._locked
```

## Composition pattern for KMP (SizeBoundVector)

JVM-final classes like `ArrayList` can't be subclassed in KMP commonMain. Use **composition + interface delegation**:

```kotlin
// Instead of: class SizeBoundVector<E> : ArrayList<E>()
// Use: class SizeBoundVector<E> : MutableList<E> {
//     private val backingList = ArrayList<E>()
//     // delegate all MutableList methods
// }
```

## @Throws filter mismatch in iOS builds

When moving files to commonMain, override methods must have EXACTLY matching `@Throws` annotations as their parent class. JVM compilation is lenient (allows subset), but KMP metadata compilation (used for iOS target) is strict. This manifests only in iOS CI, not in JVM tests.

## Iterative compiler-validated bulk migration

The best approach for moving files to commonMain:
1. Copy ALL candidate files to commonMain
2. Run `compileCommonMainKotlinMetadata`
3. Move back files mentioned in error messages
4. Repeat until clean build (typically 3-6 iterations)

Key insight: errors cascade — fixing iteration 1 reveals iteration 2 errors that were masked. Each iteration peels back one layer of the dependency onion.

## Cascade ceiling analysis

After Phase 6, the remaining ~400 files form ONE tightly-coupled connected component blocked by ~16 "inner ring" files:

**Direct JVM dependency files (can't move without abstraction):**
- kxml2/xmlpull consumers (7 files) — need DOM abstraction
- gavaghan geodesy (2 files) — external library
- com.carrotsearch.hppc (1 file: TableBuilder)
- java.io.Reader/Writer consumers (5 files)
- Class<*> serialization (PrototypeFactory constructors, Hasher)

**Cascade structure:**
- `Element` (kxml2 DOM) = #1 unresolved reference (100 occurrences)
- `EvaluationContext` = most-imported type (163 files reference it)
- `TreeReference` = 82 importers, blocked by XPathExpression → EvaluationContext
- Moving ANY single blocker doesn't help — the cluster reconnects through other paths

**Implication:** Bulk migration ceiling requires either DOM abstraction for kxml2 or moving parser/serializer files to jvmMain to break the dependency cycle.

## KMP String/ByteArray API differences

Common substitutions for commonMain compatibility:
- `String.toByteArray()` → `String.encodeToByteArray()`
- `String(ByteArray)` → `ByteArray.decodeToString()`
- `String(ByteArray, offset, length)` → `ByteArray.decodeToString(offset, offset + length)`
- `System.arraycopy(src, srcOff, dst, dstOff, len)` → `src.copyInto(dst, dstOff, srcOff, srcOff + len)`
