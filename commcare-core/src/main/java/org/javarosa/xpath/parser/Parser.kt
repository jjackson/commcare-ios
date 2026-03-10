package org.javarosa.xpath.parser

import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.expr.XPathQName
import org.javarosa.xpath.parser.ast.ASTNode
import org.javarosa.xpath.parser.ast.ASTNodeAbstractExpr
import org.javarosa.xpath.parser.ast.ASTNodeBinaryOp
import org.javarosa.xpath.parser.ast.ASTNodeFilterExpr
import org.javarosa.xpath.parser.ast.ASTNodeFunctionCall
import org.javarosa.xpath.parser.ast.ASTNodeLocPath
import org.javarosa.xpath.parser.ast.ASTNodePathStep
import org.javarosa.xpath.parser.ast.ASTNodePredicate
import org.javarosa.xpath.parser.ast.ASTNodeUnaryOp

object Parser {

    @JvmStatic
    @Throws(XPathSyntaxException::class)
    fun parse(tokens: List<Token>): XPathExpression? {
        val tree = buildParseTree(tokens)
        return tree.build()
    }

    @Throws(XPathSyntaxException::class)
    private fun buildParseTree(tokens: List<Token>): ASTNode {
        val root = ASTNodeAbstractExpr()

        root.content = ArrayList<Any>(tokens)

        parseFuncCalls(root)
        parseParens(root)
        parsePredicates(root)
        parseOperators(root)
        parsePathExpr(root)
        verifyBaseExpr(root)

        return root
    }

    private fun parseOperators(root: ASTNode) {
        val orOp = intArrayOf(Token.OR)
        val andOp = intArrayOf(Token.AND)
        val eqOps = intArrayOf(Token.EQ, Token.NEQ)
        val cmpOps = intArrayOf(Token.LT, Token.LTE, Token.GT, Token.GTE)
        val addOps = intArrayOf(Token.PLUS, Token.MINUS)
        val multOps = intArrayOf(Token.MULT, Token.DIV, Token.MOD)
        val unionOp = intArrayOf(Token.UNION)

        parseBinaryOp(root, orOp, ASTNodeBinaryOp.ASSOC_RIGHT)
        parseBinaryOp(root, andOp, ASTNodeBinaryOp.ASSOC_RIGHT)
        parseBinaryOp(root, eqOps, ASTNodeBinaryOp.ASSOC_LEFT)
        parseBinaryOp(root, cmpOps, ASTNodeBinaryOp.ASSOC_LEFT)
        parseBinaryOp(root, addOps, ASTNodeBinaryOp.ASSOC_LEFT)
        parseBinaryOp(root, multOps, ASTNodeBinaryOp.ASSOC_LEFT)
        parseUnaryOp(root, Token.UMINUS)
        parseBinaryOp(root, unionOp, ASTNodeBinaryOp.ASSOC_LEFT) /* 'a|-b' parses weird (as in, doesn't), but i think that's correct */
    }

    //find and condense all function calls in the current level, then do the same in lower levels
    @Throws(XPathSyntaxException::class)
    private fun parseFuncCalls(node: ASTNode) {
        if (node is ASTNodeAbstractExpr) {
            var i = 0
            while (i < node.size() - 1) {
                if (node.getTokenType(i + 1) == Token.LPAREN && node.getTokenType(i) == Token.QNAME) {
                    condenseFuncCall(node, i)
                }
                i++
            }
        }

        for (subNode in node.getChildren()) {
            parseFuncCalls(subNode)
        }
    }

    //i == index of token beginning func call (func name)
    @Throws(XPathSyntaxException::class)
    private fun condenseFuncCall(node: ASTNodeAbstractExpr, funcStart: Int) {
        val funcCall = ASTNodeFunctionCall(node.getToken(funcStart)!!.`val` as XPathQName)

        val funcEnd = node.indexOfBalanced(funcStart + 1, Token.RPAREN, Token.LPAREN, Token.RPAREN)
        if (funcEnd == -1) {
            throw XPathSyntaxException("Mismatched brackets or parentheses") //mismatched parens
        }

        val args = node.partitionBalanced(Token.COMMA, funcStart + 1, Token.LPAREN, Token.RPAREN)
        if (args!!.pieces.size == 1 && args.pieces[0].size() == 0) {
            //no arguments
        } else {
            //process arguments
            funcCall.args = args.pieces
        }

        node.condense(funcCall, funcStart, funcEnd + 1)
    }

