package org.javarosa.core.model.condition

import org.javarosa.core.model.FormDef
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.XPathException
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

class Condition : Triggerable {

    @JvmField
    var trueAction: Int = 0

    @JvmField
    var falseAction: Int = 0

    constructor() {
        // for externalization
    }

    constructor(
        expr: IConditionExpr?, trueAction: Int, falseAction: Int,
        contextRef: TreeReference?
    ) : this(expr, trueAction, falseAction, contextRef, ArrayList<TreeReference>())

    constructor(
        expr: IConditionExpr?, trueAction: Int, falseAction: Int,
        contextRef: TreeReference?, targets: ArrayList<TreeReference>
    ) : super(expr, contextRef) {
        this.trueAction = trueAction
        this.falseAction = falseAction
        this.targets = targets
    }

    override fun eval(instance: FormInstance?, evalContext: EvaluationContext?): Any {
        try {
            return expr!!.eval(instance, evalContext)
        } catch (e: XPathException) {
            e.setMessagePrefix("Display Condition Error: Error in calculation for " + contextRef!!.toString(true))
            throw e
        }
    }

    fun evalBool(model: FormInstance?, evalContext: EvaluationContext?): Boolean {
        return eval(model, evalContext) as Boolean
    }

    override fun apply(ref: TreeReference?, result: Any?, instance: FormInstance?, f: FormDef?) {
        val boolResult = result as Boolean
        performAction(instance!!.resolveReference(ref!!)!!, if (boolResult) trueAction else falseAction)
    }

    override fun canCascade(): Boolean {
        return trueAction == ACTION_SHOW || trueAction == ACTION_HIDE
    }

    override fun isCascadingToChildren(): Boolean {
        return trueAction == ACTION_SHOW || trueAction == ACTION_HIDE
    }

    private fun performAction(node: TreeElement, action: Int) {
        when (action) {
            ACTION_NULL -> {}
            ACTION_SHOW -> node.setRelevant(true)
            ACTION_HIDE -> node.setRelevant(false)
            ACTION_ENABLE -> node.setEnabled(true)
            ACTION_DISABLE -> node.setEnabled(false)
            ACTION_LOCK -> { /* not supported */ }
            ACTION_UNLOCK -> { /* not supported */ }
            ACTION_REQUIRE -> node.setRequired(true)
            ACTION_DONT_REQUIRE -> node.setRequired(false)
        }
    }

    /**
     * Conditions are equal if they have the same actions, expression, and
     * triggers, but NOT targets or context ref.
     */
    override fun equals(other: Any?): Boolean {
        if (other is Condition) {
            return (this === other ||
                    (this.trueAction == other.trueAction &&
                            this.falseAction == other.falseAction &&
                            super.equals(other)))
        }
        return false
    }

    override fun hashCode(): Int {
        return trueAction xor falseAction xor super.hashCode()
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(input: PlatformDataInputStream, pf: PrototypeFactory) {
        super.readExternal(input, pf)
        trueAction = ExtUtil.readInt(input)
        falseAction = ExtUtil.readInt(input)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        super.writeExternal(out)
        ExtUtil.writeNumeric(out, trueAction.toLong())
        ExtUtil.writeNumeric(out, falseAction.toLong())
    }

    override fun getDebugLabel(): String {
        return "relevant"
    }

    companion object {
        const val ACTION_NULL: Int = 0
        const val ACTION_SHOW: Int = 1
        const val ACTION_HIDE: Int = 2
        const val ACTION_ENABLE: Int = 3
        const val ACTION_DISABLE: Int = 4
        const val ACTION_LOCK: Int = 5
        const val ACTION_UNLOCK: Int = 6
        const val ACTION_REQUIRE: Int = 7
        const val ACTION_DONT_REQUIRE: Int = 8
    }
}
