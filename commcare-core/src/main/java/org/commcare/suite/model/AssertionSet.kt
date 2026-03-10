package org.commcare.suite.model

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * @author ctsims
 */
class AssertionSet : Externalizable {
    private lateinit var xpathExpressions: ArrayList<String>
    private lateinit var messages: ArrayList<Text>

    constructor()

    /**
     * Create a set of assertion tests.
     * NOTE: The tests are _not parsed here_ to test their xpath expressions.
     * They should be tested _before_ being passed in (we don't do so here
     * to permit retaining the locality of which expression failed).
     */
    constructor(xpathExpressions: ArrayList<String>, messages: ArrayList<Text>) {
        // First, make sure things are set up correctly
        if (xpathExpressions.size != messages.size) {
            throw IllegalArgumentException("Expression and message sets must be the same size")
        }

        this.xpathExpressions = xpathExpressions
        this.messages = messages
    }

    fun getAssertionFailure(ec: EvaluationContext): Text? {
        try {
            for (i in 0 until xpathExpressions.size) {
                val expression: XPathExpression = XPathParseTool.parseXPath(xpathExpressions[i])!!
                try {
                    val `val` = expression.eval(ec)
                    if (!FunctionUtils.toBoolean(`val`)) {
                        return messages[i]
                    }
                } catch (e: Exception) {
                    return messages[i]
                }
            }
            return null
        } catch (xpe: XPathSyntaxException) {
            throw XPathException("Assertion somehow failed to parse after validating : " + xpe.message)
        }
    }

    fun getAssertionsXPaths(): ArrayList<String> = this.xpathExpressions

    fun evalAssertionAtIndex(i: Int, expression: XPathExpression, ec: EvaluationContext): Text? {
        try {
            val `val` = expression.eval(ec)
            if (!FunctionUtils.toBoolean(`val`)) {
                return messages[i]
            }
        } catch (e: Exception) {
            return messages[i]
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        this.xpathExpressions = ExtUtil.read(`in`, ExtWrapList(String::class.java), pf) as ArrayList<String>
        this.messages = ExtUtil.read(`in`, ExtWrapList(Text::class.java), pf) as ArrayList<Text>
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.write(out, ExtWrapList(xpathExpressions))
        ExtUtil.write(out, ExtWrapList(messages))
    }
}
