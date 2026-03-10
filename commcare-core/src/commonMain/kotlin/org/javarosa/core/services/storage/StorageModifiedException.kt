package org.javarosa.core.services.storage

class StorageModifiedException : RuntimeException {
    constructor() : super()

    constructor(message: String?) : super(message)
}
