@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.core.interfaces

import CommCareCrypto.*
import kotlinx.cinterop.*

/**
 * iOS cryptography implementation using CommonCrypto via cinterop wrappers.
 *
 * AES uses CBC mode with PKCS7 padding + HMAC-SHA256 (Encrypt-then-MAC)
 * since CommonCrypto's GCM APIs are SPI (not public). This provides
 * authenticated encryption equivalent to AES-GCM.
 *
 * Output format: IV (16 bytes) || ciphertext || HMAC-SHA256 (32 bytes)
 *
 * The HMAC covers IV + ciphertext to prevent tampering.
 */
actual object PlatformCrypto {
    private const val CBC_IV_LENGTH = 16
    private const val HMAC_LENGTH = 32
    private const val CBC_BLOCK_SIZE = 16

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
        require(key.size == 16 || key.size == 24 || key.size == 32) {
            "AES key must be 16, 24, or 32 bytes (got ${key.size})"
        }

        val iv = randomBytes(CBC_IV_LENGTH)
        // Max output: input + one block of padding
        val maxOutputLen = data.size + CBC_BLOCK_SIZE
        val ciphertext = ByteArray(maxOutputLen)

        val actualLen = memScoped {
            val outLen = alloc<UIntVar>()
            key.usePinned { pinnedKey ->
                iv.usePinned { pinnedIv ->
                    ciphertext.usePinned { pinnedOut ->
                        if (data.isEmpty()) {
                            val status = cc_aes_cbc_encrypt(
                                pinnedKey.addressOf(0), key.size.toUInt(),
                                pinnedIv.addressOf(0),
                                null, 0u,
                                pinnedOut.addressOf(0), maxOutputLen.toUInt(),
                                outLen.ptr
                            )
                            check(status == 0) { "AES-CBC encrypt failed with status $status" }
                        } else {
                            data.usePinned { pinnedData ->
                                val status = cc_aes_cbc_encrypt(
                                    pinnedKey.addressOf(0), key.size.toUInt(),
                                    pinnedIv.addressOf(0),
                                    pinnedData.addressOf(0), data.size.toUInt(),
                                    pinnedOut.addressOf(0), maxOutputLen.toUInt(),
                                    outLen.ptr
                                )
                                check(status == 0) { "AES-CBC encrypt failed with status $status" }
                            }
                        }
                    }
                }
            }
            outLen.value.toInt()
        }

        val trimmedCiphertext = ciphertext.copyOf(actualLen)
        // Compute HMAC over IV + ciphertext
        val ivAndCiphertext = iv + trimmedCiphertext
        val hmac = hmacSha256(key, ivAndCiphertext)

        return iv + trimmedCiphertext + hmac
    }

    actual fun aesDecrypt(data: ByteArray, key: ByteArray): ByteArray {
        require(key.size == 16 || key.size == 24 || key.size == 32) {
            "AES key must be 16, 24, or 32 bytes (got ${key.size})"
        }
        require(data.size >= CBC_IV_LENGTH + HMAC_LENGTH) {
            "Encrypted data too short"
        }

        val iv = data.copyOfRange(0, CBC_IV_LENGTH)
        val ciphertext = data.copyOfRange(CBC_IV_LENGTH, data.size - HMAC_LENGTH)
        val storedHmac = data.copyOfRange(data.size - HMAC_LENGTH, data.size)

        // Verify HMAC before decrypting (Encrypt-then-MAC)
        val ivAndCiphertext = iv + ciphertext
        val computedHmac = hmacSha256(key, ivAndCiphertext)
        require(storedHmac.contentEquals(computedHmac)) { "HMAC verification failed — data may be tampered" }

        val maxOutputLen = ciphertext.size
        val plaintext = ByteArray(maxOutputLen)

        val actualLen = memScoped {
            val outLen = alloc<UIntVar>()
            key.usePinned { pinnedKey ->
                iv.usePinned { pinnedIv ->
                    plaintext.usePinned { pinnedOut ->
                        if (ciphertext.isEmpty()) {
                            val status = cc_aes_cbc_decrypt(
                                pinnedKey.addressOf(0), key.size.toUInt(),
                                pinnedIv.addressOf(0),
                                null, 0u,
                                pinnedOut.addressOf(0), maxOutputLen.toUInt(),
                                outLen.ptr
                            )
                            check(status == 0) { "AES-CBC decrypt failed with status $status" }
                        } else {
                            ciphertext.usePinned { pinnedCipher ->
                                val status = cc_aes_cbc_decrypt(
                                    pinnedKey.addressOf(0), key.size.toUInt(),
                                    pinnedIv.addressOf(0),
                                    pinnedCipher.addressOf(0), ciphertext.size.toUInt(),
                                    pinnedOut.addressOf(0), maxOutputLen.toUInt(),
                                    outLen.ptr
                                )
                                check(status == 0) { "AES-CBC decrypt failed with status $status" }
                            }
                        }
                    }
                }
            }
            outLen.value.toInt()
        }

        return plaintext.copyOf(actualLen)
    }

    actual fun generateAesKey(bits: Int): ByteArray {
        return randomBytes(bits / 8)
    }

    actual fun pbkdf2(password: String, salt: ByteArray, iterations: Int, keyLengthBytes: Int): ByteArray {
        val derivedKey = ByteArray(keyLengthBytes)
        val passwordBytes = password.encodeToByteArray()

        passwordBytes.usePinned { pinnedPassword ->
            salt.usePinned { pinnedSalt ->
                derivedKey.usePinned { pinnedKey ->
                    val status = cc_pbkdf2_sha256(
                        pinnedPassword.addressOf(0).reinterpret(),
                        passwordBytes.size.toUInt(),
                        pinnedSalt.addressOf(0), salt.size.toUInt(),
                        iterations.toUInt(),
                        pinnedKey.addressOf(0), keyLengthBytes.toUInt()
                    )
                    check(status == 0) { "PBKDF2 failed with status $status" }
                }
            }
        }

        return derivedKey
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = ByteArray(HMAC_LENGTH)
        key.usePinned { pinnedKey ->
            mac.usePinned { pinnedMac ->
                if (data.isEmpty()) {
                    cc_hmac_sha256(
                        pinnedKey.addressOf(0), key.size.toUInt(),
                        null, 0u,
                        pinnedMac.addressOf(0)
                    )
                } else {
                    data.usePinned { pinnedData ->
                        cc_hmac_sha256(
                            pinnedKey.addressOf(0), key.size.toUInt(),
                            pinnedData.addressOf(0), data.size.toUInt(),
                            pinnedMac.addressOf(0)
                        )
                    }
                }
            }
        }
        return mac
    }
}
