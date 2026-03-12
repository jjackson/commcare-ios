package org.javarosa.xpath.expr

import org.javarosa.xpath.parser.ast.PlatformFuncExprRegistry

/**
 * Register JVM-specific XPath functions that depend on libraries not available
 * in commonMain (javax.crypto, etc.).
 *
 * Must be called during JVM initialization before XPath parsing begins.
 * Called from XFormParser init and other entry points.
 *
 * Note: Geo functions (closest-point-on-polygon, is-point-inside-polygon) have been
 * moved to commonMain and are now registered directly in FunctionUtils and ASTNodeFunctionCall.
 */
object JvmXPathFunctions {
    private var registered = false

    @JvmStatic
    fun ensureRegistered() {
        if (registered) return
        registered = true

        // Register no-arg factories for FunctionUtils (used for auto-complete, etc.)
        FunctionUtils.registerXPathFunction(XPathEncryptStringFunc.NAME) { XPathEncryptStringFunc() }
        FunctionUtils.registerXPathFunction(XPathDecryptStringFunc.NAME) { XPathDecryptStringFunc() }

        // Register builders for ASTNodeFunctionCall (used during XPath parsing)
        @Suppress("UNCHECKED_CAST")
        fun registerBuilder(name: String, builder: (Array<XPathExpression>) -> XPathFuncExpr) {
            PlatformFuncExprRegistry.register(name) { _, args ->
                builder(args as Array<XPathExpression>)
            }
        }

        registerBuilder("encrypt-string") { args -> XPathEncryptStringFunc(args) }
        registerBuilder("decrypt-string") { args -> XPathDecryptStringFunc(args) }
    }
}
