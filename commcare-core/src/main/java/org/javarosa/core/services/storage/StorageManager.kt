package org.javarosa.core.services.storage

import org.javarosa.core.services.Logger
import java.util.Hashtable

/**
 * Manages StorageProviders for JavaRosa, which maintain persistent
 * data on a device.
 *
 * Largely derived from Cell Life's RMSManager
 *
 * @author Clayton Sims
 */
class StorageManager(factory: IStorageIndexedFactory) {

    private val storageRegistry: Hashtable<String, IStorageUtilityIndexed<*>> = Hashtable()
    private var storageFactory: IStorageIndexedFactory? = null

    init {
        setStorageFactory(factory, false)
    }

    /**
     * Attempts to set the storage factory for the current environment and fails and dies if there
     * is already a storage factory set if specified. Should be used by actual applications who need to use
     * a specific storage factory and shouldn't tolerate being pre-empted.
     *
     * @param fact     An available storage factory.
     * @param mustWork true if it is intolerable for another storage factory to have been set. False otherwise
     */
    fun setStorageFactory(fact: IStorageIndexedFactory, mustWork: Boolean) {
        if (storageFactory == null) {
            storageFactory = fact
        } else {
            if (mustWork) {
                Logger.die(
                    "A Storage Factory had already been set when storage factory " + fact.javaClass.name
                            + " attempted to become the only storage factory",
                    RuntimeException("Duplicate Storage Factory set")
                )
            }
        }
    }

    fun registerStorage(key: String, type: Class<*>) {
        if (storageFactory == null) {
            throw RuntimeException("No storage factory has been set; I don't know what kind of storage utility to create. Either set a storage factory, or register your StorageUtilitys directly.")
        }
        if (storageRegistry.containsKey(key)) {
            if (storageRegistry[key]!!.getPrototype() == type) {
                return
            } else {
                throw RuntimeException(
                    String.format(
                        "Attempting to change storage type for key %s from type %s to type %s.",
                        key, storageRegistry[key]!!.getPrototype().name, type.name
                    )
                )
            }
        }

        storageRegistry[key] = storageFactory!!.newStorage(key, type)
    }

    fun getStorage(key: String): IStorageUtilityIndexed<*> {
        if (storageRegistry.containsKey(key)) {
            return storageRegistry[key]!!
        } else {
            throw RuntimeException("No storage utility has been registered to handle \"$key\"; you must register one first with StorageManager.registerStorage()")
        }
    }

    fun halt() {
        val e = storageRegistry.elements()
        while (e.hasMoreElements()) {
            e.nextElement().close()
        }
    }

    /**
     * Clear all registered elements of storage, including the factory.
     */
    fun forceClear() {
        halt()
        storageRegistry.clear()
        storageFactory = null
    }
}
