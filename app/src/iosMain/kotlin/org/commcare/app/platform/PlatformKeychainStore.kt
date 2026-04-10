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
 *
 * ## Bridging dance (see issue #385)
 *
 * K/N interop between `Map<Any?, Any?>` and `CFDictionaryRef` behaves
 * differently across runtime contexts:
 *
 * - **Standalone test context** (`xcrun simctl spawn --standalone`):
 *   `mapOf(...) as CFDictionaryRef` works via implicit bridging.
 * - **App context** (Compose onClick handler): the same cast throws
 *   `ClassCastException: HashMap cannot be cast to CPointer`.
 *
 * And the `NSMutableDictionary + CFBridgingRetain + reinterpret` path
 * works in the app context but causes `SecItemAdd` to return
 * `errSecParam (-50)` in the standalone test context.
 *
 * Dual-path solution: try `mapOf` first; if `ClassCastException` is
 * thrown, fall back to `NSMutableDictionary`. Each runtime context
 * takes the path that works there.
 */
actual class PlatformKeychainStore actual constructor() {

    actual fun store(key: String, value: String) {
        delete(key)

        val valueData = NSString.create(string = value)
            .dataUsingEncoding(NSUTF8StringEncoding) ?: return

        val status = try {
            val query = mapOf<Any?, Any?>(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to SERVICE_NAME,
                kSecAttrAccount to key,
                kSecValueData to valueData,
                kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            )

            @Suppress("UNCHECKED_CAST")
            SecItemAdd(query as CFDictionaryRef, null)
        } catch (_: ClassCastException) {
            val dict = buildNSMutableDictionary {
                setObj(kSecClassGenericPassword, kSecClass)
                setObj(SERVICE_NAME, kSecAttrService)
                setObj(key, kSecAttrAccount)
                setObj(valueData, kSecValueData)
                setObj(kSecAttrAccessibleWhenUnlockedThisDeviceOnly, kSecAttrAccessible)
            }
            withCFDictionary(dict) { cfDict -> SecItemAdd(cfDict, null) }
        }
        if (status != errSecSuccess) {
            println("[Keychain] store($key) failed with OSStatus=$status")
        }
    }

    actual fun retrieve(key: String): String? {
        return memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = try {
                val query = mapOf<Any?, Any?>(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrService to SERVICE_NAME,
                    kSecAttrAccount to key,
                    kSecReturnData to true,
                    kSecMatchLimit to kSecMatchLimitOne
                )

                @Suppress("UNCHECKED_CAST")
                SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
            } catch (_: ClassCastException) {
                val dict = buildNSMutableDictionary {
                    setObj(kSecClassGenericPassword, kSecClass)
                    setObj(SERVICE_NAME, kSecAttrService)
                    setObj(key, kSecAttrAccount)
                    setObj(NSNumber(bool = true), kSecReturnData)
                    setObj(kSecMatchLimitOne, kSecMatchLimit)
                }
                withCFDictionary(dict) { cfDict ->
                    SecItemCopyMatching(cfDict, result.ptr)
                }
            }

            if (status != errSecSuccess) {
                return@memScoped null
            }

            val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
            NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
        }
    }

    actual fun delete(key: String) {
        try {
            val query = mapOf<Any?, Any?>(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to SERVICE_NAME,
                kSecAttrAccount to key
            )

            @Suppress("UNCHECKED_CAST")
            SecItemDelete(query as CFDictionaryRef)
        } catch (_: ClassCastException) {
            val dict = buildNSMutableDictionary {
                setObj(kSecClassGenericPassword, kSecClass)
                setObj(SERVICE_NAME, kSecAttrService)
                setObj(key, kSecAttrAccount)
            }
            withCFDictionary(dict) { cfDict -> SecItemDelete(cfDict) }
        }
    }

    // ----- helpers -----

    private inline fun buildNSMutableDictionary(
        block: NSMutableDictionary.() -> Unit,
    ): NSMutableDictionary = NSMutableDictionary().apply(block)

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
