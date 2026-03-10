@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util.externalizable

/**
 * Cross-platform prototype factory for serialization/deserialization.
 * On JVM, uses Class.forName() + newInstance() for reflection-based instantiation.
 * On iOS, uses a registration map of factory functions.
 *
 * Most Externalizable implementations receive this as an opaque parameter
 * and pass it through to ExtUtil or child readExternal() calls.
 */
expect open class PrototypeFactory() {
    /**
     * Get an instance of a class by its hash code.
     */
    open fun getInstance(hash: ByteArray): Any

    companion object {
        fun compareHash(a: ByteArray, b: ByteArray): Boolean
        fun getClassHashSize(): Int
        fun getWrapperTag(): ByteArray
    }
}
