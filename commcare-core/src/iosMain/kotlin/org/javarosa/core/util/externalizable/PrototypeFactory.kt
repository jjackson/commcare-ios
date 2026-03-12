@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util.externalizable

import kotlin.reflect.KClass

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

    actual constructor(classNames: HashSet<String>) : this() {
        // On iOS, class names alone can't create instances — factory lambdas must be
        // registered via registerFactory(). This constructor exists for API compatibility.
        for (name in classNames) {
            val hash = computeHash(name)
            hashToName[hash.toList()] = name
        }
    }

    /**
     * Register a factory function for a serializable type.
     * @param className The fully-qualified class name (must match JVM class name for compatibility)
     * @param factory A function that creates a new empty instance of the type
     */
    fun registerFactory(className: String, factory: () -> Externalizable) {
        factories[className] = factory
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
    actual open fun getClassName(hash: ByteArray): String? {
        return hashToName[hash.toList()]
    }

    actual open fun createInstance(type: KClass<*>): Any {
        val name = type.qualifiedName
            ?: throw CannotCreateObjectException("No qualified name for $type")
        return getInstance(name)
    }

    actual open fun getInstance(hash: ByteArray): Any {
        val name = hashToName[hash.toList()]
            ?: throw CannotCreateObjectException("No class registered for hash")
        return getInstance(name)
    }

    /**
     * Simple hash computation matching Java's ClassNameHasher behavior.
     */
    private fun computeHash(className: String): ByteArray {
        val bytes = className.encodeToByteArray()
        val hash = ByteArray(DEFAULT_HASH_SIZE)
        for (i in bytes.indices) {
            hash[i % DEFAULT_HASH_SIZE] = (hash[i % DEFAULT_HASH_SIZE].toInt() xor bytes[i].toInt()).toByte()
        }
        return hash
    }

    actual companion object {
        private const val DEFAULT_HASH_SIZE = 4

        actual fun compareHash(a: ByteArray, b: ByteArray): Boolean {
            if (a.size != b.size) return false
            for (i in a.indices) {
                if (a[i] != b[i]) return false
            }
            return true
        }

        actual fun getWrapperTag(): ByteArray {
            val bytes = ByteArray(getClassHashSize())
            for (i in bytes.indices) {
                bytes[i] = 0xff.toByte()
            }
            return bytes
        }

        actual fun getClassHashSize(): Int = DEFAULT_HASH_SIZE

        actual fun getClassHashByName(className: String): ByteArray {
            return computeHashStatic(className)
        }

        actual fun getClassHashForType(type: KClass<*>): ByteArray {
            return getClassHashByName(type.qualifiedName ?: throw IllegalArgumentException("No name for $type"))
        }

        private fun computeHashStatic(className: String): ByteArray {
            val reversed = StringBuilder(className).reverse().toString()
            val bytes = reversed.encodeToByteArray()
            val hash = ByteArray(DEFAULT_HASH_SIZE)
            for (i in bytes.indices) {
                hash[i % DEFAULT_HASH_SIZE] = (hash[i % DEFAULT_HASH_SIZE].toInt() xor bytes[i].toInt()).toByte()
            }
            return hash
        }
    }
}
