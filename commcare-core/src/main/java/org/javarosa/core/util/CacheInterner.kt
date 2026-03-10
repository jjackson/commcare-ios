package org.javarosa.core.util

/**
 * A CacheInterner is a special case of a cache table that is used to intern objects
 * which will exist in multiple contexts at runtime. All of the keys in an interner
 * are integer values.
 *
 * This is the JVM implementation of [Interner] using WeakReference-based [CacheTable].
 *
 * @author ctsims
 */
class CacheInterner<K> : CacheTable<Int, K>(), Interner<K> {

    /**
     * Intern the provided value in this cache table
     *
     * @param k The object to be interned
     * @return Either the original object (if it has been interned by this operation) or
     * an object that is identical to the one passed in. Note that the hashcode and equals
     * methods of the object type need to be correctly implemented for interning to function
     * as expected.
     */
    override fun intern(k: K): K {
        val nonNullK = k ?: throw NullPointerException("Cannot intern null")
        synchronized(this) {
            val hash = nonNullK.hashCode()
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
    }

    override fun retrieve(key: Int): K? {
        synchronized(this) {
            return super.retrieve(DataUtil.integer(key))
        }
    }

    override fun register(key: Int, item: K) {
        synchronized(this) {
            super.register(DataUtil.integer(key), item)
        }
    }
}
