package org.javarosa.core.util

import kotlin.concurrent.AtomicReference
import kotlin.native.concurrent.ObsoleteWorkersApi
import platform.Foundation.NSThread

actual class PlatformThreadLocal<T> actual constructor(private val initialValue: () -> T) {
    private val threadValues = AtomicReference<HashMap<String, @UnsafeVariance T>>(HashMap())

    private fun threadKey(): String = NSThread.currentThread.description ?: "main"

    actual fun get(): T {
        val key = threadKey()
        val map = threadValues.value
        @Suppress("UNCHECKED_CAST")
        return map[key] ?: initialValue().also { set(it) }
    }

    actual fun set(value: T) {
        val key = threadKey()
        val newMap = HashMap(threadValues.value)
        newMap[key] = value
        threadValues.value = newMap
    }
}
