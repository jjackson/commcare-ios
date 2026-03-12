package org.commcare.cases.util

/**
 * Create a LinkedHashMap with access-order (LRU) iteration.
 * On JVM, uses LinkedHashMap(initialCapacity, loadFactor, accessOrder=true).
 * On iOS, uses a wrapper that reorders on access.
 */
expect fun <K, V> createAccessOrderedLinkedHashMap(): LinkedHashMap<K, V>
