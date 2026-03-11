package org.commcare.core.network.bitcache

import org.javarosa.core.io.createByteArrayInputStream
import org.javarosa.core.io.createByteArrayOutputStream
import org.javarosa.core.io.byteArrayOutputStreamToBytes
import org.javarosa.core.io.PlatformInputStream
import org.javarosa.core.io.PlatformOutputStream

/**
 * @author ctsims
 */
internal class MemoryBitCache : BitCache {

    private var bos: PlatformOutputStream? = null
    private var data: ByteArray? = null

    override fun initializeCache() {
        bos = createByteArrayOutputStream()
        data = null
    }

    override fun getCacheStream(): PlatformOutputStream {
        return bos!!
    }

    override fun retrieveCache(): PlatformInputStream {
        if (data == null) {
            data = byteArrayOutputStreamToBytes(bos!!)
            bos = null
        }
        return createByteArrayInputStream(data!!)
    }

    override fun release() {
        bos = null
        data = null
    }
}
