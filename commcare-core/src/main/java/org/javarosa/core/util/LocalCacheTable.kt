package org.javarosa.core.util

import java.lang.ref.WeakReference

/**
 * A Local Cache Table is a weak reference store that can be used
 * to maintain a cache of objects keyed by a dynamic type.
 *
 * Local Cache Tables should be used for non-global caches. For global caches
 * use [CacheTable].
 */
class LocalCacheTable<T, K> {
    private val currentTable = HashMap<T, WeakReference<K>>()

    fun retrieve(key: T): K? {
        synchronized(this) {
            if (!currentTable.containsKey(key)) {
                return null
            }
            @Suppress("UNCHECKED_CAST")
            val retVal = currentTable[key]!!.get()
            if (retVal == null) {
                currentTable.remove(key)
            }
            return retVal
        }
    }

    fun register(key: T, item: K) {
        synchronized(this) {
            currentTable[key] = WeakReference(item)
        }
    }

    fun clear() {
        currentTable.clear()
    }
}
