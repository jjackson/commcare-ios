package org.commcare.core.network.bitcache

import org.commcare.core.encryption.CryptUtil
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKey

/**
 * @author ctsims
 */
internal class FileBitCache(
    private var cacheDirSetup: BitCacheFactory.CacheDirSetup?
) : BitCache {

    private var key: SecretKey? = null
    private var temp: File? = null

    @Throws(IOException::class)
    override fun initializeCache() {
        val cacheLocation = cacheDirSetup!!.getCacheDir()

        // generate temp file
        temp = File.createTempFile("commcare_pull_${Date().time}", "xml", cacheLocation)
        key = CryptUtil.generateSemiRandomKey()
    }

    @Throws(IOException::class)
    override fun getCacheStream(): OutputStream {
        // generate write key/cipher
        try {
            val encrypter = Cipher.getInstance("AES")
            encrypter.init(Cipher.ENCRYPT_MODE, key)

            // stream file
            val fos = FileOutputStream(temp)
            val cos = CipherOutputStream(fos, encrypter)

            return BufferedOutputStream(cos, 1024)
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            throw RuntimeException(e)
        } catch (e: NoSuchPaddingException) {
            e.printStackTrace()
            throw RuntimeException(e)
        } catch (e: InvalidKeyException) {
            e.printStackTrace()
            throw RuntimeException(e)
        }
    }

    @Throws(IOException::class)
    override fun retrieveCache(): InputStream {
        try {
            // generate read key/cipher
            val decrypter = Cipher.getInstance("AES")
            decrypter.init(Cipher.DECRYPT_MODE, key)

            // process
            val fis = FileInputStream(temp)
            val bis = BufferedInputStream(fis, 4096)
            return CipherInputStream(bis, decrypter)
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            throw RuntimeException(e)
        } catch (e: NoSuchPaddingException) {
            e.printStackTrace()
            throw RuntimeException(e)
        } catch (e: InvalidKeyException) {
            e.printStackTrace()
            throw RuntimeException(e)
        }
    }

    override fun release() {
        key = null
        cacheDirSetup = null
        temp?.delete()
    }
}
