package org.commcare.app.platform

/**
 * Platform-specific secure credential storage.
 * On iOS: uses Keychain (via Security framework).
 * On JVM: in-memory map (for testing only).
 */
expect class PlatformKeychainStore() {
    fun store(key: String, value: String)
    fun retrieve(key: String): String?
    fun delete(key: String)
}
