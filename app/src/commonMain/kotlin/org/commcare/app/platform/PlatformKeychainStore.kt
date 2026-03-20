package org.commcare.app.platform

/**
 * Platform-specific secure key/value storage.
 * iOS: Keychain Services, JVM: in-memory map (dev stub).
 */
expect class PlatformKeychainStore() {
    /**
     * Store a value securely under the given key.
     */
    fun store(key: String, value: String)

    /**
     * Retrieve a previously stored value, or null if not found.
     */
    fun retrieve(key: String): String?

    /**
     * Delete a stored value.
     */
    fun delete(key: String)
}
