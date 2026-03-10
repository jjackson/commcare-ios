package org.javarosa.core.util

/**
 * A simple multimap that maps keys to lists of values.
 * Replaces Guava's ArrayListMultimap/Multimap for KMP compatibility.
 */
class ListMultimap<K, V> private constructor(
    private val map: MutableMap<K, MutableList<V>>
) : kotlin.collections.Iterable<Map.Entry<K, Collection<V>>> {

    constructor() : this(LinkedHashMap())

    fun put(key: K, value: V) {
        map.getOrPut(key) { mutableListOf() }.add(value)
    }

    fun putAll(key: K, values: Iterable<V>) {
        val list = map.getOrPut(key) { mutableListOf() }
        values.forEach { list.add(it) }
    }

    fun putAll(other: ListMultimap<K, V>) {
        for (key in other.keySet()) {
            putAll(key, other[key])
        }
    }

    operator fun get(key: K): List<V> {
        return map[key] ?: emptyList()
    }

    fun keySet(): Set<K> = map.keys

    fun keys(): Collection<K> = map.keys

    fun entries(): List<Map.Entry<K, V>> {
        val result = mutableListOf<Map.Entry<K, V>>()
        for ((key, values) in map) {
            for (value in values) {
                result.add(SimpleMapEntry(key, value))
            }
        }
        return result
    }

    fun removeAll(key: K): List<V> {
        return map.remove(key) ?: emptyList()
    }

    val isEmpty: Boolean get() = map.isEmpty() || map.values.all { it.isEmpty() }

    fun forEach(action: (K, V) -> Unit) {
        for ((key, values) in map) {
            for (value in values) {
                action(key, value)
            }
        }
    }

    override fun iterator(): kotlin.collections.Iterator<Map.Entry<K, Collection<V>>> {
        return map.entries.map { (k, v) ->
            @Suppress("USELESS_CAST")
            SimpleMapEntry(k, v as Collection<V>) as Map.Entry<K, Collection<V>>
        }.iterator()
    }

    fun size(): Int = map.values.sumOf { it.size }

    fun containsKey(key: K): Boolean = map.containsKey(key) && map[key]!!.isNotEmpty()

    override fun toString(): String = map.toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ListMultimap<*, *>) return false
        return map == other.map
    }

    override fun hashCode(): Int = map.hashCode()

    companion object {
        @JvmStatic
        fun <K, V> create(): ListMultimap<K, V> = ListMultimap()

        @JvmStatic
        fun <K, V> emptyMultimap(): ListMultimap<K, V> = ListMultimap()
    }
}

/**
 * Pure Kotlin Map.Entry implementation replacing java.util.AbstractMap.SimpleEntry
 * for KMP commonMain compatibility.
 */
private class SimpleMapEntry<out K, out V>(
    override val key: K,
    override val value: V
) : Map.Entry<K, V> {
    override fun toString(): String = "$key=$value"
    override fun hashCode(): Int = (key?.hashCode() ?: 0) xor (value?.hashCode() ?: 0)
    override fun equals(other: Any?): Boolean {
        if (other !is Map.Entry<*, *>) return false
        return key == other.key && value == other.value
    }
}
