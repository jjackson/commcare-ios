package org.javarosa.core.util

/**
 * Ordered map with indexed access to keys.
 * Uses composition with LinkedHashMap for cross-platform compatibility
 * (LinkedHashMap is final in Kotlin/Native, so we can't extend it).
 */
class OrderedHashtable<K, V> : MutableMap<K, V> {
    private val backingMap: LinkedHashMap<K, V>
    private val orderedKeys: ArrayList<K>

    constructor() {
        backingMap = LinkedHashMap()
        orderedKeys = ArrayList()
    }

    constructor(initialCapacity: Int) {
        backingMap = LinkedHashMap(initialCapacity)
        orderedKeys = ArrayList(initialCapacity)
    }

    override val size: Int get() = backingMap.size
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = backingMap.entries
    override val keys: MutableSet<K> get() = backingMap.keys
    override val values: MutableCollection<V> get() = backingMap.values
    override fun containsKey(key: K): Boolean = backingMap.containsKey(key)
    override fun containsValue(value: V): Boolean = backingMap.containsValue(value)
    override fun get(key: K): V? = backingMap[key]
    override fun isEmpty(): Boolean = backingMap.isEmpty()

    override fun clear() {
        orderedKeys.clear()
        backingMap.clear()
    }

    fun elementAt(index: Int): V {
        return get(keyAt(index))!!
    }

    fun elements(): MutableIterator<V> {
        val elements = ArrayList<V>()
        for (i in 0 until size) {
            elements.add(elementAt(i))
        }
        return elements.iterator()
    }

    fun indexOfKey(key: K): Int {
        return orderedKeys.indexOf(key)
    }

    fun keyAt(index: Int): K {
        return orderedKeys[index]
    }

    fun orderedKeys(): MutableIterator<K> {
        return orderedKeys.iterator()
    }

    override fun put(key: K, value: V): V? {
        if (key == null) {
            throw NullPointerException(
                "No value found for key $key in table ${this.toString()}"
            )
        }

        val v = backingMap.put(key, value)
        if (backingMap.size > orderedKeys.size) {
            orderedKeys.add(key)
        }
        return v
    }

    override fun putAll(from: Map<out K, V>) {
        for ((key, value) in from) {
            put(key, value)
        }
    }

    override fun remove(key: K): V? {
        orderedKeys.remove(key)
        return backingMap.remove(key)
    }

    fun removeAt(i: Int) {
        val key = keyAt(i)
        backingMap.remove(key)
        orderedKeys.removeAt(i)
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("[")
        val e = orderedKeys()
        while (e.hasNext()) {
            val key = e.next()
            sb.append(key.toString())
            sb.append(" => ")
            sb.append(get(key).toString())
            if (e.hasNext())
                sb.append(", ")
        }
        sb.append("]")
        return sb.toString()
    }
}
