package org.javarosa.core.model.instance

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.instance.utils.ITreeVisitor
import org.javarosa.xpath.expr.XPathExpression
import java.util.Vector

/**
 * @author ctsims
 */
class InstanceBase(private val instanceName: String?) : AbstractTreeElement {

    private var child: AbstractTreeElement? = null

    fun setChild(child: AbstractTreeElement?) {
        this.child = child
    }

    override val isLeaf: Boolean
        get() = false

    override val isChildable: Boolean
        get() = false

    override fun getInstanceName(): String? = instanceName

    override fun hasChildren(): Boolean = getNumChildren() > 0

    override fun getChild(name: String, multiplicity: Int): AbstractTreeElement? {
        val currentChild = child
        if (currentChild != null && name == currentChild.getName() && multiplicity == 0) {
            return currentChild
        }
        return null
    }

    override fun getChildrenWithName(name: String): Vector<AbstractTreeElement> {
        val children = Vector<AbstractTreeElement>()
        val currentChild = child
        if (currentChild != null && name == currentChild.getName()) {
            children.addElement(currentChild)
        }
        return children
    }

    override fun getNumChildren(): Int = 1

    override fun getChildAt(i: Int): AbstractTreeElement? {
        return if (i == 0) {
            child
        } else {
            null
        }
    }

    override val isRepeatable: Boolean
        get() = false

    override val isAttribute: Boolean
        get() = false

    override fun getChildMultiplicity(name: String): Int {
        val currentChild = child
        return if (currentChild != null && name == currentChild.getName()) {
            1
        } else {
            0
        }
    }

    override fun accept(visitor: ITreeVisitor) {
        child?.accept(visitor)
    }

    override fun getAttributeCount(): Int = 0

    override fun getAttributeNamespace(index: Int): String? = null

    override fun getAttributeName(index: Int): String? = null

    override fun getAttributeValue(index: Int): String? = null

    override fun getAttribute(namespace: String?, name: String): AbstractTreeElement? = null

    override fun getAttributeValue(namespace: String?, name: String): String? = null

    override fun getRef(): TreeReference = TreeReference.rootRef()

    override fun clearVolatiles() {
        child?.clearVolatiles()
    }

    override fun getName(): String? = null

    override fun getMult(): Int = TreeReference.DEFAULT_MUTLIPLICITY

    override fun getParent(): AbstractTreeElement? = null

    override fun getValue(): IAnswerData? = null

    override fun getDataType(): Int = 0

    override val isRelevant: Boolean
        get() = true

    override fun tryBatchChildFetch(
        name: String,
        mult: Int,
        predicates: Vector<XPathExpression>,
        evalContext: EvaluationContext
    ): Collection<TreeReference>? = null

    override fun getNamespace(): String? = null
}