    @Throws(XPathSyntaxException::class)
    private fun parseParens(node: ASTNode) {
        parseBalanced(node, object : SubNodeFactory() {
            override fun newNode(node: ASTNodeAbstractExpr): ASTNode {
                return node
            }
        }, Token.LPAREN, Token.RPAREN)
    }

    @Throws(XPathSyntaxException::class)
    private fun parsePredicates(node: ASTNode) {
        parseBalanced(node, object : SubNodeFactory() {
            override fun newNode(node: ASTNodeAbstractExpr): ASTNode {
                val p = ASTNodePredicate()
                p.expr = node
                return p
            }
        }, Token.LBRACK, Token.RBRACK)
    }

    private abstract class SubNodeFactory {
        abstract fun newNode(node: ASTNodeAbstractExpr): ASTNode
    }

    @Throws(XPathSyntaxException::class)
    private fun parseBalanced(node: ASTNode, snf: SubNodeFactory, lToken: Int, rToken: Int) {
        if (node is ASTNodeAbstractExpr) {
            var i = 0
            while (i < node.size()) {
                val type = node.getTokenType(i)
                if (type == rToken) {
                    throw XPathSyntaxException("Unbalanced brackets or parentheses!") //unbalanced
                } else if (type == lToken) {
                    val j = node.indexOfBalanced(i, rToken, lToken, rToken)
                    if (j == -1) {
                        throw XPathSyntaxException("mismatched brackets or parentheses!") //mismatched
                    }

                    node.condense(snf.newNode(node.extract(i + 1, j)), i, j + 1)
                }
                i++
            }
        }

        for (subNode in node.getChildren()) {
            parseBalanced(subNode, snf, lToken, rToken)
        }
    }

    private fun parseBinaryOp(node: ASTNode, ops: IntArray, associativity: Int) {
        if (node is ASTNodeAbstractExpr) {
            val part = node.partition(ops, 0, node.size())

            if (part.separators.size == 0) {
                //no occurrences of op
            } else {
                val binOp = ASTNodeBinaryOp()
                binOp.associativity = associativity
                binOp.exprs = part.pieces
                binOp.ops = part.separators

                node.condenseFull(binOp)
            }
        }

        for (subNode in node.getChildren()) {
            parseBinaryOp(subNode, ops, associativity)
        }
    }

    private fun parseUnaryOp(node: ASTNode, op: Int) {
        if (node is ASTNodeAbstractExpr) {
            if (node.size() > 0 && node.getTokenType(0) == op) {
                val unOp = ASTNodeUnaryOp()
                unOp.op = op
                unOp.expr = if (node.size() > 1) node.extract(1, node.size()) else ASTNodeAbstractExpr()
                node.condenseFull(unOp)
            }
        }

        for (subNode in node.getChildren()) {
            parseUnaryOp(subNode, op)
        }
    }

    @Throws(XPathSyntaxException::class)
    private fun parsePathExpr(node: ASTNode) {
        if (node is ASTNodeAbstractExpr) {
            val pathOps = intArrayOf(Token.SLASH, Token.DBL_SLASH)
            val part = node.partition(pathOps, 0, node.size())

            if (part.separators.size == 0) {
                //filter expression or standalone step
                if (node.isStep()) {
                    val step = parseStep(node)
                    val path = ASTNodeLocPath()
                    path.clauses.add(step)
                    node.condenseFull(path)
                } else {
                    //filter expr
                    val filt = parseFilterExp(node)
                    if (filt != null) {
                        node.condenseFull(filt)
                    }
                }
            } else {
                //path expression (but first clause may be filter expr)
                val path = ASTNodeLocPath()
                path.separators = part.separators

                if (part.separators.size == 1 && node.size() == 1 && part.separators[0] == Token.SLASH) {
                    //empty absolute path
                } else {
                    for (i in part.pieces.indices) {
                        val x = part.pieces[i]
                        if (x.isStep()) {
                            val step = parseStep(x)
                            path.clauses.add(step)
                        } else {
                            if (i == 0) {
                                if (x.size() == 0) {
                                    //absolute path expr; first clause is null
                                    /* do nothing */
                                } else {
                                    //filter expr
                                    val filt = parseFilterExp(x)
                                    if (filt != null)
                                        path.clauses.add(filt)
                                    else
                                        path.clauses.add(x)
                                }
                            } else {
                                throw XPathSyntaxException("Unexpected beginning of path")
                            }
                        }
                    }
                }
                node.condenseFull(path)
            }
        }

        for (subNode in node.getChildren()) {
            parsePathExpr(subNode)
        }
    }

