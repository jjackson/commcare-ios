package org.javarosa.core.util

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.Vector

/**
 * Maintain an array of integers in sorted order. No duplicates allowed.
 */
class SortedIntSet : Externalizable {
    private var v: Vector<Int>

    constructor() {
        v = Vector()
    }

    /**
     * Add new value; return index inserted at if value was not already present, -1 if it was
     */
    fun add(n: Int): Int {
        val i = indexOf(n, false)
        return if (i != -1 && get(i) == n) {
            -1
        } else {
            v.insertElementAt(n, i + 1)
            i + 1
        }
    }

    /**
     * Remove a value; return index of item just removed if it was present, -1 if it was not
     */
    fun remove(n: Int): Int {
        val i = indexOf(n, true)
        if (i != -1) {
            v.removeElementAt(i)
        }
        return i
    }

    /**
     * Return value at index
     */
    operator fun get(i: Int): Int {
        return v.elementAt(i)
    }

    /**
     * Return whether value is present
     */
    fun contains(n: Int): Boolean {
        return indexOf(n, true) != -1
    }

    /**
     * If exact = true: return the index of a value, -1 if not present
     * If exact = false: return the index of the highest value <= the target value,
     * -1 if all values are greater than the target value
     */
    fun indexOf(n: Int, exact: Boolean): Int {
        var lo = 0
        var hi = v.size - 1

        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val value = get(mid)

            when {
                value < n -> lo = mid + 1
                value > n -> hi = mid - 1
                else -> return mid
            }
        }

        return if (exact) -1 else lo - 1
    }

    /**
     * Return number of values
     */
    fun size(): Int {
        return v.size
    }

    /**
     * Return underlying vector (outside modification may corrupt the datastructure)
     */
    fun getVector(): Vector<*> {
        return v
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        @Suppress("UNCHECKED_CAST")
        v = ExtUtil.read(`in`, ExtWrapList(Integer::class.java), pf) as Vector<Int>
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.write(out, ExtWrapList(v))
    }
}
