package org.javarosa.core.util

/**
 * A Cache Table is a store that can be used to maintain a cache of objects
 * keyed by a dynamic type.
 *
 * Cache tables will automatically clean up stale entries on an internal schedule,
 * and compact the table size to maintain as small of a footprint as possible.
 *
 * Note: In the original Java implementation, this used WeakReferences for GC-friendly
 * caching. For KMP compatibility, this now uses strong references with manual eviction.
 * Callers should use [clear] to release entries when the cache is no longer needed.
 *
 * @author ctsims
 */
open class CacheTable<T, K> {
    private var totalAdditions = 0

    @JvmField
    var currentTable: HashMap<T, K>

    init {
        currentTable = HashMap()
    }

    open fun retrieve(key: T): K? {
        synchronized(this) {
            return currentTable[key]
        }
    }

    open fun register(key: T, item: K) {
        synchronized(this) {
            currentTable[key] = item
            totalAdditions++
        }
    }

    fun clear() {
        currentTable.clear()
    }

    companion object {
        // Removed the background cleaner thread and weak reference tracking.
        // Callers should call clear() when the cache is no longer needed.
    }
}
