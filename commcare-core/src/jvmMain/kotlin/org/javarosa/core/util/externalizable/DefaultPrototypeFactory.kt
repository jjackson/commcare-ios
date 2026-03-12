package org.javarosa.core.util.externalizable

import org.javarosa.core.services.PrototypeManager

actual fun defaultPrototypeFactory(): PrototypeFactory {
    return PrototypeManager.getDefault()!!
}
