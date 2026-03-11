package org.commcare.util

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.Key
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.jvm.JvmStatic

object EncryptionUtils {

    /**
     * Encrypts a message and produces a base64 encoded payload containing the ciphertext.
     * The key and transform are specified as inputs.
     * A random IV may be generated to encrypt the input (unless using RSA).
     *
     * @param message a byte[] to be encrypted
     * @param key     The key to use for encryption
     * @param transform The transformation to use for encryption
     * @param includeMessageLength Whether to include the message length in the packed payload
     * @return A base64 encoded payload containing the IV and encrypted ciphertext
     */
    @JvmStatic
    @Throws(EncryptionException::class)
    fun encrypt(
        message: ByteArray, key: Key, transform: String,
        includeMessageLength: Boolean
    ): String {
        val MIN_IV_LENGTH_BYTE = 1
        val MAX_IV_LENGTH_BYTE = 255

        val allowEmptyIV = transform.startsWith("RSA")

        try {
            val cipher = Cipher.getInstance(transform)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encryptedMessage = cipher.doFinal(message)
            val iv = cipher.iv
            val ivLength = iv?.size ?: 0
            if (!allowEmptyIV && (ivLength < MIN_IV_LENGTH_BYTE || ivLength > MAX_IV_LENGTH_BYTE)) {
                throw EncryptionException(
                    "Initialization vector should be between " +
                            MIN_IV_LENGTH_BYTE + " and " + MAX_IV_LENGTH_BYTE +
                            " bytes long, but it is " + ivLength + " bytes"
                )
            }

            val extraBytes = if (includeMessageLength) 2 else 0

            val byteBuffer = ByteBuffer.allocate(1 + ivLength + extraBytes + encryptedMessage.size)
                .put(ivLength.toByte())

            if (iv != null) {
                byteBuffer.put(iv)
            }

            if (includeMessageLength) {
                byteBuffer.put((encryptedMessage.size / 256).toByte())
                byteBuffer.put((encryptedMessage.size % 256).toByte())
            }

            byteBuffer.put(encryptedMessage)

            return Base64.encode(byteBuffer.array())
        } catch (e: NoSuchAlgorithmException) {
            throw EncryptionException("Error during encryption", e)
        } catch (e: NoSuchPaddingException) {
            throw EncryptionException("Error during encryption", e)
        } catch (e: InvalidKeyException) {
            throw EncryptionException("Error during encryption", e)
        } catch (e: BadPaddingException) {
            throw EncryptionException("Error during encryption", e)
        } catch (e: IllegalBlockSizeException) {
            throw EncryptionException("Error during encryption", e)
        }
    }

    /**
     * Encrypts a message using the AES encryption and produces a base64 encoded payload
     * containing the ciphertext, and a random IV which was used to encrypt the input.
     *
     * @param message a UTF-8 encoded message to be encrypted
     * @param key     A base64 encoded 256 bit symmetric key
     * @return A base64 encoded payload containing the IV and AES encrypted ciphertext
     */
    @JvmStatic
    @Throws(EncryptionException::class)
    fun encrypt(message: String, key: String): String {
        val ENCRYPT_ALGO = "AES/GCM/NoPadding"
        val secret = getSecretKeySpec(key)

        return encrypt(
            message.toByteArray(Charset.forName("UTF-8")),
            secret, ENCRYPT_ALGO, false
        )
    }

    @JvmStatic
    @Throws(EncryptionException::class)
    fun getSecretKeySpec(key: String): SecretKey {
        val KEY_LENGTH_BIT = 256
        val keyBytes: ByteArray
        try {
            keyBytes = Base64.decode(key)
        } catch (e: Base64DecoderException) {
            throw EncryptionException("Encryption key base 64 encoding is invalid", e)
        }
        if (8 * keyBytes.size != KEY_LENGTH_BIT) {
            throw EncryptionException(
                "Key should be $KEY_LENGTH_BIT bits long, not ${8 * keyBytes.size}"
            )
        }
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Decrypts a message and returns the unencrypted byte[].
     *
     * @param bytes a byte[] to be decrypted
     * @param key     The key that should be used for decryption
     * @param transform The transformation to use for decryption
     * @param includeMessageLength Whether the message length is included in the packed bytes input
     * @return A byte[] containing the unencrypted message
     */
    @JvmStatic
    @Throws(EncryptionException::class)
    fun decrypt(
        bytes: ByteArray, key: Key, transform: String,
        includeMessageLength: Boolean
    ): ByteArray {
        val TAG_LENGTH_BIT = 128
        var readIndex = 0
        val ivLength = bytes[readIndex].toInt() and 0xFF
        readIndex++
        var iv: ByteArray? = null
        if (ivLength > 0) {
            iv = ByteArray(ivLength)
            System.arraycopy(bytes, readIndex, iv, 0, ivLength)
            readIndex += ivLength
        }

        val encryptedLength: Int
        if (includeMessageLength) {
            encryptedLength = ((bytes[readIndex].toInt() and 0xFF) shl 8) +
                    (bytes[readIndex + 1].toInt() and 0xFF)
            readIndex += 2
        } else {
            encryptedLength = bytes.size - readIndex
        }

        val encrypted = ByteArray(encryptedLength)
        System.arraycopy(bytes, readIndex, encrypted, 0, encryptedLength)

        try {
            val cipher = Cipher.getInstance(transform)

            if (includeMessageLength) {
                cipher.init(Cipher.DECRYPT_MODE, key, if (iv != null) IvParameterSpec(iv) else null)
            } else {
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
            }

            return cipher.doFinal(encrypted)
        } catch (e: NoSuchAlgorithmException) {
            throw EncryptionException("Decrypting message failed", e)
        } catch (e: NoSuchPaddingException) {
            throw EncryptionException("Decrypting message failed", e)
        } catch (e: IllegalBlockSizeException) {
            throw EncryptionException("Decrypting message failed", e)
        } catch (e: InvalidAlgorithmParameterException) {
            throw EncryptionException("Decrypting message failed", e)
        } catch (e: BadPaddingException) {
            throw EncryptionException("Decrypting message failed", e)
        } catch (e: InvalidKeyException) {
            throw EncryptionException("Decrypting message failed", e)
        }
    }

    /**
     * Decrypts a base64 payload containing an IV and AES encrypted ciphertext using the provided key.
     *
     * @param message a message to be decrypted
     * @param key     key that should be used for decryption
     * @return Decrypted message for the given AES encrypted message
     */
    @JvmStatic
    @Throws(EncryptionException::class)
    fun decrypt(message: String, key: String): String {
        val ENCRYPT_ALGO = "AES/GCM/NoPadding"
        val secret = getSecretKeySpec(key)

        try {
            val messageBytes = Base64.decode(message)
            val plainText = decrypt(messageBytes, secret, ENCRYPT_ALGO, false)

            return String(plainText, Charset.forName("UTF-8"))
        } catch (e: Base64DecoderException) {
            throw EncryptionException("Decrypting message failed", e)
        }
    }

    @JvmStatic
    fun getMd5HashAsString(plainText: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            md.update(plainText.toByteArray())
            val hashInBytes = md.digest()
            Base64.encode(hashInBytes)
        } catch (e: NoSuchAlgorithmException) {
            ""
        }
    }

    class EncryptionException : Exception {
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)
    }
}
