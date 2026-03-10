@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.services

import org.javarosa.core.util.externalizable.PrototypeFactory

expect object PrototypeManager {
    fun getDefault(): PrototypeFactory?
}
