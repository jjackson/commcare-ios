@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.commcare.app.platform

import cnames.structs.__CFDictionary
import kotlinx.cinterop.CPointer
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
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
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
 * Uses the iOS Security framework Keychain for secure credential storage.
 *
 * Stores items as kSecClassGenericPassword with a fixed service name
 * and per-key account names. Items are accessible only when the device
 * is unlocked and are not included in backups (ThisDeviceOnly).
 *
 * Implementation notes (see issue #385):
 *
 * 1. Queries are built with NSMutableDictionary rather than Kotlin
 *    mapOf + "as CFDictionaryRef" cast. The implicit Map-to-CFDictionary
 *    bridging in Kotlin/Native crashes with an NSMapGet NULL assertion
 *    on iOS simulator in certain type-cache edge cases.
 *
 * 2. Bridging NSMutableDictionary to CFDictionaryRef goes through
 *    CFBridgingRetain + pointer reinterpret, not a direct Kotlin
 *    "as" cast. Kotlin's type system sees NSMutableDictionary as an
 *    Obj-C object, not a CPointer, so a direct cast raises
 *    ClassCastException. CFBridgingRetain returns a +1 retained
 *    CFTypeRef which we then reinterpret as CFDictionaryRef and
 *    release after the SecItem call.
 *
 * 3. CFStringRef constants (kSecClass, kSecAttrService, etc.) are
 *    cast to NSCopyingProtocol when passed as setObject:forKey: keys.
 *    CFStringRef is toll-free bridged with NSString, which implements
 *    NSCopying, so the cast succeeds at runtime.
 */
actual class PlatformKeychainStore actual constructor() {

    actual fun store(key: String, value: String) {
        // Delete any existing item first to avoid errSecDuplicateItem
        delete(key)

        val valueData = NSString.create(string = value)
            .dataUsingEncoding(NSUTF8StringEncoding) ?: return

        val query = buildQuery {
            setObject(kSecClassGenericPassword, forKey = kSecClass.asNSCopying())
            setObject(SERVICE_NAME, forKey = kSecAttrService.asNSCopying())
            setObject(key, forKey = kSecAttrAccount.asNSCopying())
            setObject(valueData, forKey = kSecValueData.asNSCopying())
            setObject(
                kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                forKey = kSecAttrAccessible.asNSCopying(),
            )
        }

        withCFDictionary(query) { cfQuery ->
            SecItemAdd(cfQuery, null)
        }
    }

    actual fun retrieve(key: String): String? {
        val query = buildQuery {
            setObject(kSecClassGenericPassword, forKey = kSecClass.asNSCopying())
            setObject(SERVICE_NAME, forKey = kSecAttrService.asNSCopying())
            setObject(key, forKey = kSecAttrAccount.asNSCopying())
            setObject(NSNumber(bool = true), forKey = kSecReturnData.asNSCopying())
            setObject(kSecMatchLimitOne, forKey = kSecMatchLimit.asNSCopying())
        }

        return memScoped {
            val result = alloc<CFTypeRefVar>()

            val status = withCFDictionary(query) { cfQuery ->
                SecItemCopyMatching(cfQuery, result.ptr)
            }

            if (status != errSecSuccess) {
                return null
            }

            val data = CFBridgingRelease(result.value) as? NSData ?: return null
            NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
        }
    }

    actual fun delete(key: String) {
        val query = buildQuery {
            setObject(kSecClassGenericPassword, forKey = kSecClass.asNSCopying())
            setObject(SERVICE_NAME, forKey = kSecAttrService.asNSCopying())
            setObject(key, forKey = kSecAttrAccount.asNSCopying())
        }

        withCFDictionary(query) { cfQuery ->
            SecItemDelete(cfQuery)
        }
    }

    // ----- helpers -----

    private inline fun buildQuery(block: NSMutableDictionary.() -> Unit): NSMutableDictionary {
        return NSMutableDictionary().apply(block)
    }

    /**
     * Bridge an NSMutableDictionary to a CFDictionaryRef for passing to
     * Security framework APIs. Uses CFBridgingRetain to get a +1 retained
     * CFTypeRef, reinterprets it as CFDictionaryRef, runs the block, then
     * releases the retain.
     */
    private inline fun <R> withCFDictionary(
        dict: NSMutableDictionary,
        block: (CFDictionaryRef) -> R,
    ): R {
        val retained = CFBridgingRetain(dict)
            ?: throw IllegalStateException("CFBridgingRetain returned null for NSMutableDictionary")
        try {
            val cfDict: CFDictionaryRef = retained.reinterpret<__CFDictionary>()
            return block(cfDict)
        } finally {
            CFRelease(retained)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asNSCopying(): NSCopyingProtocol = this as NSCopyingProtocol
}
