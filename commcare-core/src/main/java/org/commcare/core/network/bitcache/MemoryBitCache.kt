package org.commcare.core.network.bitcache

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * @author ctsims
 */
internal class MemoryBitCache : BitCache {

    private var bos: ByteArrayOutputStream? = null
    private var data: ByteArray? = null

    override fun initializeCache() {
        bos = ByteArrayOutputStream()
        data = null
    }

    override fun getCacheStream(): OutputStream {
        return bos!!
    }

    override fun retrieveCache(): InputStream {
        if (data == null) {
            data = bos!!.toByteArray()
            bos = null
        }
        return ByteArrayInputStream(data)
    }

    override fun release() {
        bos = null
        data = null
    }
}
