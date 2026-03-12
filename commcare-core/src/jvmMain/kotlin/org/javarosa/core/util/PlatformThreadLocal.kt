package org.javarosa.core.util

actual class PlatformThreadLocal<T> actual constructor(private val initialValue: () -> T) {
    private val threadLocal = object : ThreadLocal<T>() {
        override fun initialValue(): T = this@PlatformThreadLocal.initialValue()
    }

    @Suppress("UNCHECKED_CAST")
    actual fun get(): T = threadLocal.get() as T
    actual fun set(value: T) = threadLocal.set(value)
}
