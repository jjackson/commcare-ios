@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.commcare.app.platform

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setValue
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.Security.errSecSuccess
import platform.darwin.OSStatus

private const val SERVICE_NAME = "org.commcare.app.connectid"

/**
 * iOS implementation of PlatformKeychainStore using the iOS Keychain (Security framework).
 * Stores credentials as kSecClassGenericPassword items keyed by service + account.
 */
actual class PlatformKeychainStore actual constructor() {

    actual fun store(key: String, value: String) {
        // Delete any existing item first, then add
        delete(key)

        val valueData = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return

        val query = NSMutableDictionary()
        query.setValue(kSecClassGenericPassword, forKey = kSecClass as String)
        query.setValue(SERVICE_NAME, forKey = kSecAttrService as String)
        query.setValue(key, forKey = kSecAttrAccount as String)
        query.setValue(valueData, forKey = kSecValueData as String)

        @Suppress("UNCHECKED_CAST")
        SecItemAdd(query as platform.CoreFoundation.CFDictionaryRef, null)
    }

    actual fun retrieve(key: String): String? {
        val query = NSMutableDictionary()
        query.setValue(kSecClassGenericPassword, forKey = kSecClass as String)
        query.setValue(SERVICE_NAME, forKey = kSecAttrService as String)
        query.setValue(key, forKey = kSecAttrAccount as String)
        query.setValue(true, forKey = kSecReturnData as String)
        query.setValue(kSecMatchLimitOne, forKey = kSecMatchLimit as String)

        memScoped {
            val result = alloc<CFTypeRefVar>()
            @Suppress("UNCHECKED_CAST")
            val status: OSStatus = SecItemCopyMatching(
                query as platform.CoreFoundation.CFDictionaryRef,
                result.ptr
            )
            if (status == errSecSuccess) {
                val cfData = result.value ?: return null
                val nsData = CFBridgingRelease(cfData) as? NSData ?: return null
                val nsString = NSString.create(data = nsData, encoding = NSUTF8StringEncoding)
                    ?: return null
                // Use toString() instead of direct cast — K/N bridge is unreliable with `as? String`
                return nsString.toString()
            }
        }
        return null
    }

    actual fun delete(key: String) {
        val query = NSMutableDictionary()
        query.setValue(kSecClassGenericPassword, forKey = kSecClass as String)
        query.setValue(SERVICE_NAME, forKey = kSecAttrService as String)
        query.setValue(key, forKey = kSecAttrAccount as String)

        @Suppress("UNCHECKED_CAST")
        SecItemDelete(query as platform.CoreFoundation.CFDictionaryRef)
    }
}
