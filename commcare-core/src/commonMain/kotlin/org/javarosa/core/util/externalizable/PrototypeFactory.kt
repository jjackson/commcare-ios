@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util.externalizable

import kotlin.reflect.KClass

/**
 * Factory for creating instances of serializable types by their hash codes.
 *
 * On JVM: Uses Class.forName + newInstance() with a Hasher-based registration system.
 * On iOS: Uses a pre-registered map of factory lambdas keyed by class name.
 */
expect open class PrototypeFactory() {

    /**
     * Secondary constructor accepting class names for lazy registration.
     * On JVM: stores names for lazy Class.forName initialization.
     * On iOS: stores names (registration still requires factory lambdas).
     */
    constructor(classNames: HashSet<String>)

    /**
     * Create a new instance of a registered type by its hash.
     */
    open fun getInstance(hash: ByteArray): Any

    /**
     * Look up a class name by its hash. Returns null if not registered.
     */
    open fun getClassName(hash: ByteArray): String?

    /**
     * Create a new instance of a type given its KClass.
     * On JVM: uses reflection. On iOS: looks up in factory registry.
     */
    open fun createInstance(type: KClass<*>): Any

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

        /**
         * Compute the hash for a class name.
         */
        fun getClassHashByName(className: String): ByteArray

        /**
         * Get the class hash for a KClass type.
         * On JVM: uses Class-based hash (preserves LiveHasher registration side effects).
         * On iOS: uses string-based hash computation.
         */
        fun getClassHashForType(type: KClass<*>): ByteArray
    }
}
