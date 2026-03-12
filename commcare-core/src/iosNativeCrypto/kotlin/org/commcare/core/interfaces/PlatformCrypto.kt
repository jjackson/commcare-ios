@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.core.interfaces

import CommCareCrypto.*
import kotlinx.cinterop.*

/**
 * iOS cryptography implementation using CommonCrypto via cinterop wrappers.
 *
 * AES uses GCM mode (iOS 13+). Output format matches JVM:
 * IV (12 bytes) + ciphertext + GCM tag (16 bytes).
 */
actual object PlatformCrypto {
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 16 // 128 bits

    actual fun sha256(data: ByteArray): ByteArray {
        val result = ByteArray(32)
        if (data.isEmpty()) {
            result.usePinned { pinnedResult ->
                cc_sha256(null, 0u, pinnedResult.addressOf(0))
            }
        } else {
            data.usePinned { pinnedData ->
                result.usePinned { pinnedResult ->
                    cc_sha256(pinnedData.addressOf(0), data.size.toUInt(), pinnedResult.addressOf(0))
                }
            }
        }
        return result
    }

    actual fun md5(data: ByteArray): ByteArray {
        val result = ByteArray(16)
        if (data.isEmpty()) {
            result.usePinned { pinnedResult ->
                cc_md5(null, 0u, pinnedResult.addressOf(0))
            }
        } else {
            data.usePinned { pinnedData ->
                result.usePinned { pinnedResult ->
                    cc_md5(pinnedData.addressOf(0), data.size.toUInt(), pinnedResult.addressOf(0))
                }
            }
        }
        return result
    }

    actual fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        if (size > 0) {
            bytes.usePinned { pinned ->
                val status = cc_random_generate(pinned.addressOf(0), size.toULong())
                check(status == 0) { "CCRandomGenerateBytes failed with status $status" }
            }
        }
        return bytes
    }

    actual fun aesEncrypt(data: ByteArray, key: ByteArray): ByteArray {
        // CommonCrypto GCM APIs are SPI (not public). AES-GCM requires either:
        // - CryptoKit via Swift interop (Phase 8 Wave 2+)
        // - Or a pure Kotlin AES-GCM implementation
        TODO("iOS AES-GCM encrypt requires CryptoKit Swift bridge (planned)")
    }

    actual fun aesDecrypt(data: ByteArray, key: ByteArray): ByteArray {
        TODO("iOS AES-GCM decrypt requires CryptoKit Swift bridge (planned)")
    }

    actual fun generateAesKey(bits: Int): ByteArray {
        return randomBytes(bits / 8)
    }
}
