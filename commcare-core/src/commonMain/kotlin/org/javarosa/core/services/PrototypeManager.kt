package org.javarosa.core.services

import org.javarosa.core.util.PlatformThreadLocal
import org.javarosa.core.util.platformSynchronized
import org.javarosa.core.util.externalizable.CannotCreateObjectException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.classNameToKClass
import kotlin.jvm.JvmStatic

object PrototypeManager {
    private val globalPrototypes: HashSet<String> = HashSet()

    private val threadLocalPrototypeFactory: PlatformThreadLocal<PrototypeFactory?> =
        PlatformThreadLocal { null }

    private var globalStaticDefault: PrototypeFactory? = null

    private var useThreadLocalStrategy: Boolean = false

    fun useThreadLocalStrategy(useThreadLocal: Boolean) {
        useThreadLocalStrategy = useThreadLocal
    }

    @JvmStatic
    fun registerPrototype(className: String) {
        globalPrototypes.add(className)

        try {
            val klass = classNameToKClass(className)
            PrototypeFactory().createInstance(klass)
        } catch (e: Exception) {
            throw CannotCreateObjectException("$className: not found")
        }
        rebuild()
    }

    fun registerPrototypes(classNames: Array<String>) {
        for (className in classNames) {
            registerPrototype(className)
        }
    }

    private fun getCurrentStaticFactory(): PrototypeFactory? {
        return if (useThreadLocalStrategy) {
            threadLocalPrototypeFactory.get()
        } else {
            globalStaticDefault
        }
    }

    fun getDefault(): PrototypeFactory? {
        if (getCurrentStaticFactory() == null) {
            rebuild()
        }
        return getCurrentStaticFactory()
    }

    private fun rebuild() {
        val currentStaticFactory = getCurrentStaticFactory()
        if (currentStaticFactory == null) {
            if (useThreadLocalStrategy) {
                threadLocalPrototypeFactory.set(PrototypeFactory(HashSet(globalPrototypes)))
            } else {
                globalStaticDefault = PrototypeFactory(globalPrototypes)
            }
            return
        }
        platformSynchronized(currentStaticFactory) {
            if (useThreadLocalStrategy) {
                threadLocalPrototypeFactory.set(PrototypeFactory(HashSet(globalPrototypes)))
            } else {
                globalStaticDefault = PrototypeFactory(globalPrototypes)
            }
        }
    }
}
