package org.javarosa.core.util

import java.lang.ref.WeakReference
import java.util.Enumeration
import java.util.Hashtable
import java.util.Vector

/**
 * A Cache Table is a self-purging weak reference store that can be used
 * to maintain a cache of objects keyed by a dynamic type.
 *
 * Cache tables will automatically clean up references to freed weak references
 * on an internal schedule, and compact the table size to maintain as small
 * of a footprint as possible.
 *
 * Cache tables are only available with Weak References to maintain compatibility
 * with j2me runtimes.
 *
 * @author ctsims
 */
open class CacheTable<T, K> {
    private var totalAdditions = 0

    @JvmField
    var currentTable: Hashtable<T, WeakReference<K>>

    init {
        currentTable = Hashtable()
        registerCache(this)
    }

    open fun retrieve(key: T): K? {
        synchronized(this) {
            if (!currentTable.containsKey(key)) {
                return null
            }
            val retVal = currentTable[key]!!.get()
            if (retVal == null) {
                currentTable.remove(key)
            }
            return retVal
        }
    }

    open fun register(key: T, item: K) {
        synchronized(this) {
            currentTable[key] = WeakReference(item)
            totalAdditions++
        }
    }

    fun clear() {
        currentTable.clear()
        caches.removeAllElements()
    }

    companion object {
        private val caches = Vector<WeakReference<CacheTable<*, *>>>()

        private val cleaner = Thread {
            val toRemove = Vector<Int>()
            while (true) {
                try {
                    toRemove.removeAllElements()
                    for (i in 0 until caches.size) {
                        @Suppress("UNCHECKED_CAST")
                        val cache = caches.elementAt(i).get() as CacheTable<Any?, Any?>?
                        if (cache == null) {
                            toRemove.addElement(DataUtil.integer(i))
                        } else {
                            val table = cache.currentTable
                            val en: Enumeration<Any?> = table.keys()
                            while (en.hasMoreElements()) {
                                val key = en.nextElement()
                                synchronized(cache) {
                                    // See whether or not the cached reference has been cleared by the GC
                                    if (table[key]?.get() == null) {
                                        // If so, remove the entry, it's no longer useful.
                                        table.remove(key)
                                    }
                                }
                            }

                            synchronized(cache) {
                                // See if our current size is 25% the size of the largest size we've been
                                // and compact (clone to a new table) if so, since the table maintains the
                                // largest size it has ever been.
                                // TODO: 50 is a super arbitrary upper bound
                                if (cache.totalAdditions > 50 &&
                                    cache.totalAdditions - cache.currentTable.size > (cache.currentTable.size shr 2)
                                ) {
                                    val newTable = Hashtable<Any?, WeakReference<Any?>>(cache.currentTable.size)
                                    val keys: Enumeration<Any?> = table.keys()
                                    while (keys.hasMoreElements()) {
                                        val key = keys.nextElement()
                                        newTable[key] = cache.currentTable[key]!!
                                    }
                                    cache.currentTable = newTable
                                    cache.totalAdditions = cache.currentTable.size
                                }
                            }
                        }
                    }
                    for (id in toRemove.size - 1 downTo 0) {
                        caches.removeElementAt(toRemove.elementAt(id))
                    }
                    try {
                        Thread.sleep(3000)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun registerCache(table: CacheTable<*, *>) {
            caches.addElement(WeakReference(table))
            synchronized(cleaner) {
                if (!cleaner.isAlive) {
                    cleaner.start()
                }
            }
        }
    }
}
