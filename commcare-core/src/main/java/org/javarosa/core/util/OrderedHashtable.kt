package org.javarosa.core.util


class OrderedHashtable<K, V> : LinkedHashMap<K, V> {
    private val orderedKeys: ArrayList<K>

    constructor() : super() {
        orderedKeys = ArrayList()
    }

    constructor(initialCapacity: Int) : super(initialCapacity) {
        orderedKeys = ArrayList(initialCapacity)
    }

    override fun get(key: K): V? = super.get(key)
    override fun containsKey(key: K): Boolean = super.containsKey(key)
    override fun containsValue(value: V): Boolean = super.containsValue(value)

    override fun clear() {
        orderedKeys.clear()
        super.clear()
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

        val v = super.put(key, value)
        // Check to see whether this grew after the put.
        // (We can't check for much else because this call
        // can be repeated inside of the put).
        if (super.size > orderedKeys.size) {
            orderedKeys.add(key)
        }
        return v
    }

    override fun remove(key: K): V? {
        orderedKeys.remove(key)
        return super.remove(key)
    }

    fun removeAt(i: Int) {
        remove(keyAt(i))
        orderedKeys.removeAt(i)
    }

    override fun toString(): String {
        val sb = StringBuffer()
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