    //please kill me
    @Throws(XPathSyntaxException::class)
    private fun parseStep(node: ASTNodeAbstractExpr): ASTNodePathStep {
        val step = ASTNodePathStep()
        if (node.size() == 1 && node.getTokenType(0) == Token.DOT) {
            step.axisType = ASTNodePathStep.AXIS_TYPE_NULL
            step.nodeTestType = ASTNodePathStep.NODE_TEST_TYPE_ABBR_DOT
        } else if (node.size() == 1 && node.getTokenType(0) == Token.DBL_DOT) {
            step.axisType = ASTNodePathStep.AXIS_TYPE_NULL
            step.nodeTestType = ASTNodePathStep.NODE_TEST_TYPE_ABBR_DBL_DOT
        } else {
            var i = 0
            if (node.size() > 0 && node.getTokenType(0) == Token.AT) {
                step.axisType = ASTNodePathStep.AXIS_TYPE_ABBR
                i += 1
            } else if (node.size() > 1 && node.getTokenType(0) == Token.QNAME && node.getTokenType(1) == Token.DBL_COLON) {
                val axisVal = ASTNodePathStep.validateAxisName(node.getToken(0)!!.`val`.toString())
                if (axisVal == -1) {
                    throw XPathSyntaxException("Invalid Axis: " + node.getToken(0)!!.`val`.toString())
                }
                step.axisType = ASTNodePathStep.AXIS_TYPE_EXPLICIT
                step.axisVal = axisVal
                i += 2
            } else {
                step.axisType = ASTNodePathStep.AXIS_TYPE_NULL
            }

            val tokenType = node.getTokenType(i)
            if (node.size() <= i) {
                throw XPathSyntaxException()
            }
            if (tokenType == Token.WILDCARD) {
                step.nodeTestType = ASTNodePathStep.NODE_TEST_TYPE_WILDCARD
            } else if (tokenType == Token.NSWILDCARD) {
                step.nodeTestType = ASTNodePathStep.NODE_TEST_TYPE_NSWILDCARD
                step.nodeTestNamespace = node.getToken(i)!!.`val` as String
            } else if (tokenType == Token.QNAME) {
                step.nodeTestType = ASTNodePathStep.NODE_TEST_TYPE_QNAME
                step.nodeTestQName = node.getToken(i)!!.`val` as XPathQName
            } else if (node.content[i] is ASTNodeFunctionCall) {
                if (!ASTNodePathStep.validateNodeTypeTest(node.content[i] as ASTNodeFunctionCall)) {
                    throw XPathSyntaxException()
                }
                step.nodeTestType = ASTNodePathStep.NODE_TEST_TYPE_FUNC
                step.nodeTestFunc = node.content[i] as ASTNodeFunctionCall
            } else {
                throw XPathSyntaxException()
            }
            i += 1

            while (i < node.size()) {
                if (node.content[i] is ASTNodePredicate) {
                    step.predicates.add(node.content[i] as ASTNodePredicate)
                } else {
                    throw XPathSyntaxException()
                }
                i++
            }
        }

        return step
    }

    @Throws(XPathSyntaxException::class)
    private fun parseFilterExp(node: ASTNodeAbstractExpr): ASTNodeFilterExpr? {
        val filt = ASTNodeFilterExpr()
        var i = node.size() - 1
        while (i >= 0) {
            if (node.content[i] is ASTNodePredicate) {
                filt.predicates.add(0, node.content[i] as ASTNodePredicate)
            } else {
                break
            }
            i--
        }

        if (filt.predicates.size == 0)
            return null

        filt.expr = node.extract(0, i + 1)
        return filt
    }

    @Throws(XPathSyntaxException::class)
    private fun verifyBaseExpr(node: ASTNode) {
        if (node is ASTNodeAbstractExpr) {
            if (!node.isNormalized()) {
                throw XPathSyntaxException("Bad node: $node")
            }
        }

        for (subNode in node.getChildren()) {
            verifyBaseExpr(subNode)
        }
    }
}
