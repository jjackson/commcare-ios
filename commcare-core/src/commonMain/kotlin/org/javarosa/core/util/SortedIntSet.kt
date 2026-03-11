package org.javarosa.core.util

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Maintain an array of integers in sorted order. No duplicates allowed.
 */
class SortedIntSet : Externalizable {
    private var v: ArrayList<Int>

    constructor() {
        v = ArrayList()
    }

    /**
     * Add new value; return index inserted at if value was not already present, -1 if it was
     */
    fun add(n: Int): Int {
        val i = indexOf(n, false)
        return if (i != -1 && get(i) == n) {
            -1
        } else {
            v.add(i + 1, n)
            i + 1
        }
    }

    /**
     * Remove a value; return index of item just removed if it was present, -1 if it was not
     */
    fun remove(n: Int): Int {
        val i = indexOf(n, true)
        if (i != -1) {
            v.removeAt(i)
        }
        return i
    }

    /**
     * Return value at index
     */
    operator fun get(i: Int): Int {
        return v[i]
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
    fun getVector(): ArrayList<*> {
        return v
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        val size = ExtUtil.readNumeric(`in`).toInt()
        v = ArrayList(size)
        for (i in 0 until size) {
            v.add(ExtUtil.readNumeric(`in`).toInt())
        }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeNumeric(out, v.size.toLong())
        for (n in v) {
            ExtUtil.writeNumeric(out, n.toLong())
        }
    }
}
