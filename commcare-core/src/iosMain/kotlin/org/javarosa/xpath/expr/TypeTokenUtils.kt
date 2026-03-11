package org.javarosa.xpath.expr

import kotlin.reflect.KClass

/**
 * iOS implementation only handles KClass<*> type tokens.
 */
actual fun isInstanceOfTypeToken(token: Any, value: Any): Boolean =
    (token as? KClass<*>)?.isInstance(value) ?: false

actual fun isTypeTokenEqual(token: Any, target: KClass<*>): Boolean =
    token == target
