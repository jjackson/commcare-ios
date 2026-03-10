package org.commcare.core.encryption

import org.javarosa.core.io.StreamsUtil
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * @author ctsims
 */
object CryptUtil {

    private const val PBE_PROVIDER = "PBEWITHSHA-256AND256BITAES-CBC-BC"

    @JvmStatic
    @Throws(
        NoSuchAlgorithmException::class,
        InvalidKeyException::class,
        NoSuchPaddingException::class,
        InvalidKeySpecException::class
    )
    fun encodingCipher(passwordOrPin: String): Cipher {
        val spec = PBEKeySpec(passwordOrPin.toCharArray(), "SFDWFDCF".toByteArray(), 10)
        val factory = SecretKeyFactory.getInstance(PBE_PROVIDER)
        val key = factory.generateSecret(spec)

        val cipher = Cipher.getInstance(PBE_PROVIDER)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        return cipher
    }

    @JvmStatic
    @Throws(
        NoSuchAlgorithmException::class,
        InvalidKeySpecException::class,
        NoSuchPaddingException::class,
        InvalidKeyException::class
    )
    fun decodingCipher(password: String): Cipher {
        val spec = PBEKeySpec(password.toCharArray(), "SFDWFDCF".toByteArray(), 10)
        val factory = SecretKeyFactory.getInstance(PBE_PROVIDER)
        val key = factory.generateSecret(spec)

        val cipher = Cipher.getInstance(PBE_PROVIDER)
        cipher.init(Cipher.DECRYPT_MODE, key)

        return cipher
    }

    @JvmStatic
    fun encrypt(input: ByteArray, cipher: Cipher): ByteArray {
        val bis = ByteArrayInputStream(input)
        val cis = CipherInputStream(bis, cipher)

        val bos = ByteArrayOutputStream()

        try {
            StreamsUtil.writeFromInputToOutputNew(cis, bos)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        return bos.toByteArray()
    }

    @JvmStatic
    fun decrypt(input: ByteArray, cipher: Cipher): ByteArray? {
        try {
            return cipher.doFinal(input)
        } catch (e: BadPaddingException) {
            e.printStackTrace()
        } catch (e: IllegalBlockSizeException) {
            e.printStackTrace()
        }
        return null
    }

    @JvmStatic
    fun sha256(value: String): String {
        val digest: MessageDigest
        try {
            digest = MessageDigest.getInstance("SHA-256")
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        }
        val hash = digest.digest(value.toByteArray(StandardCharsets.UTF_8))
        val sb = StringBuilder()
        for (b in hash) {
            sb.append(String.format("%x", b))
        }
        return sb.toString()
    }

    private fun append(one: ByteArray, two: ByteArray): ByteArray {
        val result = ByteArray(one.size + two.size)
        for (i in result.indices) {
            if (i < one.size) {
                result[i] = one[i]
            } else {
                val index = i - one.size
                result[i] = two[index]
            }
        }
        return result
    }

    @JvmStatic
    fun uniqueSeedFromSecureStatic(secureStatic: ByteArray): ByteArray? {
        val uniqueBase = Date().time
        val baseString = java.lang.Long.toHexString(uniqueBase)
        try {
            return append(
                baseString.toByteArray(),
                MessageDigest.getInstance("SHA-1").digest(secureStatic)
            )
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }
        return null
    }

    @JvmStatic
    fun generateSymmetricKey(prngSeed: ByteArray): SecretKey? {
        try {
            val generator = KeyGenerator.getInstance("AES")
            generator.init(256, SecureRandom(prngSeed))
            return generator.generateKey()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }
        return null
    }

    @JvmStatic
    fun generateSemiRandomKey(): SecretKey? {
        try {
            val generator = KeyGenerator.getInstance("AES")
            generator.init(256, SecureRandom())
            return generator.generateKey()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }
        return null
    }

    @JvmStatic
    @Throws(
        InvalidKeyException::class,
        NoSuchAlgorithmException::class,
        NoSuchPaddingException::class,
        InvalidKeySpecException::class
    )
    fun getPrivateKeyCipher(privateKey: ByteArray): Cipher {
        val keyFactory = KeyFactory.getInstance("RSA")
        val ks = PKCS8EncodedKeySpec(privateKey)
        val privKey = keyFactory.generatePrivate(ks) as RSAPrivateKey

        val c = Cipher.getInstance("RSA")
        c.init(Cipher.DECRYPT_MODE, privKey)
        return c
    }

    @JvmStatic
    @Throws(
        NoSuchAlgorithmException::class,
        NoSuchPaddingException::class,
        InvalidKeyException::class
    )
    fun getAesKeyCipher(aesKey: ByteArray): Cipher {
        return getAesKeyCipher(aesKey, Cipher.DECRYPT_MODE)
    }

    @Throws(
        NoSuchAlgorithmException::class,
        NoSuchPaddingException::class,
        InvalidKeyException::class
    )
    private fun getAesKeyCipher(aesKey: ByteArray, mode: Int): Cipher {
        val spec = SecretKeySpec(aesKey, "AES")
        val decrypter = Cipher.getInstance("AES")
        decrypter.init(mode, spec)
        return decrypter
    }
}
