@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.core.interfaces

import kotlinx.cinterop.*
import platform.CommonCrypto.*
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

/**
 * iOS cryptography implementation using CommonCrypto and Security frameworks.
 *
 * AES uses GCM mode via CCCryptorGCMOneshotEncrypt/Decrypt (iOS 13+).
 * Output format matches JVM: IV (12 bytes) + ciphertext + GCM tag (16 bytes).
 */
actual object PlatformCrypto {
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 16 // 128 bits
    private const val SHA256_DIGEST_LENGTH = 32
    private const val MD5_DIGEST_LENGTH = 16

    actual fun sha256(data: ByteArray): ByteArray {
        val result = ByteArray(SHA256_DIGEST_LENGTH)
        if (data.isEmpty()) {
            result.usePinned { pinnedResult ->
                CC_SHA256(null, 0u, pinnedResult.addressOf(0).reinterpret())
            }
        } else {
            data.usePinned { pinnedData ->
                result.usePinned { pinnedResult ->
                    CC_SHA256(
                        pinnedData.addressOf(0),
                        data.size.toUInt(),
                        pinnedResult.addressOf(0).reinterpret()
                    )
                }
            }
        }
        return result
    }

    actual fun md5(data: ByteArray): ByteArray {
        val result = ByteArray(MD5_DIGEST_LENGTH)
        if (data.isEmpty()) {
            result.usePinned { pinnedResult ->
                CC_MD5(null, 0u, pinnedResult.addressOf(0).reinterpret())
            }
        } else {
            data.usePinned { pinnedData ->
                result.usePinned { pinnedResult ->
                    CC_MD5(
                        pinnedData.addressOf(0),
                        data.size.toUInt(),
                        pinnedResult.addressOf(0).reinterpret()
                    )
                }
            }
        }
        return result
    }

    actual fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        if (size > 0) {
            bytes.usePinned { pinned ->
                val status = SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0))
                check(status == 0) { "SecRandomCopyBytes failed with status $status" }
            }
        }
        return bytes
    }

    actual fun aesEncrypt(data: ByteArray, key: ByteArray): ByteArray {
        val iv = randomBytes(GCM_IV_LENGTH)
        val ciphertext = ByteArray(data.size)
        val tag = ByteArray(GCM_TAG_LENGTH)

        key.usePinned { pinnedKey ->
            iv.usePinned { pinnedIv ->
                tag.usePinned { pinnedTag ->
                    if (data.isEmpty()) {
                        val status = CCCryptorGCMOneshotEncrypt(
                            kCCAlgorithmAES,
                            pinnedKey.addressOf(0),
                            key.size.toULong(),
                            pinnedIv.addressOf(0),
                            GCM_IV_LENGTH.toULong(),
                            null, 0u, // no AAD
                            null, 0u, // no input data
                            null, // no output
                            pinnedTag.addressOf(0),
                            GCM_TAG_LENGTH.toULong()
                        )
                        check(status == kCCSuccess) { "AES-GCM encrypt failed: $status" }
                    } else {
                        data.usePinned { pinnedData ->
                            ciphertext.usePinned { pinnedCipher ->
                                val status = CCCryptorGCMOneshotEncrypt(
                                    kCCAlgorithmAES,
                                    pinnedKey.addressOf(0),
                                    key.size.toULong(),
                                    pinnedIv.addressOf(0),
                                    GCM_IV_LENGTH.toULong(),
                                    null, 0u, // no AAD
                                    pinnedData.addressOf(0),
                                    data.size.toULong(),
                                    pinnedCipher.addressOf(0),
                                    pinnedTag.addressOf(0),
                                    GCM_TAG_LENGTH.toULong()
                                )
                                check(status == kCCSuccess) { "AES-GCM encrypt failed: $status" }
                            }
                        }
                    }
                }
            }
        }

        // Format matches JVM: IV + ciphertext + tag
        return iv + ciphertext + tag
    }

    actual fun aesDecrypt(data: ByteArray, key: ByteArray): ByteArray {
        require(data.size >= GCM_IV_LENGTH + GCM_TAG_LENGTH) {
            "Ciphertext too short for AES-GCM (need at least ${GCM_IV_LENGTH + GCM_TAG_LENGTH} bytes)"
        }

        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size - GCM_TAG_LENGTH)
        val tag = data.copyOfRange(data.size - GCM_TAG_LENGTH, data.size)
        val plaintext = ByteArray(ciphertext.size)

        key.usePinned { pinnedKey ->
            iv.usePinned { pinnedIv ->
                tag.usePinned { pinnedTag ->
                    if (ciphertext.isEmpty()) {
                        val status = CCCryptorGCMOneshotDecrypt(
                            kCCAlgorithmAES,
                            pinnedKey.addressOf(0),
                            key.size.toULong(),
                            pinnedIv.addressOf(0),
                            GCM_IV_LENGTH.toULong(),
                            null, 0u, // no AAD
                            null, 0u, // no ciphertext
                            null, // no output
                            pinnedTag.addressOf(0),
                            GCM_TAG_LENGTH.toULong()
                        )
                        check(status == kCCSuccess) { "AES-GCM decrypt failed: $status" }
                    } else {
                        ciphertext.usePinned { pinnedCipher ->
                            plaintext.usePinned { pinnedPlain ->
                                val status = CCCryptorGCMOneshotDecrypt(
                                    kCCAlgorithmAES,
                                    pinnedKey.addressOf(0),
                                    key.size.toULong(),
                                    pinnedIv.addressOf(0),
                                    GCM_IV_LENGTH.toULong(),
                                    null, 0u, // no AAD
                                    pinnedCipher.addressOf(0),
                                    ciphertext.size.toULong(),
                                    pinnedPlain.addressOf(0),
                                    pinnedTag.addressOf(0),
                                    GCM_TAG_LENGTH.toULong()
                                )
                                check(status == kCCSuccess) { "AES-GCM decrypt failed: $status" }
                            }
                        }
                    }
                }
            }
        }

        return plaintext
    }

    actual fun generateAesKey(bits: Int): ByteArray {
        return randomBytes(bits / 8)
    }
}
