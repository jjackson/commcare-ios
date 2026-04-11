@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.commcare.app.platform

import cnames.structs.__CFDictionary
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSCopyingProtocol
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

private const val SERVICE_NAME = "org.commcare.ios"

/**
 * iOS implementation of PlatformKeychainStore.
 *
 * Uses NSMutableDictionary + CFBridgingRetain for all Security framework
 * calls. The previous dual-path approach (mapOf-cast vs NSMutableDictionary)
 * caused silent failures: store() would take one path while retrieve() took
 * another, and the K/N bridge differences between the two paths meant items
 * stored via one path were invisible to the other.
 *
 * This version uses a single consistent path (NSMutableDictionary) and
 * kSecAttrAccessibleAfterFirstUnlock (less restrictive than
 * WhenUnlockedThisDeviceOnly, works reliably on simulator).
 *
 * See #389 for the original failure investigation.
 */
actual class PlatformKeychainStore actual constructor() {

    actual fun store(key: String, value: String) {
        delete(key)

        val valueData = NSString.create(string = value)
            .dataUsingEncoding(NSUTF8StringEncoding) ?: return

        val dict = NSMutableDictionary().apply {
            setObj(kSecClassGenericPassword, kSecClass)
            setObj(SERVICE_NAME, kSecAttrService)
            setObj(key, kSecAttrAccount)
            setObj(valueData, kSecValueData)
            setObj(kSecAttrAccessibleAfterFirstUnlock, kSecAttrAccessible)
        }
        val status = withCFDictionary(dict) { cfDict -> SecItemAdd(cfDict, null) }
        if (status != errSecSuccess) {
            // Log for debugging — println goes to Xcode console on real device
            println("[Keychain] store($key) failed: OSStatus=$status")
        }
    }

    actual fun retrieve(key: String): String? {
        return memScoped {
            val result = alloc<CFTypeRefVar>()

            val dict = NSMutableDictionary().apply {
                setObj(kSecClassGenericPassword, kSecClass)
                setObj(SERVICE_NAME, kSecAttrService)
                setObj(key, kSecAttrAccount)
                setObj(NSNumber(bool = true), kSecReturnData)
                setObj(kSecMatchLimitOne, kSecMatchLimit)
            }
            val status = withCFDictionary(dict) { cfDict ->
                SecItemCopyMatching(cfDict, result.ptr)
            }

            if (status != errSecSuccess) {
                return@memScoped null
            }

            val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
            NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
        }
    }

    actual fun delete(key: String) {
        val dict = NSMutableDictionary().apply {
            setObj(kSecClassGenericPassword, kSecClass)
            setObj(SERVICE_NAME, kSecAttrService)
            setObj(key, kSecAttrAccount)
        }
        withCFDictionary(dict) { cfDict -> SecItemDelete(cfDict) }
    }

    // ----- helpers -----

    @Suppress("UNCHECKED_CAST")
    private fun NSMutableDictionary.setObj(value: Any?, key: Any?) {
        if (value == null || key == null) return
        setObject(value, forKey = key as NSCopyingProtocol)
    }

    /**
     * Bridge an NSMutableDictionary to a CFDictionaryRef via
     * CFBridgingRetain + pointer reinterpret, run the block, then
     * release the retain in finally.
     */
    private inline fun <R> withCFDictionary(
        dict: NSMutableDictionary,
        block: (CFDictionaryRef) -> R,
    ): R {
        val retained = CFBridgingRetain(dict)
            ?: throw IllegalStateException("CFBridgingRetain returned null")
        try {
            val cfDict: CFDictionaryRef = retained.reinterpret<__CFDictionary>()
            return block(cfDict)
        } finally {
            CFRelease(retained)
        }
    }
}
