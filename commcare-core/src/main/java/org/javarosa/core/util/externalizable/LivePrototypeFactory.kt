package org.javarosa.core.util.externalizable

import org.javarosa.core.api.ClassNameHasher

/**
 * A prototype factory that is configured to keep track of all of the
 * case->hash pairs that it creates in order to use them for deserialization in
 * the future.
 *
 * Will only work reliably if it is used synchronously to hash all values that
 * are read, and should really only be expected to function for 'in memory'
 * storage like mocks.
 *
 * TODO: unify with Android storage live factory mocker
 *
 * @author ctsims
 */
class LivePrototypeFactory : PrototypeFactory {
    private val factoryTable = HashMap<String, Class<*>>()
    private val mLiveHasher: LiveHasher

    constructor() : this(ClassNameHasher())

    private constructor(hasher: Hasher) : super() {
        this.mLiveHasher = LiveHasher(this, hasher)
        PrototypeFactory.setStaticHasher(this.mLiveHasher)
    }

    override fun lazyInit() {
    }

    override fun addClass(c: Class<*>) {
        val hash = getLiveHasher().getHasher().getClassHashValue(c)
        factoryTable[ExtUtil.printBytes(hash)] = c
    }

    override fun getClass(hash: ByteArray): Class<*>? {
        val key = ExtUtil.printBytes(hash)
        return factoryTable[key]
    }

    override fun getInstance(hash: ByteArray): Any {
        return PrototypeFactory.getInstance(getClass(hash)!!)
    }

    private fun getLiveHasher(): LiveHasher {
        return this.mLiveHasher
    }

    private inner class LiveHasher(
        private val pf: LivePrototypeFactory,
        private val mHasher: Hasher
    ) : Hasher() {

        override fun getHashSize(): Int {
            return mHasher.getHashSize()
        }

        override fun getHash(c: Class<*>): ByteArray {
            val ret = mHasher.getClassHashValue(c)
            pf.addClass(c)
            return ret
        }

        fun getHasher(): Hasher {
            return mHasher
        }
    }
}
