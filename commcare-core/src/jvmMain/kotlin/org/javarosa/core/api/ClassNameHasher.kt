package org.javarosa.core.api

import org.javarosa.core.util.externalizable.Hasher

/**
 * Most simple possible "hasher" - just gets the byte representation of the hash name
 *
 * @author wspride
 */
class ClassNameHasher : Hasher() {

    override fun getHash(c: Class<*>): ByteArray {
        return getHashByName(c.name)
    }

    override fun getHashByName(className: String): ByteArray {
        // reverse because the beginning of the classpath is less likely to be unique than the name
        return StringBuilder(className).reverse().toString().toByteArray()
    }

    override fun getHashSize(): Int {
        return CLASS_HASH_SIZE
    }

    companion object {
        private const val CLASS_HASH_SIZE = 32
    }
}
