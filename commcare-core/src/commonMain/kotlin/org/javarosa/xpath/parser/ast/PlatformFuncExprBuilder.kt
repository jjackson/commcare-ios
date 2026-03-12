package org.javarosa.xpath.parser.ast

/**
 * Registry for platform-specific XPath function builders.
 * JVM registers geo/crypto function builders at startup.
 * Uses Any type to avoid depending on XPathExpression/XPathFuncExpr
 * which are in main/java (not commonMain).
 */
object PlatformFuncExprRegistry {
    /**
     * Map of function name to builder lambda.
     * Builder signature: (name: String, args: Array<*>) -> Any?
     * Returns an XPathFuncExpr instance or null.
     */
    private val builders = mutableMapOf<String, (String, Array<*>) -> Any?>()

    fun register(name: String, builder: (String, Array<*>) -> Any?) {
        builders[name] = builder
    }

    fun build(name: String, args: Array<*>): Any? {
        return builders[name]?.invoke(name, args)
    }
}
