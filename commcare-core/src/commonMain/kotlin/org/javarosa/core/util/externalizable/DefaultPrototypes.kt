package org.javarosa.core.util.externalizable

/**
 * Returns the default PrototypeFactory for the current platform.
 * On JVM, delegates to PrototypeManager.getDefault().
 * On iOS, returns IosPrototypeFactory.
 */
expect fun defaultPrototypes(): PrototypeFactory
