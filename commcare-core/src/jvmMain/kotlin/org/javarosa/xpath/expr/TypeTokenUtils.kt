package org.javarosa.xpath.expr

import kotlin.reflect.KClass

/**
 * JVM implementation handles both KClass<*> (from Kotlin) and Class<*> (from Java) type tokens.
 */
actual fun isInstanceOfTypeToken(token: Any, value: Any): Boolean = when (token) {
    is KClass<*> -> token.isInstance(value)
    is Class<*> -> token.isInstance(value)
    else -> false
}

actual fun isTypeTokenEqual(token: Any, target: KClass<*>): Boolean = when (token) {
    is KClass<*> -> token == target
    is Class<*> -> token.kotlin == target
    else -> false
}
