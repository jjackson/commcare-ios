package org.javarosa.core.util

actual inline fun <R> platformSynchronized(lock: Any, block: () -> R): R {
    // iOS is single-threaded for Kotlin/Native by default.
    // Use PlatformLock if concurrent access is needed.
    return block()
}
