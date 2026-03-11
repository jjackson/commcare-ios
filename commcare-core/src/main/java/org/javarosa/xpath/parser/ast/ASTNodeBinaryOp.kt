package org.javarosa.xpath.parser.ast
import kotlin.jvm.JvmField

import org.javarosa.xpath.expr.XPathArithExpr
import org.javarosa.xpath.expr.XPathBinaryOpExpr
import org.javarosa.xpath.expr.XPathBoolExpr
import org.javarosa.xpath.expr.XPathCmpExpr
import org.javarosa.xpath.expr.XPathEqExpr
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.expr.XPathUnionExpr
import org.javarosa.xpath.parser.Token
import org.javarosa.xpath.parser.XPathSyntaxException

class ASTNodeBinaryOp : ASTNode() {

    @JvmField
    var associativity: Int = 0

    @JvmField
    var exprs: List<out ASTNode> = ArrayList()

    @JvmField
    var ops: MutableList<Int> = ArrayList()

    override fun getChildren(): List<out ASTNode> {
        return exprs
    }

    @Throws(XPathSyntaxException::class)
    override fun build(): XPathExpression {
        var x: XPathExpression

        if (associativity == ASSOC_LEFT) {
            x = exprs[0].build()!!
            for (i in 1 until exprs.size) {
                x = getBinOpExpr(ops[i - 1], x, exprs[i].build()!!)
            }
        } else {
            x = exprs[exprs.size - 1].build()!!
            for (i in exprs.size - 2 downTo 0) {
                x = getBinOpExpr(ops[i], exprs[i].build()!!, x)
            }
        }

        return x
    }

    @Throws(XPathSyntaxException::class)
    private fun getBinOpExpr(op: Int, a: XPathExpression, b: XPathExpression): XPathBinaryOpExpr {
        return when (op) {
            Token.OR -> XPathBoolExpr(XPathBoolExpr.OR, a, b)
            Token.AND -> XPathBoolExpr(XPathBoolExpr.AND, a, b)
            Token.EQ -> XPathEqExpr(XPathEqExpr.EQ, a, b)
            Token.NEQ -> XPathEqExpr(XPathEqExpr.NEQ, a, b)
            Token.LT -> XPathCmpExpr(XPathCmpExpr.LT, a, b)
            Token.LTE -> XPathCmpExpr(XPathCmpExpr.LTE, a, b)
            Token.GT -> XPathCmpExpr(XPathCmpExpr.GT, a, b)
            Token.GTE -> XPathCmpExpr(XPathCmpExpr.GTE, a, b)
            Token.PLUS -> XPathArithExpr(XPathArithExpr.ADD, a, b)
            Token.MINUS -> XPathArithExpr(XPathArithExpr.SUBTRACT, a, b)
            Token.MULT -> XPathArithExpr(XPathArithExpr.MULTIPLY, a, b)
            Token.DIV -> XPathArithExpr(XPathArithExpr.DIVIDE, a, b)
            Token.MOD -> XPathArithExpr(XPathArithExpr.MODULO, a, b)
            Token.UNION -> XPathUnionExpr(a, b)
            else -> throw XPathSyntaxException()
        }
    }

    companion object {
        const val ASSOC_LEFT = 1
        const val ASSOC_RIGHT = 2
    }
}
