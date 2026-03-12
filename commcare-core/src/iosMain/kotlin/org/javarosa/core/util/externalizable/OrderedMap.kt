package org.javarosa.core.util.externalizable

actual fun <K, V> createOrderedHashMap(initialCapacity: Int): HashMap<K, V> {
    // LinkedHashMap maintains insertion order, which is what OrderedHashtable provides on JVM
    return LinkedHashMap(initialCapacity)
}

actual fun isOrderedHashMap(map: HashMap<*, *>): Boolean {
    // On iOS, all LinkedHashMaps are considered "ordered"
    return map is LinkedHashMap<*, *>
}
