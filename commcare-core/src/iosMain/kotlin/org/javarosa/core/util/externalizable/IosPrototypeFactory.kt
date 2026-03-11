package org.javarosa.core.util.externalizable

/**
 * iOS implementation of PrototypeFactory using explicit registration.
 * All serializable types must be registered at app startup via [registerType].
 */
class IosPrototypeFactory : PrototypeFactory() {

    private val factories = mutableMapOf<String, () -> Any>()
    private val hashes = mutableListOf<ByteArray>()

    /**
     * Register a serializable type with its class name hash and factory function.
     * Must be called at app startup for every type that may be deserialized.
     */
    fun registerType(hash: ByteArray, factory: () -> Any) {
        if (compareHash(hash, getWrapperTag())) {
            throw Error("Hash collision with reserved wrapper tag")
        }
        factories[hash.toHexString()] = factory
        hashes.add(hash)
    }

    override fun getInstance(hash: ByteArray): Any {
        val factory = factories[hash.toHexString()]
            ?: throw CannotCreateObjectException("No class registered for hash ${hash.toHexString()}")
        return factory()
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
