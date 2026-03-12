package org.commcare.cases.util

actual fun <K, V> createAccessOrderedLinkedHashMap(): LinkedHashMap<K, V> {
    return LinkedHashMap(0, 0.75f, true)
}
