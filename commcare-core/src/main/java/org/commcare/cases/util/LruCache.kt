package org.commcare.cases.util

/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/**
 * Static library version of Android LruCache. Used to write apps
 * that run on platforms prior to Android Honeycomb MR1. When running on
 * Honeycomb MR1 or above, this implementation is still used; it does not try to
 * switch to the framework's implementation. See the framework SDK
 * documentation for a class overview.
 */
open class LruCache<K, V>(private var maxSize: Int) {

    private val map: LinkedHashMap<K, V>

    /** Size of this cache in units. Not necessarily the number of elements. */
    private var size: Int = 0
    private var putCount: Int = 0
    private var createCount: Int = 0
    private var evictionCount: Int = 0
    private var hitCount: Int = 0
    private var missCount: Int = 0

    init {
        require(maxSize > 0) { "maxSize <= 0" }
        map = LinkedHashMap(0, 0.75f, true)
    }

    /**
     * Returns the value for [key] if it exists in the cache or can be
     * created by [create]. If a value was returned, it is moved to the
     * head of the queue. This returns null if a value is not cached and cannot
     * be created.
     */
    fun get(key: K): V? {
        requireNotNull(key) { "key == null" }

        val mapResult = map[key]
        if (mapResult != null) {
            hitCount++
            return mapResult
        }

        missCount++

        // TODO: release the lock while calling this potentially slow user code
        val result = create(key)
        if (result != null) {
            createCount++
            size += safeSizeOf(key, result)
            map[key] = result
            trimToSize(maxSize)
        }
        return result
    }

    /**
     * Caches [value] for [key]. The value is moved to the head of
     * the queue.
     *
     * @return the previous value mapped by [key]. Although that entry is
     *     no longer cached, it has not been passed to [entryEvicted].
     */
    fun put(key: K, value: V): V? {
        requireNotNull(key) { "key == null" }
        requireNotNull(value) { "value == null" }

        putCount++
        size += safeSizeOf(key, value)
        val previous = map.put(key, value)
        if (previous != null) {
            size -= safeSizeOf(key, previous)
        }
        trimToSize(maxSize)
        return previous
    }

    private fun trimToSize(maxSize: Int) {
        while (size > maxSize && map.isNotEmpty()) {
            val toEvict = map.entries.iterator().next() ?: break
            val key = toEvict.key
            val value = toEvict.value
            map.remove(key)
            size -= safeSizeOf(key, value)
            evictionCount++
            // TODO: release the lock while calling this potentially slow user code
            entryEvicted(key, value)
        }
        check(!(size < 0 || (map.isEmpty() && size != 0))) {
            "${this::class.qualifiedName}.sizeOf() is reporting inconsistent results!"
        }
    }

    /**
     * Removes the entry for [key] if it exists.
     *
     * @return the previous value mapped by [key]. Although that entry is
     *     no longer cached, it has not been passed to [entryEvicted].
     */
    fun remove(key: K): V? {
        requireNotNull(key) { "key == null" }
        val previous = map.remove(key)
        if (previous != null) {
            size -= safeSizeOf(key, previous)
        }
        return previous
    }

    /**
     * Called for entries that have reached the tail of the least recently used
     * queue and are be removed. The default implementation does nothing.
     */
    protected open fun entryEvicted(key: K, value: V) {}

    /**
     * Called after a cache miss to compute a value for the corresponding key.
     * Returns the computed value or null if no value can be computed. The
     * default implementation returns null.
     */
    protected open fun create(key: K): V? {
        return null
    }

    private fun safeSizeOf(key: K, value: V): Int {
        val result = sizeOf(key, value)
        check(result >= 0) { "Negative size: $key=$value" }
        return result
    }

    /**
     * Returns the size of the entry for [key] and [value] in
     * user-defined units. The default implementation returns 1 so that size
     * is the number of entries and max size is the maximum number of entries.
     *
     * An entry's size must not change while it is in the cache.
     */
    protected open fun sizeOf(key: K, value: V): Int {
        return 1
    }

    /**
     * Clear the cache, calling [entryEvicted] on each removed entry.
     */
    fun evictAll() {
        trimToSize(-1) // -1 will evict 0-sized elements
    }

    /**
     * For caches that do not override [sizeOf], this returns the number
     * of entries in the cache. For all other caches, this returns the sum of
     * the sizes of the entries in this cache.
     */
    fun size(): Int {
        return size
    }

    /**
     * For caches that do not override [sizeOf], this returns the maximum
     * number of entries in the cache. For all other caches, this returns the
     * maximum sum of the sizes of the entries in this cache.
     */
    fun maxSize(): Int {
        return maxSize
    }

    /**
     * Returns the number of times [get] returned a value.
     */
    fun hitCount(): Int {
        return hitCount
    }

    /**
     * Returns the number of times [get] returned null or required a new
     * value to be created.
     */
    fun missCount(): Int {
        return missCount
    }

    /**
     * Returns the number of times [create] returned a value.
     */
    fun createCount(): Int {
        return createCount
    }

    /**
     * Returns the number of times [put] was called.
     */
    fun putCount(): Int {
        return putCount
    }

    /**
     * Returns the number of values that have been evicted.
     */
    fun evictionCount(): Int {
        return evictionCount
    }

    /**
     * Returns a copy of the current contents of the cache, ordered from least
     * recently accessed to most recently accessed.
     */
    fun snapshot(): Map<K, V> {
        return LinkedHashMap(map)
    }

    override fun toString(): String {
        val accesses = hitCount + missCount
        val hitPercent = if (accesses != 0) 100 * hitCount / accesses else 0
        return "LruCache[maxSize=$maxSize,hits=$hitCount,misses=$missCount,hitRate=$hitPercent%]"
    }
}
