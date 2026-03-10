@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util.externalizable

/**
 * iOS implementation of PrototypeFactory.
 * Uses a registration-based map of factory functions instead of JVM reflection.
 * All serializable types must be explicitly registered at app startup.
 */
actual open class PrototypeFactory {
    private val factories = mutableMapOf<String, () -> Externalizable>()
    private val hashes = mutableMapOf<String, ByteArray>()
    private val hashToName = mutableMapOf<String, String>()

    actual constructor()

    /**
     * Register a type with its factory function and class name.
     * Must be called at app startup for all serializable types.
     */
    fun registerType(className: String, factory: () -> Externalizable) {
        factories[className] = factory
        val hash = hashClassName(className)
        hashes[className] = hash
        hashToName[hash.toHexString()] = className
    }

    actual open fun getInstance(hash: ByteArray): Any {
        val hexHash = hash.toHexString()
        val className = hashToName[hexHash]
            ?: throw CannotCreateObjectException("No registered type for hash: $hexHash")
        val factory = factories[className]
            ?: throw CannotCreateObjectException("$className: no factory registered")
        return factory()
    }

    private fun hashClassName(name: String): ByteArray {
        val reversed = StringBuilder(name).reverse().toString()
        val bytes = reversed.encodeToByteArray()
        val hashSize = getClassHashSize()
        val result = ByteArray(hashSize)
        for (i in 0 until minOf(bytes.size, hashSize)) {
            result[i] = bytes[i]
        }
        return result
    }

    actual companion object {
        private var hashSize: Int = 4

        actual fun compareHash(a: ByteArray, b: ByteArray): Boolean {
            if (a.size != b.size) return false
            for (i in a.indices) {
                if (a[i] != b[i]) return false
            }
            return true
        }

        actual fun getClassHashSize(): Int = hashSize

        actual fun getWrapperTag(): ByteArray {
            val bytes = ByteArray(getClassHashSize())
            for (i in bytes.indices) {
                bytes[i] = 0xff.toByte()
            }
            return bytes
        }
    }
}

private fun ByteArray.toHexString(): String =
    joinToString("") { it.toInt().and(0xff).toString(16).padStart(2, '0') }
