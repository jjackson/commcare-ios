package org.javarosa.core.util

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers

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

    operator fun get(i: Int): Int {
        return v[i]
    }

    fun contains(n: Int): Boolean {
        return indexOf(n, true) != -1
    }

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

    fun size(): Int {
        return v.size
    }

    fun getVector(): ArrayList<*> {
        return v
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        val size = SerializationHelpers.readNumeric(`in`).toInt()
        v = ArrayList(size)
        for (i in 0 until size) {
            v.add(SerializationHelpers.readNumeric(`in`).toInt())
        }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeNumeric(out, v.size.toLong())
        for (n in v) {
            SerializationHelpers.writeNumeric(out, n.toLong())
        }
    }
}
