package org.javarosa.xpath.parser.ast

import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.Token
import org.javarosa.xpath.parser.XPathSyntaxException

abstract class ASTNode {
    abstract fun getChildren(): List<out ASTNode>

    @Throws(XPathSyntaxException::class)
    abstract fun build(): XPathExpression?

    private var indent: Int = 0

    private fun printStr(s: String) {
        for (i in 0 until 2 * indent)
            print(" ")
        println(s)
    }

    fun print(o: Any?) {
        indent += 1

        if (o is ASTNodeAbstractExpr) {
            printStr("abstractexpr {")
            for (i in 0 until o.size()) {
                if (o.getType(i) == ASTNodeAbstractExpr.CHILD)
                    print(o.content[i])
                else
                    printStr(o.getToken(i).toString())
            }
            printStr("}")
        } else if (o is ASTNodePredicate) {
            printStr("predicate {")
            print(o.expr)
            printStr("}")
        } else if (o is ASTNodeFunctionCall) {
            if (o.args.isEmpty()) {
                printStr("func {${o.name}, args {none}}")
            } else {
                printStr("func {${o.name}, args {{")
                for (i in o.args.indices) {
                    print(o.args[i])
                    if (i < o.args.size - 1)
                        printStr(" } {")
                }
                printStr("}}}")
            }
        } else if (o is ASTNodeBinaryOp) {
            printStr("opexpr {")
            for (i in o.exprs.indices) {
                print(o.exprs[i])
                if (i < o.exprs.size - 1) {
                    when (o.ops[i]) {
                        Token.AND -> printStr("and:")
                        Token.OR -> printStr("or:")
                        Token.EQ -> printStr("eq:")
                        Token.NEQ -> printStr("neq:")
                        Token.LT -> printStr("lt:")
                        Token.LTE -> printStr("lte:")
                        Token.GT -> printStr("gt:")
                        Token.GTE -> printStr("gte:")
                        Token.PLUS -> printStr("plus:")
                        Token.MINUS -> printStr("minus:")
                        Token.DIV -> printStr("div:")
                        Token.MOD -> printStr("mod:")
                        Token.MULT -> printStr("mult:")
                        Token.UNION -> printStr("union:")
                    }
                }
            }
            printStr("}")
        } else if (o is ASTNodeUnaryOp) {
            printStr("opexpr {")
            when (o.op) {
                Token.UMINUS -> printStr("num-neg:")
            }
            print(o.expr)
            printStr("}")
        } else if (o is ASTNodeLocPath) {
            printStr("pathexpr {")
            val offset = if (o.isAbsolute()) 1 else 0
            for (i in 0 until o.clauses.size + offset) {
                if (offset == 0 || i > 0)
                    print(o.clauses[i - offset])
                if (i < o.separators.size) {
                    when (o.separators[i]) {
                        Token.DBL_SLASH -> printStr("dbl-slash:")
                        Token.SLASH -> printStr("slash:")
                    }
                }
            }
            printStr("}")
        } else if (o is ASTNodePathStep) {
            printStr("step {axis: ${o.axisType} node test type: ${o.nodeTestType}")
            if (o.axisType == ASTNodePathStep.AXIS_TYPE_EXPLICIT)
                printStr("  axis type: ${o.axisVal}")
            if (o.nodeTestType == ASTNodePathStep.NODE_TEST_TYPE_QNAME)
                printStr("  node test name: ${o.nodeTestQName}")
            if (o.nodeTestType == ASTNodePathStep.NODE_TEST_TYPE_FUNC) print(o.nodeTestFunc)
            printStr("predicates...")
            val e = o.predicates.iterator()
            while (e.hasNext())
                print(e.next())
            printStr("}")
        } else if (o is ASTNodeFilterExpr) {
            printStr("filter expr {")
            print(o.expr)
            printStr("predicates...")
            val e = o.predicates.iterator()
            while (e.hasNext())
                print(e.next())
            printStr("}")
        }

        indent -= 1
    }
}
