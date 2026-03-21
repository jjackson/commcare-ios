@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.commcare.app.platform

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.Security.errSecSuccess

private const val SERVICE_NAME = "org.commcare.ios"

/**
 * iOS implementation of PlatformKeychainStore.
 * Uses the iOS Security framework Keychain for secure credential storage.
 *
 * Stores items as kSecClassGenericPassword with a fixed service name
 * and per-key account names. Items are accessible only when the device
 * is unlocked and are not included in backups (ThisDeviceOnly).
 */
actual class PlatformKeychainStore actual constructor() {

    actual fun store(key: String, value: String) {
        // Delete any existing item first to avoid errSecDuplicateItem
        delete(key)

        val valueData = NSString.create(string = value)
            .dataUsingEncoding(NSUTF8StringEncoding) ?: return

        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE_NAME,
            kSecAttrAccount to key,
            kSecValueData to valueData,
            kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        )

        @Suppress("UNCHECKED_CAST")
        SecItemAdd(query as CFDictionaryRef, null)
    }

    actual fun retrieve(key: String): String? {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE_NAME,
            kSecAttrAccount to key,
            kSecReturnData to true,
            kSecMatchLimit to kSecMatchLimitOne
        )

        return memScoped {
            val result = alloc<CFTypeRefVar>()

            @Suppress("UNCHECKED_CAST")
            val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)

            if (status != errSecSuccess) {
                return null
            }

            val data = CFBridgingRelease(result.value) as? NSData ?: return null
            NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
        }
    }

    actual fun delete(key: String) {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE_NAME,
            kSecAttrAccount to key
        )

        @Suppress("UNCHECKED_CAST")
        SecItemDelete(query as CFDictionaryRef)
    }
}
