@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.app.platform

import platform.Foundation.NSUserDefaults

/**
 * iOS keychain store backed by NSUserDefaults for the dev phase.
 *
 * Production hardening note: values should be stored in iOS Keychain Services
 * (SecItemAdd / SecItemCopyMatching) for proper at-rest encryption and device
 * binding. NSUserDefaults is used here because Keychain cinterop requires
 * additional entitlements in the Xcode project that are deferred to the
 * App Store preparation wave.
 */
actual class PlatformKeychainStore actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun store(key: String, value: String) {
        defaults.setObject(value, forKey = prefixedKey(key))
    }

    actual fun retrieve(key: String): String? =
        defaults.stringForKey(prefixedKey(key))

    actual fun delete(key: String) {
        defaults.removeObjectForKey(prefixedKey(key))
    }

    private fun prefixedKey(key: String) = "commcare_keychain_$key"
}
