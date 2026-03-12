package org.javarosa.core.util.externalizable

import org.javarosa.core.util.OrderedHashtable

@Suppress("UNCHECKED_CAST")
actual fun <K, V> createOrderedHashMap(initialCapacity: Int): HashMap<K, V> {
    return OrderedHashtable<K, V>(initialCapacity) as HashMap<K, V>
}

actual fun isOrderedHashMap(map: HashMap<*, *>): Boolean {
    return map is OrderedHashtable<*, *>
}
