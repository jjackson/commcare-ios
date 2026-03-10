package org.commcare.core.network.bitcache

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * @author ctsims
 */
interface BitCache {
    @Throws(IOException::class)
    fun initializeCache()

    @Throws(IOException::class)
    fun getCacheStream(): OutputStream

    @Throws(IOException::class)
    fun retrieveCache(): InputStream

    fun release()
}
