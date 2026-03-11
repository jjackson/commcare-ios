package org.javarosa.xpath.expr

import kotlin.reflect.KClass

/**
 * Cross-platform type token matching for XPath function prototype system.
 *
 * Type tokens can be KClass<*> (from Kotlin code) or Class<*> (from Java code on JVM).
 * These helpers abstract the matching so the prototype system works cross-platform.
 */
expect fun isInstanceOfTypeToken(token: Any, value: Any): Boolean
expect fun isTypeTokenEqual(token: Any, target: KClass<*>): Boolean
