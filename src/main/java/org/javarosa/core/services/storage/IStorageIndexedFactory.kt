package org.javarosa.core.services.storage

interface IStorageIndexedFactory {
    fun newStorage(name: String, type: Class<*>): IStorageUtilityIndexed<*>
}
