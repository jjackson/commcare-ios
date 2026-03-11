package org.javarosa.xpath.parser.ast
import kotlin.jvm.JvmField

import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.expr.XPathNumericLiteral
import org.javarosa.xpath.expr.XPathQName
import org.javarosa.xpath.expr.XPathStringLiteral
import org.javarosa.xpath.expr.XPathVariableReference
import org.javarosa.xpath.parser.Token
import org.javarosa.xpath.parser.XPathSyntaxException

class ASTNodeAbstractExpr : ASTNode() {

    @JvmField
    var content: MutableList<Any> = ArrayList()

    override fun getChildren(): List<ASTNode> {
        val children = ArrayList<ASTNode>()
        for (i in 0 until size()) {
            if (getType(i) == CHILD) {
                children.add(content[i] as ASTNode)
            }
        }
        return children
    }

    @Throws(XPathSyntaxException::class)
    override fun build(): XPathExpression {
        if (size() == 1) {
            if (getType(0) == CHILD) {
                return (content[0] as ASTNode).build()!!
            } else {
                return when (getTokenType(0)) {
                    Token.NUM -> XPathNumericLiteral(getToken(0)!!.`val` as Double)
                    Token.STR -> XPathStringLiteral(getToken(0)!!.`val` as String)
                    Token.VAR -> XPathVariableReference(getToken(0)!!.`val` as XPathQName)
                    else -> throw XPathSyntaxException()
                }
            }
        } else {
            throw XPathSyntaxException()
        }
    }

    private fun isTerminal(): Boolean {
        if (size() == 1) {
            val type = getTokenType(0)
            return type == Token.NUM || type == Token.STR || type == Token.VAR
        }
        return false
    }

    fun isNormalized(): Boolean {
        if (size() == 1 && getType(0) == CHILD) {
            val child = content[0] as ASTNode
            if (child is ASTNodePathStep || child is ASTNodePredicate) {
                throw RuntimeException("shouldn't happen")
            }
            return true
        }
        return isTerminal()
    }

    fun getType(i: Int): Int {
        val o = content[i]
        return when (o) {
            is Token -> TOKEN
            is ASTNode -> CHILD
            else -> -1
        }
    }

    fun getToken(i: Int): Token? {
        return if (getType(i) == TOKEN) content[i] as Token else null
    }

    fun getTokenType(i: Int): Int {
        val t = getToken(i)
        return t?.type ?: -1
    }

    //create new node containing children from [start,end)
    fun extract(start: Int, end: Int): ASTNodeAbstractExpr {
        val node = ASTNodeAbstractExpr()
        node.content = ArrayList(content.subList(start, end))
        return node
    }

    /**
     * remove children from [start,end) and replace with node n
     */
    fun condense(node: ASTNode, start: Int, end: Int) {
        for (i in end - 1 downTo start) {
            content.removeAt(i)
        }
        content.add(start, node)
    }

    /**
     * Replace contents (which should be just tokens) with a single node
     */
    fun condenseFull(node: ASTNode) {
        content.clear()
        content.add(node)
    }

    //find the next incidence of 'target' at the current stack level
    //start points to the opening of the current stack level
    fun indexOfBalanced(start: Int, target: Int, leftPush: Int, rightPop: Int): Int {
        var depth = 0
        var i = start + 1
        var found = false

        while (depth >= 0 && i < size()) {
            val type = getTokenType(i)

            if (depth == 0 && type == target) {
                found = true
                break
            }

            if (type == leftPush)
                depth++
            else if (type == rightPop)
                depth--

            i++
        }

        return if (found) i else -1
    }

    class Partition {
        @JvmField
        val pieces: MutableList<ASTNodeAbstractExpr> = ArrayList()

        @JvmField
        val separators: MutableList<Int> = ArrayList()
    }

    //partition the range [start,end), separating by any occurrence of separator
    fun partition(separators: IntArray, start: Int, end: Int): Partition {
        val part = Partition()
        val sepIdxs = ArrayList<Int>()

        for (i in start until end) {
            for (separator in separators) {
                if (getTokenType(i) == separator) {
                    part.separators.add(separator)
                    sepIdxs.add(i)
                    break
                }
            }
        }

        for (i in 0..sepIdxs.size) {
            val pieceStart = if (i == 0) start else sepIdxs[i - 1] + 1
            val pieceEnd = if (i == sepIdxs.size) end else sepIdxs[i]
            part.pieces.add(extract(pieceStart, pieceEnd))
        }

        return part
    }

    //partition by sep, to the end of the current stack level
    //start is the opening token of the current stack level
    fun partitionBalanced(sep: Int, start: Int, leftPush: Int, rightPop: Int): Partition? {
        val part = Partition()
        val sepIdxs = ArrayList<Int>()
        val end = indexOfBalanced(start, rightPop, leftPush, rightPop)
        if (end == -1) {
            return null
        }

        var k = start
        do {
            k = indexOfBalanced(k, sep, leftPush, rightPop)
            if (k != -1) {
                sepIdxs.add(k)
                part.separators.add(sep)
            }
        } while (k != -1)

        for (i in 0..sepIdxs.size) {
            val pieceStart = if (i == 0) start + 1 else sepIdxs[i - 1] + 1
            val pieceEnd = if (i == sepIdxs.size) end else sepIdxs[i]
            part.pieces.add(extract(pieceStart, pieceEnd))
        }

        return part
    }

    fun size(): Int {
        return content.size
    }

    /**
     * true if 'node' is potentially a step, as opposed to a filter expr
     */
    fun isStep(): Boolean {
        if (size() > 0) {
            val type = getTokenType(0)
            if (type == Token.QNAME ||
                type == Token.WILDCARD ||
                type == Token.NSWILDCARD ||
                type == Token.AT ||
                type == Token.DOT ||
                type == Token.DBL_DOT
            ) {
                return true
            } else if (content[0] is ASTNodeFunctionCall) {
                val name = (content[0] as ASTNodeFunctionCall).name.toString()
                return name == "node" || name == "text" || name == "comment" || name == "processing-instruction"
            } else {
                return false
            }
        }
        return false
    }

    companion object {
        const val CHILD = 1
        const val TOKEN = 2
    }
}
