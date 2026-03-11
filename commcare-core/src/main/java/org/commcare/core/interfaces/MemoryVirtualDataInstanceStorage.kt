package org.commcare.core.interfaces

import org.javarosa.core.model.instance.ExternalDataInstance
import kotlin.random.Random

/**
 * In memory implementation of VirtualDataInstanceStorage for use in the CLI and tests.
 */
open class MemoryVirtualDataInstanceStorage : VirtualDataInstanceStorage {

    private val storage: MutableMap<String, ExternalDataInstance> = HashMap()

    override fun write(dataInstance: ExternalDataInstance): String {
        val key = buildString {
            val chars = "0123456789abcdef"
            repeat(32) { i ->
                if (i == 8 || i == 12 || i == 16 || i == 20) append('-')
                append(chars[Random.nextInt(chars.length)])
            }
        }
        storage[key] = dataInstance
        return key
    }

    override fun write(key: String, dataInstance: ExternalDataInstance): String {
        if (contains(key)) {
            throw RuntimeException("Virtual instance with key '$key' already exists")
        }
        storage[key] = dataInstance
        return key
    }

    override fun read(key: String, instanceId: String, refId: String): ExternalDataInstance? {
        return storage[key]
    }

    override fun contains(key: String): Boolean {
        return storage.containsKey(key)
    }

    fun clear() {
        storage.clear()
    }
}
