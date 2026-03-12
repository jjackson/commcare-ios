package org.javarosa.xpath.expr

import org.javarosa.xpath.parser.ast.PlatformFuncExprRegistry

/**
 * Register JVM-specific XPath functions that depend on libraries not available
 * in commonMain (gavaghan geodesy, javax.crypto, etc.).
 *
 * Must be called during JVM initialization before XPath parsing begins.
 * Called from XFormParser init and other entry points.
 */
object JvmXPathFunctions {
    private var registered = false

    @JvmStatic
    fun ensureRegistered() {
        if (registered) return
        registered = true

        // Register no-arg factories for FunctionUtils (used for auto-complete, etc.)
        FunctionUtils.registerXPathFunction(XPathDistanceFunc.NAME) { XPathDistanceFunc() }
        FunctionUtils.registerXPathFunction(XPathEncryptStringFunc.NAME) { XPathEncryptStringFunc() }
        FunctionUtils.registerXPathFunction(XPathDecryptStringFunc.NAME) { XPathDecryptStringFunc() }
        FunctionUtils.registerXPathFunction(XPathClosestPointOnPolygonFunc.NAME) { XPathClosestPointOnPolygonFunc() }
        FunctionUtils.registerXPathFunction(XPathIsPointInsidePolygonFunc.NAME) { XPathIsPointInsidePolygonFunc() }

        // Register builders for ASTNodeFunctionCall (used during XPath parsing)
        @Suppress("UNCHECKED_CAST")
        fun registerBuilder(name: String, builder: (Array<XPathExpression>) -> XPathFuncExpr) {
            PlatformFuncExprRegistry.register(name) { _, args ->
                builder(args as Array<XPathExpression>)
            }
        }

        registerBuilder("distance") { args -> XPathDistanceFunc(args) }
        registerBuilder("encrypt-string") { args -> XPathEncryptStringFunc(args) }
        registerBuilder("decrypt-string") { args -> XPathDecryptStringFunc(args) }
        registerBuilder("closest-point-on-polygon") { args -> XPathClosestPointOnPolygonFunc(args) }
        registerBuilder("is-point-inside-polygon") { args -> XPathIsPointInsidePolygonFunc(args) }
    }
}
