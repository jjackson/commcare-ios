package org.javarosa.core.util

import java.util.Enumeration
import java.util.Hashtable
import java.util.Vector

class OrderedHashtable<K, V> : Hashtable<K, V> {
    private val orderedKeys: Vector<K>

    constructor() : super() {
        orderedKeys = Vector()
    }

    constructor(initialCapacity: Int) : super(initialCapacity) {
        orderedKeys = Vector(initialCapacity)
    }

    override fun clear() {
        orderedKeys.removeAllElements()
        super.clear()
    }

    fun elementAt(index: Int): V {
        return get(keyAt(index))!!
    }

    override fun elements(): Enumeration<V> {
        val elements = Vector<V>()
        for (i in 0 until size) {
            elements.addElement(elementAt(i))
        }
        return elements.elements()
    }

    fun indexOfKey(key: K): Int {
        return orderedKeys.indexOf(key)
    }

    fun keyAt(index: Int): K {
        return orderedKeys.elementAt(index)
    }

    override fun keys(): Enumeration<K> {
        return orderedKeys.elements()
    }

    override fun put(key: K, value: V): V? {
        if (key == null) {
            throw NullPointerException(
                String.format("No value found for key %s in table %s", key, this.toString())
            )
        }

        val v = super.put(key, value)
        // Check to see whether this grew after the put.
        // (We can't check for much else because this call
        // can be repeated inside of the put).
        if (super.size > orderedKeys.size) {
            orderedKeys.addElement(key)
        }
        return v
    }

    override fun remove(key: K): V? {
        orderedKeys.removeElement(key)
        return super.remove(key)
    }

    fun removeAt(i: Int) {
        remove(keyAt(i))
        orderedKeys.removeElementAt(i)
    }

    override fun toString(): String {
        val sb = StringBuffer()
        sb.append("[")
        val e = keys()
        while (e.hasMoreElements()) {
            val key = e.nextElement()
            sb.append(key.toString())
            sb.append(" => ")
            sb.append(get(key).toString())
            if (e.hasMoreElements())
                sb.append(", ")
        }
        sb.append("]")
        return sb.toString()
    }
}
