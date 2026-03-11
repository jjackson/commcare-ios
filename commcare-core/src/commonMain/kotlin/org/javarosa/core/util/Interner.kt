package org.javarosa.core.util

/**
 * An Interner is a special case of a cache table that is used to intern objects
 * which will exist in multiple contexts at runtime. All of the keys in an interner
 * are integer values.
 *
 * @author ctsims
 */
class Interner<K> : CacheTable<Int, K>() {

    /**
     * Intern the provided value in this cache table
     *
     * @param k The object to be interned
     * @return Either the original object (if it has been interned by this operation) or
     * an object that is identical to the one passed in. Note that the hashcode and equals
     * methods of the object type need to be correctly implemented for interning to function
     * as expected.
     */
    fun intern(k: K & Any): K {
        val hash = k.hashCode()
        val nk = retrieve(hash)
        if (nk == null) {
            register(hash, k)
            return k
        }

        return if (k == nk) {
            nk
        } else {
            // Collision. We should deal with this better for interning (and not manually caching) tables.
            k
        }
    }

    override fun retrieve(key: Int): K? {
        return super.retrieve(DataUtil.integer(key))
    }

    override fun register(key: Int, item: K) {
        super.register(DataUtil.integer(key), item)
    }
}
