package org.javarosa.core.services

import org.javarosa.core.util.externalizable.CannotCreateObjectException
import org.javarosa.core.util.externalizable.PrototypeFactory
import java.util.HashSet

object PrototypeManager {
    private val globalPrototypes: HashSet<String> = HashSet()

    private val threadLocalPrototypeFactory: ThreadLocal<PrototypeFactory?> =
        object : ThreadLocal<PrototypeFactory?>() {
            override fun initialValue(): PrototypeFactory? {
                return null
            }
        }

    private var globalStaticDefault: PrototypeFactory? = null

    private var useThreadLocalStrategy: Boolean = false

    @JvmStatic
    fun useThreadLocalStrategy(useThreadLocal: Boolean) {
        useThreadLocalStrategy = useThreadLocal
    }

    @JvmStatic
    fun registerPrototype(className: String) {
        globalPrototypes.add(className)

        try {
            PrototypeFactory.getInstance(Class.forName(className))
        } catch (e: ClassNotFoundException) {
            throw CannotCreateObjectException("$className: not found")
        }
        rebuild()
    }

    @JvmStatic
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

    @JvmStatic
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
                @Suppress("UNCHECKED_CAST")
                threadLocalPrototypeFactory.set(PrototypeFactory(globalPrototypes.clone() as HashSet<String>))
            } else {
                globalStaticDefault = PrototypeFactory(globalPrototypes)
            }
            return
        }
        synchronized(currentStaticFactory) {
            if (useThreadLocalStrategy) {
                @Suppress("UNCHECKED_CAST")
                threadLocalPrototypeFactory.set(PrototypeFactory(globalPrototypes.clone() as HashSet<String>))
            } else {
                globalStaticDefault = PrototypeFactory(globalPrototypes)
            }
        }
    }
}
