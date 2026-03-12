package org.javarosa.xpath.parser.ast

import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException

class ASTNodePredicate : ASTNode() {

    var expr: ASTNode? = null

    override fun getChildren(): List<ASTNode> {
        val v = ArrayList<ASTNode>()
        v.add(expr!!)
        return v
    }

    @Throws(XPathSyntaxException::class)
    override fun build(): XPathExpression? {
        return expr!!.build()
    }
}
