package org.javarosa.core.util.externalizable

import org.javarosa.core.util.OrderedHashtable

/**
 * Factory for creating ordered hash maps.
 */
fun <K, V> createOrderedHashMap(initialCapacity: Int = 16): MutableMap<K, V> {
    return OrderedHashtable<K, V>(initialCapacity)
}

/**
 * Check if a map is an ordered hashtable type.
 */
fun isOrderedHashMap(map: Map<*, *>): Boolean {
    return map is OrderedHashtable<*, *>
}
