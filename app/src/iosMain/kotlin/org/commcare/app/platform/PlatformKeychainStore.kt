package org.commcare.app.platform

import platform.Foundation.NSString
import platform.Foundation.NSUserDefaults
import platform.Foundation.create

private const val PREFIX = "commcare_secure_"

/**
 * iOS implementation of PlatformKeychainStore.
 * Uses NSUserDefaults with explicit NSString bridging to avoid K/N CPointer cast issues.
 *
 * TODO: Replace with proper Keychain via Swift helper for App Store builds.
 */
actual class PlatformKeychainStore actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun store(key: String, value: String) {
        // Explicitly create NSString to ensure proper bridging
        val nsKey = NSString.create(string = PREFIX + key)
        val nsValue = NSString.create(string = value)
        defaults.setObject(nsValue, forKey = nsKey.toString())
        defaults.synchronize()
    }

    actual fun retrieve(key: String): String? {
        val nsKey = NSString.create(string = PREFIX + key)
        val obj = defaults.objectForKey(nsKey.toString()) ?: return null
        return obj.toString()
    }

    actual fun delete(key: String) {
        val nsKey = NSString.create(string = PREFIX + key)
        defaults.removeObjectForKey(nsKey.toString())
        defaults.synchronize()
    }
}
