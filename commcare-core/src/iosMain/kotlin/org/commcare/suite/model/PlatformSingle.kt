package org.commcare.suite.model

/**
 * iOS implementation: simple synchronous wrapper since badge computation
 * doesn't need reactive streams on iOS.
 */
actual class PlatformSingle<T>(private val callable: () -> T) {
    actual fun blockingGet(): T = callable()
}

actual fun <T> platformSingleFromCallable(callable: () -> T): PlatformSingle<T> {
    return PlatformSingle(callable)
}

actual fun <T> platformSingleJust(value: T): PlatformSingle<T> {
    return PlatformSingle { value }
}
