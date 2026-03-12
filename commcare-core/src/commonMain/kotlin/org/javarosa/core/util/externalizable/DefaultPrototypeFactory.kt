package org.javarosa.core.util.externalizable

/**
 * Get the default PrototypeFactory for deserialization.
 * On JVM: delegates to PrototypeManager.getDefault().
 * On iOS: returns the global registered factory.
 */
expect fun defaultPrototypeFactory(): PrototypeFactory
