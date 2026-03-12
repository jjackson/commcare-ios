package org.javarosa.core.util

actual class PlatformThreadLocal<T> actual constructor(private val initialValue: () -> T) {
    private var value: T = initialValue()

    actual fun get(): T = value
    actual fun set(value: T) {
        this.value = value
    }
}
