package org.javarosa.core.util.externalizable

import kotlin.reflect.KClass

actual fun classNameToKClass(className: String): KClass<*> {
    // On iOS, class lookup is done through the PrototypeFactory registry.
    // This is a fallback that throws if the class isn't registered.
    throw UnsupportedOperationException(
        "Direct class name to KClass lookup not supported on iOS. " +
        "Register classes with PrototypeFactory instead."
    )
}
