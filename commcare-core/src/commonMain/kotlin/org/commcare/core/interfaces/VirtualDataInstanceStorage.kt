package org.commcare.core.interfaces

import org.javarosa.core.model.instance.ExternalDataInstance

/**
 * Read and write operations for entity selections made on a mult-select Entity Screen
 */
interface VirtualDataInstanceStorage {
    fun write(dataInstance: ExternalDataInstance): String

    fun write(key: String, dataInstance: ExternalDataInstance): String

    /**
     * Load an instance from storage.
     *
     * @param key The instance storage key
     * @param instanceId The instanceId to apply to the loaded instance
     * @param refId Unique reference id to apply to the loaded instance
     */
    fun read(key: String, instanceId: String, refId: String): ExternalDataInstance?

    fun contains(key: String): Boolean
}
