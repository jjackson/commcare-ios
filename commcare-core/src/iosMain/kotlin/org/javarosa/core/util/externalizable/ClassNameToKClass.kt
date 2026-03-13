package org.javarosa.core.util.externalizable

import kotlin.reflect.KClass

/**
 * Global registry mapping class names to KClass instances for iOS.
 * On JVM this uses Class.forName().kotlin; on iOS we maintain a manual registry.
 */
object KClassRegistry {
    private val registry = mutableMapOf<String, KClass<*>>()

    fun register(className: String, kClass: KClass<*>) {
        registry[className] = kClass
    }

    fun lookup(className: String): KClass<*>? {
        return registry[className]
    }
}

actual fun classNameToKClass(className: String): KClass<*> {
    return KClassRegistry.lookup(className)
        ?: throw IllegalArgumentException(
            "Class '$className' not registered in KClassRegistry. " +
            "Register it at app startup via KClassRegistry.register()."
        )
}
