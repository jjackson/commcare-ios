package org.javarosa.core.util.externalizable

/**
 * Cross-platform factory for creating ordered hash maps.
 * On JVM: creates OrderedHashtable instances.
 * On iOS: creates LinkedHashMap instances (which are ordered by insertion).
 */
expect fun <K, V> createOrderedHashMap(initialCapacity: Int = 16): HashMap<K, V>

/**
 * Check if a HashMap is an ordered hashtable type.
 */
expect fun isOrderedHashMap(map: HashMap<*, *>): Boolean
