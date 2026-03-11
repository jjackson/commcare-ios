@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util

import java.lang.ref.WeakReference

/**
 * JVM implementation of CacheTable using WeakReference-backed storage
 * with a background cleaner thread for automatic memory management.
 */
actual open class CacheTable<T, K> {
    private var totalAdditions = 0

    @JvmField
    var currentTable: HashMap<T, WeakReference<K>>

    init {
        currentTable = HashMap()
        registerCache(this)
    }

    actual open fun retrieve(key: T): K? {
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

    actual open fun register(key: T, item: K) {
        synchronized(this) {
            currentTable[key] = WeakReference(item)
            totalAdditions++
        }
    }

    actual fun clear() {
        currentTable.clear()
        caches.clear()
    }

    companion object {
        private val caches = ArrayList<WeakReference<CacheTable<*, *>>>()

        private val cleaner = Thread {
            val toRemove = ArrayList<Int>()
            while (true) {
                try {
                    toRemove.clear()
                    for (i in 0 until caches.size) {
                        @Suppress("UNCHECKED_CAST")
                        val cache = caches[i].get() as CacheTable<Any?, Any?>?
                        if (cache == null) {
                            toRemove.add(DataUtil.integer(i))
                        } else {
                            val table = cache.currentTable
                            val en: MutableIterator<Any?> = table.keys.iterator()
                            while (en.hasNext()) {
                                val key = en.next()
                                synchronized(cache) {
                                    if (table[key]?.get() == null) {
                                        table.remove(key)
                                    }
                                }
                            }

                            synchronized(cache) {
                                if (cache.totalAdditions > 50 &&
                                    cache.totalAdditions - cache.currentTable.size > (cache.currentTable.size shr 2)
                                ) {
                                    val newTable = HashMap<Any?, WeakReference<Any?>>(cache.currentTable.size)
                                    val keys: MutableIterator<Any?> = table.keys.iterator()
                                    while (keys.hasNext()) {
                                        val key = keys.next()
                                        newTable[key] = cache.currentTable[key]!!
                                    }
                                    cache.currentTable = newTable
                                    cache.totalAdditions = cache.currentTable.size
                                }
                            }
                        }
                    }
                    for (id in toRemove.size - 1 downTo 0) {
                        caches.removeAt(toRemove[id])
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
            caches.add(WeakReference(table))
            synchronized(cleaner) {
                if (!cleaner.isAlive) {
                    cleaner.start()
                }
            }
        }
    }
}
