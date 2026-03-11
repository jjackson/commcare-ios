@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util.externalizable

/**
 * Factory for creating instances of serializable types by their hash codes.
 *
 * On JVM: Uses Class.forName + newInstance() with a Hasher-based registration system.
 * On iOS: Uses a pre-registered map of factory lambdas keyed by class name.
 */
expect open class PrototypeFactory() {
    /**
     * Create a new instance of a registered type by its hash.
     */
    open fun getInstance(hash: ByteArray): Any

    companion object {
        /**
         * Compare two hash byte arrays for equality.
         */
        fun compareHash(a: ByteArray, b: ByteArray): Boolean

        /**
         * Get the reserved wrapper tag (all 0xFF bytes).
         */
        fun getWrapperTag(): ByteArray

        /**
         * Get the size of class hash codes in bytes.
         */
        fun getClassHashSize(): Int
    }
}
