package org.javarosa.core.util.externalizable

actual fun defaultPrototypes(): PrototypeFactory {
    return IosPrototypeFactory()
}
