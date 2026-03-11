package org.commcare.suite.model

/**
 * Platform abstraction for a deferred computation that produces a single value.
 * On JVM: typealias to io.reactivex.Single<T> (has blockingGet() natively).
 * On iOS: simple synchronous wrapper with blockingGet().
 */
expect class PlatformSingle<T> {
    fun blockingGet(): T
}

/**
 * Create a PlatformSingle from a callable block.
 */
expect fun <T> platformSingleFromCallable(callable: () -> T): PlatformSingle<T>

/**
 * Create a PlatformSingle that immediately returns a value.
 */
expect fun <T> platformSingleJust(value: T): PlatformSingle<T>
