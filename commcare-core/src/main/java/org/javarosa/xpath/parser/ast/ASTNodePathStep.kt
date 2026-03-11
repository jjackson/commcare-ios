package org.javarosa.xpath.parser.ast
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmField

import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.expr.XPathQName
import org.javarosa.xpath.expr.XPathStep
import org.javarosa.xpath.parser.Token
import org.javarosa.xpath.parser.XPathSyntaxException

class ASTNodePathStep : ASTNode() {

    @JvmField
    var axisType: Int = 0

    @JvmField
    var axisVal: Int = 0

    @JvmField
    var nodeTestType: Int = 0

    @JvmField
    var nodeTestFunc: ASTNodeFunctionCall? = null

    @JvmField
    var nodeTestQName: XPathQName? = null

    @JvmField
    var nodeTestNamespace: String? = null

    @JvmField
    val predicates: ArrayList<ASTNode> = ArrayList()

    override fun getChildren(): ArrayList<ASTNode> {
        return predicates
    }

    override fun build(): XPathExpression? {
        return null
    }

    @Throws(XPathSyntaxException::class)
    fun getStep(): XPathStep {
        if (nodeTestType == NODE_TEST_TYPE_ABBR_DOT) {
            return XPathStep.ABBR_SELF()
        } else if (nodeTestType == NODE_TEST_TYPE_ABBR_DBL_DOT) {
            return XPathStep.ABBR_PARENT()
        } else {
            val step: XPathStep

            if (axisType == AXIS_TYPE_NULL) {
                axisVal = XPathStep.AXIS_CHILD
            } else if (axisType == AXIS_TYPE_ABBR) {
                axisVal = XPathStep.AXIS_ATTRIBUTE
            }

            if (nodeTestType == NODE_TEST_TYPE_QNAME) {
                step = XPathStep(axisVal, nodeTestQName!!)
            } else if (nodeTestType == NODE_TEST_TYPE_WILDCARD) {
                step = XPathStep(axisVal, XPathStep.TEST_NAME_WILDCARD)
            } else if (nodeTestType == NODE_TEST_TYPE_NSWILDCARD) {
                step = XPathStep(axisVal, nodeTestNamespace!!)
            } else {
                val funcName = nodeTestFunc!!.name.toString()
                val type: Int = when (funcName) {
                    "node" -> XPathStep.TEST_TYPE_NODE
                    "text" -> XPathStep.TEST_TYPE_TEXT
                    "comment" -> XPathStep.TEST_TYPE_COMMENT
                    "processing-instruction" -> XPathStep.TEST_TYPE_PROCESSING_INSTRUCTION
                    else -> throw RuntimeException()
                }

                step = XPathStep(axisVal, type)
                if (nodeTestFunc!!.args.isNotEmpty()) {
                    step.literal = (nodeTestFunc!!.args[0] as ASTNodeAbstractExpr).getToken(0)!!.`val` as String
                }
            }

            val preds = Array(predicates.size) { i ->
                predicates[i].build()!!
            }
            step.predicates = preds

            return step
        }
    }

    companion object {
        const val AXIS_TYPE_ABBR = 1
        const val AXIS_TYPE_EXPLICIT = 2
        const val AXIS_TYPE_NULL = 3

        const val NODE_TEST_TYPE_QNAME = 1
        const val NODE_TEST_TYPE_WILDCARD = 2
        const val NODE_TEST_TYPE_NSWILDCARD = 3
        const val NODE_TEST_TYPE_ABBR_DOT = 4
        const val NODE_TEST_TYPE_ABBR_DBL_DOT = 5
        const val NODE_TEST_TYPE_FUNC = 6

        @JvmStatic
        fun validateAxisName(axisName: String): Int {
            return when (axisName) {
                "child" -> XPathStep.AXIS_CHILD
                "descendant" -> XPathStep.AXIS_DESCENDANT
                "parent" -> XPathStep.AXIS_PARENT
                "ancestor" -> XPathStep.AXIS_ANCESTOR
                "following-sibling" -> XPathStep.AXIS_FOLLOWING_SIBLING
                "preceding-sibling" -> XPathStep.AXIS_PRECEDING_SIBLING
                "following" -> XPathStep.AXIS_FOLLOWING
                "preceding" -> XPathStep.AXIS_PRECEDING
                "attribute" -> XPathStep.AXIS_ATTRIBUTE
                "namespace" -> XPathStep.AXIS_NAMESPACE
                "self" -> XPathStep.AXIS_SELF
                "descendant-or-self" -> XPathStep.AXIS_DESCENDANT_OR_SELF
                "ancestor-or-self" -> XPathStep.AXIS_ANCESTOR_OR_SELF
                else -> -1
            }
        }

        @JvmStatic
        fun validateNodeTypeTest(f: ASTNodeFunctionCall): Boolean {
            val name = f.name.toString()
            if (name == "node" || name == "text" || name == "comment" || name == "processing-instruction") {
                if (f.args.isEmpty()) {
                    return true
                } else if (name == "processing-instruction" && f.args.size == 1) {
                    val x = f.args[0] as ASTNodeAbstractExpr
                    return x.size() == 1 && x.getTokenType(0) == Token.STR
                } else {
                    return false
                }
            } else {
                return false
            }
        }
    }
}
