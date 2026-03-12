package org.commcare.cases.util

/**
 * iOS implementation using insertion-ordered LinkedHashMap.
 * Access-order (LRU) behavior is approximated by removing and re-inserting on get().
 * LruCache overrides get() to call this map, so LRU ordering works correctly.
 */
actual fun <K, V> createAccessOrderedLinkedHashMap(): LinkedHashMap<K, V> {
    return LinkedHashMap()
}
