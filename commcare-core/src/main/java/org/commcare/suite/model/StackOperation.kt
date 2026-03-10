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
import org.javarosa.xpath.parser.XPathSyntaxException
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream

import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * A stack operation descriptor, containing all of the relevant details
 * about which operation to perform and any associated metadata
 *
 * @author ctsims
 */
class StackOperation : Externalizable {
    private var opType: Int = 0
    private var ifCondition: String? = null
    private var elements: ArrayList<StackFrameStep> = ArrayList()

    /**
     * Deserialization Only!
     */
    constructor()

    // Copy Constructor
    constructor(oldStackOp: StackOperation) {
        this.opType = oldStackOp.opType
        this.ifCondition = oldStackOp.ifCondition
        this.elements = ArrayList(oldStackOp.elements.size)
        for (element in oldStackOp.elements) {
            elements.add(StackFrameStep(element))
        }
    }

    @Throws(XPathSyntaxException::class)
    private constructor(opType: Int, ifCondition: String?, elements: ArrayList<StackFrameStep>) {
        this.opType = opType
        this.ifCondition = ifCondition
        if (ifCondition != null) {
            XPathParseTool.parseXPath(ifCondition)
        }
        this.elements = elements
    }

    fun getOp(): Int = opType

    fun isOperationTriggered(ec: EvaluationContext): Boolean {
        val cond = ifCondition
        return if (cond != null) {
            try {
                FunctionUtils.toBoolean(XPathParseTool.parseXPath(cond)!!.eval(ec))
            } catch (e: XPathSyntaxException) {
                // This error makes no sense, since we parse the input for
                // validation when we create it!
                throw XPathException(e.message)
            }
        } else {
            true
        }
    }

    /**
     * Get the actual steps to be added (un-processed) to a frame.
     *
     * @return The definitions for the steps that should be included in this operation
     * @throws IllegalStateException if this operation do not support stack frame steps
     */
    fun getStackFrameSteps(): ArrayList<StackFrameStep> {
        if (opType == OPERATION_CLEAR) {
            throw IllegalStateException("Clear Operations do not define frame steps")
        }
        return elements
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        opType = ExtUtil.readInt(`in`)
        ifCondition = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        elements = ExtUtil.read(`in`, ExtWrapList(StackFrameStep::class.java), pf) as ArrayList<StackFrameStep>
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeNumeric(out, opType.toLong())
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(ifCondition))
        ExtUtil.write(out, ExtWrapList(elements))
    }

    override fun toString(): String {
        return "StackOperation $elements"
    }

    companion object {
        const val OPERATION_CREATE = 0
        const val OPERATION_PUSH = 1
        const val OPERATION_CLEAR = 2

        @JvmStatic
        @Throws(XPathSyntaxException::class)
        fun buildCreateFrame(
            ifCondition: String?,
            elements: ArrayList<StackFrameStep>
        ): StackOperation {
            return StackOperation(OPERATION_CREATE, ifCondition, elements)
        }

        @JvmStatic
        @Throws(XPathSyntaxException::class)
        fun buildPushFrame(
            ifCondition: String?,
            elements: ArrayList<StackFrameStep>
        ): StackOperation {
            return StackOperation(OPERATION_PUSH, ifCondition, elements)
        }

        @JvmStatic
        @Throws(XPathSyntaxException::class)
        fun buildClearFrame(ifCondition: String?): StackOperation {
            return StackOperation(OPERATION_CLEAR, ifCondition, ArrayList())
        }
    }
}
