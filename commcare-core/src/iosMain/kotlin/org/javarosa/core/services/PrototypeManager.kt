@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.services

import org.javarosa.core.util.externalizable.PrototypeFactory

actual object PrototypeManager {
    private var registeredFactory: PrototypeFactory? = null

    fun registerFactory(factory: PrototypeFactory) {
        registeredFactory = factory
    }

    actual fun getDefault(): PrototypeFactory? {
        return registeredFactory
    }
}
