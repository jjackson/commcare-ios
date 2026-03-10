package org.commcare.core.network.bitcache

import java.io.File

/**
 * @author ctsims
 */
object BitCacheFactory {

    @JvmStatic
    fun getCache(cacheDirSetup: CacheDirSetup, estimatedSize: Long): BitCache {
        return if (estimatedSize == -1L || estimatedSize > 1024 * 1024 * 4) {
            FileBitCache(cacheDirSetup)
        } else {
            MemoryBitCache()
        }
    }

    interface CacheDirSetup {
        fun getCacheDir(): File
    }
}
