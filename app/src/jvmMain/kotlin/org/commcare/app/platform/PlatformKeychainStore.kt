package org.commcare.app.platform

/**
 * JVM stub for keychain storage — uses an in-memory map.
 * Not persistent across restarts; suitable for development/testing only.
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
