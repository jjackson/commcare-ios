package org.commcare.core.interfaces

import org.javarosa.core.model.instance.ExternalDataInstance
import java.util.UUID

/**
 * In memory implementation of VirtualDataInstanceStorage for use in the CLI and tests.
 */
open class MemoryVirtualDataInstanceStorage : VirtualDataInstanceStorage {

    private val storage: MutableMap<String, ExternalDataInstance> = HashMap()

    override fun write(dataInstance: ExternalDataInstance): String {
        val key = UUID.randomUUID().toString()
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
