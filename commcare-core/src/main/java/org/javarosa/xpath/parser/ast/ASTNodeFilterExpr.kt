package org.javarosa.xpath.parser.ast

import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.expr.XPathFilterExpr
import org.javarosa.xpath.parser.XPathSyntaxException
import java.util.Vector

class ASTNodeFilterExpr : ASTNode() {

    @JvmField
    var expr: ASTNodeAbstractExpr? = null

    @JvmField
    val predicates: Vector<ASTNode> = Vector()

    override fun getChildren(): List<ASTNode> {
        val list = ArrayList<ASTNode>()
        list.add(expr!!)
        for (e in predicates) {
            list.add(e)
        }
        return list
    }

    @Throws(XPathSyntaxException::class)
    override fun build(): XPathExpression {
        val preds = Array(predicates.size) { i ->
            predicates.elementAt(i).build()!!
        }

        return XPathFilterExpr(expr!!.build()!!, preds)
    }
}
