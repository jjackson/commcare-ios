package org.commcare.core.network.bitcache

import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.io.PlatformInputStream
import org.javarosa.core.io.PlatformOutputStream

/**
 * @author ctsims
 */
interface BitCache {
    @Throws(PlatformIOException::class)
    fun initializeCache()

    @Throws(PlatformIOException::class)
    fun getCacheStream(): PlatformOutputStream

    @Throws(PlatformIOException::class)
    fun retrieveCache(): PlatformInputStream

    fun release()
}
