package org.commcare.suite.model

import io.reactivex.Single

/**
 * JVM implementation wrapping io.reactivex.Single.
 * Provides access to the underlying Single for JVM callers that need RxJava APIs
 * (e.g., .test(), .doOnDispose()).
 */
actual class PlatformSingle<T>(val single: Single<T>) {
    actual fun blockingGet(): T = single.blockingGet()
}

actual fun <T> platformSingleFromCallable(callable: () -> T): PlatformSingle<T> {
    return PlatformSingle(Single.fromCallable(callable))
}

actual fun <T> platformSingleJust(value: T): PlatformSingle<T> {
    return PlatformSingle(Single.just(value))
}
