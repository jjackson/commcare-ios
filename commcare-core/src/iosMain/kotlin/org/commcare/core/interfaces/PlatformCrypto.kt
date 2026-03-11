package org.commcare.core.interfaces

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CommonCrypto.CC_MD5
import platform.CommonCrypto.CC_MD5_DIGEST_LENGTH
import platform.CommonCrypto.CC_SHA256
import platform.CommonCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CommonCrypto.CCCrypt
import platform.CommonCrypto.kCCAlgorithmAES
import platform.CommonCrypto.kCCBlockSizeAES128
import platform.CommonCrypto.kCCDecrypt
import platform.CommonCrypto.kCCEncrypt
import platform.CommonCrypto.kCCOptionPKCS7Padding
import platform.CommonCrypto.kCCSuccess
import platform.CoreFoundation.CFIndex
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault
import platform.posix.size_tVar

/**
 * iOS cryptography implementation using CommonCrypto and Security frameworks.
 *
 * Uses AES/CBC/PKCS7 (CommonCrypto's native mode) rather than AES/GCM
 * since CommonCrypto doesn't directly support GCM. The encrypt/decrypt
 * format is IV (16 bytes) prepended to ciphertext, matching the interface
 * contract.
 */
@OptIn(ExperimentalForeignApi::class)
actual object PlatformCrypto {
    private const val AES_IV_LENGTH = kCCBlockSizeAES128

    actual fun sha256(data: ByteArray): ByteArray {
        val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
        digest.usePinned { digestPinned ->
            if (data.isEmpty()) {
                CC_SHA256(null, 0u, digestPinned.addressOf(0).reinterpret())
            } else {
                data.usePinned { dataPinned ->
                    CC_SHA256(
                        dataPinned.addressOf(0),
                        data.size.toUInt(),
                        digestPinned.addressOf(0).reinterpret()
                    )
                }
            }
        }
        return digest
    }

    actual fun md5(data: ByteArray): ByteArray {
        val digest = ByteArray(CC_MD5_DIGEST_LENGTH)
        digest.usePinned { digestPinned ->
            if (data.isEmpty()) {
                CC_MD5(null, 0u, digestPinned.addressOf(0).reinterpret())
            } else {
                data.usePinned { dataPinned ->
                    CC_MD5(
                        dataPinned.addressOf(0),
                        data.size.toUInt(),
                        digestPinned.addressOf(0).reinterpret()
                    )
                }
            }
        }
        return digest
    }

    actual fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        bytes.usePinned { pinned ->
            val status = SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0))
            if (status != errSecSuccess) {
                throw RuntimeException("SecRandomCopyBytes failed with status: $status")
            }
        }
        return bytes
    }

    actual fun aesEncrypt(data: ByteArray, key: ByteArray): ByteArray {
        val iv = randomBytes(AES_IV_LENGTH)
        val bufferSize = data.size + AES_IV_LENGTH // room for PKCS7 padding
        val ciphertext = ByteArray(bufferSize)

        memScoped {
            val dataOutMoved = alloc<size_tVar>()
            val status = ciphertext.usePinned { ciphertextPinned ->
                iv.usePinned { ivPinned ->
                    key.usePinned { keyPinned ->
                        if (data.isEmpty()) {
                            CCCrypt(
                                kCCEncrypt,
                                kCCAlgorithmAES,
                                kCCOptionPKCS7Padding.toUInt(),
                                keyPinned.addressOf(0),
                                key.size.toULong(),
                                ivPinned.addressOf(0),
                                null,
                                0u,
                                ciphertextPinned.addressOf(0),
                                bufferSize.toULong(),
                                dataOutMoved.ptr
                            )
                        } else {
                            data.usePinned { dataPinned ->
                                CCCrypt(
                                    kCCEncrypt,
                                    kCCAlgorithmAES,
                                    kCCOptionPKCS7Padding.toUInt(),
                                    keyPinned.addressOf(0),
                                    key.size.toULong(),
                                    ivPinned.addressOf(0),
                                    dataPinned.addressOf(0),
                                    data.size.toULong(),
                                    ciphertextPinned.addressOf(0),
                                    bufferSize.toULong(),
                                    dataOutMoved.ptr
                                )
                            }
                        }
                    }
                }
            }
            if (status != kCCSuccess) {
                throw RuntimeException("AES encrypt failed with status: $status")
            }
            val actualSize = dataOutMoved.value.toInt()
            return iv + ciphertext.copyOfRange(0, actualSize)
        }
    }

    actual fun aesDecrypt(data: ByteArray, key: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, AES_IV_LENGTH)
        val ciphertext = data.copyOfRange(AES_IV_LENGTH, data.size)
        val plaintext = ByteArray(ciphertext.size)

        memScoped {
            val dataOutMoved = alloc<size_tVar>()
            val status = plaintext.usePinned { plaintextPinned ->
                iv.usePinned { ivPinned ->
                    key.usePinned { keyPinned ->
                        if (ciphertext.isEmpty()) {
                            CCCrypt(
                                kCCDecrypt,
                                kCCAlgorithmAES,
                                kCCOptionPKCS7Padding.toUInt(),
                                keyPinned.addressOf(0),
                                key.size.toULong(),
                                ivPinned.addressOf(0),
                                null,
                                0u,
                                plaintextPinned.addressOf(0),
                                plaintext.size.toULong(),
                                dataOutMoved.ptr
                            )
                        } else {
                            ciphertext.usePinned { ciphertextPinned ->
                                CCCrypt(
                                    kCCDecrypt,
                                    kCCAlgorithmAES,
                                    kCCOptionPKCS7Padding.toUInt(),
                                    keyPinned.addressOf(0),
                                    key.size.toULong(),
                                    ivPinned.addressOf(0),
                                    ciphertextPinned.addressOf(0),
                                    ciphertext.size.toULong(),
                                    plaintextPinned.addressOf(0),
                                    plaintext.size.toULong(),
                                    dataOutMoved.ptr
                                )
                            }
                        }
                    }
                }
            }
            if (status != kCCSuccess) {
                throw RuntimeException("AES decrypt failed with status: $status")
            }
            val actualSize = dataOutMoved.value.toInt()
            return plaintext.copyOfRange(0, actualSize)
        }
    }

    actual fun generateAesKey(bits: Int): ByteArray {
        return randomBytes(bits / 8)
    }
}
