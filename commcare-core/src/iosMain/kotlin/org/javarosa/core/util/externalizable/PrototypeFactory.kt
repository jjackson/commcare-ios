@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util.externalizable

/**
 * iOS implementation of PrototypeFactory using a registration-based approach.
 * Instead of JVM reflection (Class.forName + newInstance), iOS uses a map of
 * pre-registered factory functions keyed by class name hash.
 *
 * All serializable types must be registered at app startup via [registerFactory].
 */
actual open class PrototypeFactory actual constructor() {

    private val factories = mutableMapOf<String, () -> Externalizable>()
    private val hashToName = mutableMapOf<List<Byte>, String>()

    /**
     * Register a factory function for a serializable type.
     * @param className The fully-qualified class name (must match JVM class name for compatibility)
     * @param factory A function that creates a new empty instance of the type
     */
    fun registerFactory(className: String, factory: () -> Externalizable) {
        factories[className] = factory
        // Also register by hash for hash-based lookup
        val hash = computeHash(className)
        hashToName[hash.toList()] = className
    }

    /**
     * Create a new instance of a registered type by class name.
     */
    fun getInstance(className: String): Externalizable {
        val factory = factories[className]
            ?: throw CannotCreateObjectException("$className: not registered in PrototypeFactory")
        return factory()
    }

    /**
     * Create a new instance of a registered type by hash.
     */
    fun getInstance(hash: ByteArray): Externalizable {
        val name = hashToName[hash.toList()]
            ?: throw CannotCreateObjectException("No class registered for hash")
        return getInstance(name)
    }

    /**
     * Simple hash computation matching Java's ClassNameHasher behavior.
     * Computes a hash of the class name for serialization lookup.
     */
    private fun computeHash(className: String): ByteArray {
        val bytes = className.encodeToByteArray()
        val hash = ByteArray(4)
        for (i in bytes.indices) {
            hash[i % 4] = (hash[i % 4].toInt() xor bytes[i].toInt()).toByte()
        }
        return hash
    }
}
