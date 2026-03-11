package org.javarosa.core.util.externalizable

/**
 * Cross-platform base class for the prototype factory used in serialization.
 *
 * Provides the common API that Externalizable implementations need:
 * - getInstance(hash) to create objects during deserialization
 * - Companion methods for hash comparison and wrapper tags
 *
 * On JVM, use JvmPrototypeFactory which adds Class.forName()/newInstance() support.
 * On iOS, use IosPrototypeFactory which uses explicit registration.
 */
open class PrototypeFactory {

    protected var initialized: Boolean = false

    /**
     * Create an instance of the class identified by its hash.
     * Subclasses must override with platform-specific instantiation.
     */
    open fun getInstance(hash: ByteArray): Any {
        throw CannotCreateObjectException("No class registered for hash ${hash.contentToString()}")
    }

    companion object {
        private var _hashSize: Int = 32

        fun compareHash(a: ByteArray, b: ByteArray): Boolean {
            if (a.size != b.size) {
                return false
            }
            for (i in a.indices) {
                if (a[i] != b[i]) {
                    return false
                }
            }
            return true
        }

        fun getClassHashSize(): Int = _hashSize

        fun setClassHashSize(size: Int) {
            _hashSize = size
        }

        fun getWrapperTag(): ByteArray {
            val bytes = ByteArray(getClassHashSize())
            for (i in bytes.indices) {
                bytes[i] = 0xff.toByte()
            }
            return bytes
        }
    }
}
