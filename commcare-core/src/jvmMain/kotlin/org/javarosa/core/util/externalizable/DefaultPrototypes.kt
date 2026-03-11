@file:JvmName("DefaultPrototypes")

package org.javarosa.core.util.externalizable

import org.javarosa.core.services.PrototypeManager

actual fun defaultPrototypes(): PrototypeFactory {
    return PrototypeManager.getDefault()!!
}
