package org.javarosa.core.model

import org.javarosa.core.model.condition.IConditionExpr
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.util.restorable.RestoreUtils
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException


class ItemsetBinding : Externalizable {

    /**
     * note that storing both the ref and expr for everything is kind of redundant, but we're forced
     * to since it's nearly impossible to convert between the two w/o having access to the underlying
     * xform/xpath classes, which we don't from the core model project
     */

    @JvmField
    var nodesetRef: TreeReference? = null   //absolute ref of itemset source nodes
    @JvmField
    var nodesetExpr: IConditionExpr? = null //path expression for source nodes; may be relative, may contain predicates
    @JvmField
    var contextRef: TreeReference? = null   //context ref for nodesetExpr; ref of the control parent (group/formdef) of itemset question

    @JvmField
    var labelRef: TreeReference? = null     //absolute ref of label
    @JvmField
    var labelExpr: IConditionExpr? = null   //path expression for label; may be relative, no predicates
    @JvmField
    var labelIsItext: Boolean = false       //if true, content of 'label' is an itext id

    @JvmField
    var copyMode: Boolean = false           //true = copy subtree; false = copy string value
    @JvmField
    var copyRef: TreeReference? = null      //absolute ref to copy

    @JvmField
    var valueRef: TreeReference? = null     //absolute ref to value
    @JvmField
    var valueExpr: IConditionExpr? = null   //path expression for value; may be relative, no predicates (must be relative if copy mode)

    @JvmField
    var sortRef: TreeReference? = null      //absolute ref to sort
    @JvmField
    var sortExpr: IConditionExpr? = null    //path expression for sort; may be relative, no predicates (must be relative if copy mode)

    private var destRef: TreeReference? = null //ref that identifies the repeated nodes resulting from this itemset

    // dynamic choices, not serialized
    private var choices: ArrayList<SelectChoice>? = null

    fun getChoices(): ArrayList<SelectChoice>? {
        return choices
    }

    fun setChoices(choices: ArrayList<SelectChoice>?) {
        if (this.choices != null) {
            clearChoices()
        }
        this.choices = choices
        sortChoices()
    }

    private fun sortChoices() {
        val currentSortRef = sortRef
        val currentChoices = choices
        if (currentSortRef != null && currentChoices != null) {
            // Perform sort
            currentChoices.sortWith { choice1, choice2 ->
                choice1.evaluatedSortProperty!!.compareTo(choice2.evaluatedSortProperty!!)
            }

            // Re-set indices after sorting
            for (i in currentChoices.indices) {
                currentChoices[i].setIndex(i)
            }
        }
    }

    fun clearChoices() {
        this.choices = null
    }

    fun setDestRef(q: QuestionDef) {
        destRef = DataInstance.unpackReference(q.getBind()!!).clone()
        if (copyMode) {
            destRef!!.add(copyRef!!.getNameLast(), TreeReference.INDEX_UNBOUND)
        }
    }

    fun getDestRef(): TreeReference? {
        return destRef
    }

    fun getRelativeValue(): IConditionExpr? {
        val relRef: TreeReference? = if (copyRef == null) {
            valueRef //must be absolute in this case
        } else if (valueRef != null) {
            valueRef!!.relativize(copyRef!!)
        } else {
            null
        }

        return if (relRef != null) RestoreUtils.refToPathExpr(relRef) else null
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        nodesetRef = ExtUtil.read(`in`, TreeReference::class.java, pf) as TreeReference
        nodesetExpr = ExtUtil.read(`in`, ExtWrapTagged(), pf) as IConditionExpr
        contextRef = ExtUtil.read(`in`, TreeReference::class.java, pf) as TreeReference
        labelRef = ExtUtil.read(`in`, TreeReference::class.java, pf) as TreeReference
        labelExpr = ExtUtil.read(`in`, ExtWrapTagged(), pf) as IConditionExpr
        valueRef = ExtUtil.read(`in`, ExtWrapNullable(TreeReference::class.java), pf) as TreeReference?
        valueExpr = ExtUtil.read(`in`, ExtWrapNullable(ExtWrapTagged()), pf) as IConditionExpr?
        copyRef = ExtUtil.read(`in`, ExtWrapNullable(TreeReference::class.java), pf) as TreeReference?
        labelIsItext = ExtUtil.readBool(`in`)
        copyMode = ExtUtil.readBool(`in`)
        sortRef = ExtUtil.read(`in`, ExtWrapNullable(TreeReference::class.java), pf) as TreeReference?
        sortExpr = ExtUtil.read(`in`, ExtWrapNullable(ExtWrapTagged()), pf) as IConditionExpr?
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.write(out, nodesetRef!!)
        ExtUtil.write(out, ExtWrapTagged(nodesetExpr!!))
        ExtUtil.write(out, contextRef!!)
        ExtUtil.write(out, labelRef!!)
        ExtUtil.write(out, ExtWrapTagged(labelExpr!!))
        ExtUtil.write(out, ExtWrapNullable(valueRef))
        val localValueExpr = valueExpr
        ExtUtil.write(out, ExtWrapNullable(if (localValueExpr == null) null else ExtWrapTagged(localValueExpr)))
        ExtUtil.write(out, ExtWrapNullable(copyRef))
        ExtUtil.writeBool(out, labelIsItext)
        ExtUtil.writeBool(out, copyMode)
        ExtUtil.write(out, ExtWrapNullable(sortRef))
        val localSortExpr = sortExpr
        ExtUtil.write(out, ExtWrapNullable(if (localSortExpr == null) null else ExtWrapTagged(localSortExpr)))
    }
}
