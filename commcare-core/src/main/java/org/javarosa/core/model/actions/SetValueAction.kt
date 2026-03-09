package org.javarosa.core.model.actions

import org.javarosa.core.model.FormDef
import org.javarosa.core.model.IFormElement
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.Recalculate
import org.javarosa.core.model.data.AnswerDataFactory
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xform.parse.IElementHandler
import org.javarosa.xpath.XPathNodeset
import org.javarosa.xpath.XPathTypeMismatchException
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.expr.XPathExpression
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * @author ctsims
 */
class SetValueAction : Action {

    // node that this action is targeting
    private var target: TreeReference? = null

    // the value to be assigned to the target when this action is triggered
    private var value: XPathExpression? = null

    private var explicitValue: String? = null

    constructor() : super() {
        // for externalization
    }

    constructor(target: TreeReference?, value: XPathExpression?) : super(ELEMENT_NAME) {
        this.target = target
        this.value = value
    }

    constructor(target: TreeReference?, explicitValue: String?) : super(ELEMENT_NAME) {
        this.target = target
        this.explicitValue = explicitValue
    }

    override fun processAction(model: FormDef, contextRef: TreeReference?): TreeReference? {
        val currentTarget = target ?: return null

        // Qualify the reference if necessary
        var targetReference =
            if (contextRef == null) currentTarget else currentTarget.contextualize(contextRef)

        //For now we only process setValue actions which are within the
        //context if a context is provided. This happens for repeats where
        //insert events should only trigger on the right nodes
        if (contextRef != null) {
            //Note: right now we're qualifying then testing parentage to see whether
            //there was a conflict, but it's not super clear whether this is a perfect
            //strategy
            if (!contextRef.isParentOf(targetReference!!, false)) {
                return null
            }
        }

        //TODO: either the target or the value's node might not exist here, catch and throw reasonably
        val context = EvaluationContext(model.getEvaluationContext(), targetReference)

        val failMessage = "Target of TreeReference ${currentTarget.toString(true)} could not be resolved!"

        if (targetReference!!.hasPredicates()) {
            //CTS: in theory these predicates could contain logic which breaks if the qualified ref
            //contains unbound repeats (IE: nested repeats).
            val references = context.expandReference(targetReference!!)!!
            if (references.size == 0) {
                //If after finding our concrete reference it is a template, this action is outside of the
                //scope of the current target, so we can leave.
                if (model.getMainInstance()!!.hasTemplatePath(currentTarget)) {
                    return null
                }
                throw NullPointerException(failMessage)
            } else if (references.size > 1) {
                throw XPathTypeMismatchException(
                    "XPath nodeset has more than one node [${XPathNodeset.printNodeContents(references)}]; Actions can only target a single node reference. Refine path expression to match only one node."
                )
            } else {
                targetReference = references.elementAt(0)
            }
        }

        val node = context.resolveReference(targetReference!!)
        if (node == null) {
            //After all that, there's still the possibility that the qualified reference contains
            //an unbound template, so see if such a reference could exist. Unfortunately this
            //won't be included in the above walk if the template is nested, since only the
            //top level template retains its subelement templates
            if (model.getMainInstance()!!.hasTemplatePath(currentTarget)) {
                return null
            } else {
                throw NullPointerException(failMessage)
            }
        }

        val currentExplicitValue = explicitValue
        val result: Any? = if (currentExplicitValue != null) {
            currentExplicitValue
        } else {
            FunctionUtils.unpack(value!!.eval(model.getMainInstance()!!, context))
        }

        //CTS: Is not clear whether we should be creating _another_ EC below with this newly qualified
        //ref or not. This logic used to come after the result was calculated.

        val dataType = node.getDataType()
        val value = Recalculate.wrapData(result, dataType)

        if (value == null) {
            model.setValue(null, targetReference!!)
        } else {
            val targetData = try {
                AnswerDataFactory.templateByDataType(dataType).cast(value.uncast())
            } catch (e: IllegalArgumentException) {
                val ne = XPathTypeMismatchException(
                    "Invalid data type in setvalue event targetting |${targetReference}|\nError: $e"
                )
                ne.initCause(e)
                throw ne
            }
            model.setValue(targetData, targetReference!!)
        }
        return targetReference
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        target = ExtUtil.read(`in`, TreeReference::class.java, pf) as TreeReference
        explicitValue = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        if (explicitValue == null) {
            value = ExtUtil.read(`in`, ExtWrapTagged(), pf) as XPathExpression
        }
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.write(out, target!!)

        ExtUtil.write(out, ExtUtil.emptyIfNull(explicitValue))
        if (explicitValue == null) {
            ExtUtil.write(out, ExtWrapTagged(value!!))
        }
    }

    companion object {
        const val ELEMENT_NAME: String = "setvalue"

        @JvmStatic
        fun getHandler(): IElementHandler {
            return IElementHandler { p, e, parent ->
                // the generic parseAction() method in XFormParser already checks to make sure
                // that parent is an IFormElement, and throws an exception if it is not
                p.parseSetValueAction((parent as IFormElement).getActionController()!!, e)
            }
        }
    }
}
