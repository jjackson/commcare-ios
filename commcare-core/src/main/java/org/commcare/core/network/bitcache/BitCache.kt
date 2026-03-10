package org.commcare.core.network.bitcache

import org.javarosa.core.util.externalizable.PlatformIOException
import java.io.InputStream
import java.io.OutputStream

/**
 * @author ctsims
 */
interface BitCache {
    @Throws(PlatformIOException::class)
    fun initializeCache()

    @Throws(PlatformIOException::class)
    fun getCacheStream(): OutputStream

    @Throws(PlatformIOException::class)
    fun retrieveCache(): InputStream

    fun release()
}
