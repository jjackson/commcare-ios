package org.javarosa.core.util.externalizable

private val globalFactory = PrototypeFactory()

actual fun defaultPrototypeFactory(): PrototypeFactory {
    return globalFactory
}
