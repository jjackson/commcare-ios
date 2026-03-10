package org.javarosa.core.util.externalizable

/**
 * Abstract hashing class defining the basic outline of performing a hash. Hasher
 * implementations must override [getHash] and [getHashSize]. [getClassHashValue] handles
 * array creation and copying with these methods defined.
 *
 * @author ctsims
 * @author wspride
 */
abstract class Hasher {

    fun getClassHashValue(type: Class<*>): ByteArray {
        val returnHash = ByteArray(getHashSize())
        val computedHash = getHash(type) // add support for a salt, in case of collision?

        for (i in 0 until minOf(returnHash.size, computedHash.size)) {
            returnHash[i] = computedHash[i]
        }

        return returnHash
    }

    abstract fun getHashSize(): Int

    abstract fun getHash(c: Class<*>): ByteArray
}
