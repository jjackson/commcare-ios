package org.commcare.app.platform

import platform.Foundation.NSUserDefaults
import platform.Foundation.setValue

private const val PREFIX = "commcare_secure_"

/**
 * iOS implementation of PlatformKeychainStore.
 * Uses NSUserDefaults for now — production should use iOS Keychain via Security framework,
 * but the CFStringRef cinterop casts (kSecClass, kSecAttrAccount, etc.) crash with
 * ClassCastException on K/N ("CPointer cannot be cast to kotlin.String").
 *
 * TODO: Replace with proper Keychain implementation when K/N cinterop improves,
 * or use a Swift helper bridged via @ObjCName.
 */
actual class PlatformKeychainStore actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun store(key: String, value: String) {
        defaults.setObject(value, forKey = PREFIX + key)
    }

    actual fun retrieve(key: String): String? {
        return defaults.stringForKey(PREFIX + key)
    }

    actual fun delete(key: String) {
        defaults.removeObjectForKey(PREFIX + key)
    }
}
