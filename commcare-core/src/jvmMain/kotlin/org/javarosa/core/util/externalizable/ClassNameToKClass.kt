package org.javarosa.core.util.externalizable

import kotlin.reflect.KClass

actual fun classNameToKClass(className: String): KClass<*> {
    return Class.forName(className).kotlin
}
