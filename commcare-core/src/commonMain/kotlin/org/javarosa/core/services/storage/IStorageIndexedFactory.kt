package org.javarosa.core.services.storage

import kotlin.reflect.KClass

interface IStorageIndexedFactory {
    fun newStorage(name: String, type: KClass<*>): IStorageUtilityIndexed<*>
}
