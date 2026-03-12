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
        val iv = randomBytes(GCM_IV_LENGTH)
        val ciphertext = ByteArray(data.size)
        val tag = ByteArray(GCM_TAG_LENGTH)

        key.usePinned { pinnedKey ->
            iv.usePinned { pinnedIv ->
                tag.usePinned { pinnedTag ->
                    if (data.isEmpty()) {
                        val status = cc_gcm_encrypt(
                            pinnedKey.addressOf(0), key.size.toULong(),
                            pinnedIv.addressOf(0), GCM_IV_LENGTH.toULong(),
                            null, 0u,
                            null,
                            pinnedTag.addressOf(0), GCM_TAG_LENGTH.toULong()
                        )
                        check(status == 0) { "AES-GCM encrypt failed: $status" }
                    } else {
                        data.usePinned { pinnedData ->
                            ciphertext.usePinned { pinnedCipher ->
                                val status = cc_gcm_encrypt(
                                    pinnedKey.addressOf(0), key.size.toULong(),
                                    pinnedIv.addressOf(0), GCM_IV_LENGTH.toULong(),
                                    pinnedData.addressOf(0), data.size.toULong(),
                                    pinnedCipher.addressOf(0),
                                    pinnedTag.addressOf(0), GCM_TAG_LENGTH.toULong()
                                )
                                check(status == 0) { "AES-GCM encrypt failed: $status" }
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
                        val status = cc_gcm_decrypt(
                            pinnedKey.addressOf(0), key.size.toULong(),
                            pinnedIv.addressOf(0), GCM_IV_LENGTH.toULong(),
                            null, 0u,
                            null,
                            pinnedTag.addressOf(0), GCM_TAG_LENGTH.toULong()
                        )
                        check(status == 0) { "AES-GCM decrypt failed: $status" }
                    } else {
                        ciphertext.usePinned { pinnedCipher ->
                            plaintext.usePinned { pinnedPlain ->
                                val status = cc_gcm_decrypt(
                                    pinnedKey.addressOf(0), key.size.toULong(),
                                    pinnedIv.addressOf(0), GCM_IV_LENGTH.toULong(),
                                    pinnedCipher.addressOf(0), ciphertext.size.toULong(),
                                    pinnedPlain.addressOf(0),
                                    pinnedTag.addressOf(0), GCM_TAG_LENGTH.toULong()
                                )
                                check(status == 0) { "AES-GCM decrypt failed: $status" }
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
