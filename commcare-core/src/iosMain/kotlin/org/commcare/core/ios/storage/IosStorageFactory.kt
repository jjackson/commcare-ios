package org.commcare.core.ios.storage

import org.javarosa.core.services.storage.IStorageIndexedFactory
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.externalizable.PrototypeFactory
import kotlin.reflect.KClass

/**
 * iOS storage factory that creates in-memory storage instances.
 * Uses PrototypeFactory for instance creation from KClass.
 *
 * For production use, this should be replaced with a SQLite-backed factory.
 */
class IosStorageFactory(private val prototypeFactory: PrototypeFactory) : IStorageIndexedFactory {

    override fun newStorage(name: String, type: KClass<*>): IStorageUtilityIndexed<*> {
        @Suppress("UNCHECKED_CAST")
        val factory = {
            (prototypeFactory.createInstance(type) as? Persistable)
                ?: throw RuntimeException("Cannot create instance for type: ${type.simpleName}")
        }
        return IosInMemoryStorage(
            prototypeClass = type,
            instanceFactory = factory,
            pFactory = prototypeFactory
        )
    }
}
