package org.javarosa.core.util.externalizable

import kotlin.reflect.KClass

/**
 * Convert a fully-qualified class name to a KClass.
 * Used during deserialization to reconstruct type information from stored class names.
 */
expect fun classNameToKClass(className: String): KClass<*>
