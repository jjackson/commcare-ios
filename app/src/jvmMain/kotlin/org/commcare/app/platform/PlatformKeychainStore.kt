package org.commcare.app.platform

/**
 * JVM implementation of PlatformKeychainStore — simple in-memory map.
 * JVM is for testing only; no real secure storage needed.
 */
actual class PlatformKeychainStore actual constructor() {
    private val store = mutableMapOf<String, String>()

    actual fun store(key: String, value: String) {
        store[key] = value
    }

    actual fun retrieve(key: String): String? = store[key]

    actual fun delete(key: String) {
        store.remove(key)
    }
}
