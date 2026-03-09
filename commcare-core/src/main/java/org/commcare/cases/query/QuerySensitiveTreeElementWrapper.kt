package org.commcare.cases.query

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.instance.utils.ITreeVisitor
import org.javarosa.xpath.expr.XPathExpression
import java.util.Vector

/**
 * Wraps an abstract tree element to provide no-op dispatch implementations for query sensitive
 * methods
 *
 * Created by ctsims on 9/19/2017.
 */
class QuerySensitiveTreeElementWrapper private constructor(
    private val wrapped: QuerySensitiveTreeElement,
    private val context: QueryContext
) : AbstractTreeElement {

    override val isLeaf: Boolean get() = wrapped.isLeaf

    override val isChildable: Boolean get() = wrapped.isChildable

    override fun getInstanceName(): String? = wrapped.getInstanceName()

    override fun getChild(name: String, multiplicity: Int): AbstractTreeElement? =
        wrapped.getChild(context, name, multiplicity)

    override fun getChildrenWithName(name: String): Vector<AbstractTreeElement> =
        wrapped.getChildrenWithName(name)

    override fun hasChildren(): Boolean = wrapped.hasChildren()

    override fun getNumChildren(): Int = wrapped.getNumChildren()

    override fun getChildAt(i: Int): AbstractTreeElement? = wrapped.getChildAt(i)

    override val isRepeatable: Boolean get() = wrapped.isRepeatable

    override val isAttribute: Boolean get() = wrapped.isAttribute

    override fun getChildMultiplicity(name: String): Int =
        wrapped.getChildMultiplicity(context, name)

    override fun accept(visitor: ITreeVisitor) = wrapped.accept(visitor)

    override fun getAttributeCount(): Int = wrapped.getAttributeCount()

    override fun getAttributeNamespace(index: Int): String? = wrapped.getAttributeNamespace(index)

    override fun getAttributeName(index: Int): String? = wrapped.getAttributeName(index)

    override fun getAttributeValue(index: Int): String? = wrapped.getAttributeValue(index)

    override fun getAttribute(namespace: String?, name: String): AbstractTreeElement? =
        wrapped.getAttribute(context, namespace, name)

    override fun getAttributeValue(namespace: String?, name: String): String? =
        wrapped.getAttributeValue(namespace, name)

    override fun getRef(): TreeReference = wrapped.getRef()

    override fun clearVolatiles() = wrapped.clearVolatiles()

    override fun getName(): String? = wrapped.getName()

    override fun getMult(): Int = wrapped.getMult()

    override fun getParent(): AbstractTreeElement? = wrapped.getParent()

    override fun getValue(): IAnswerData? = wrapped.getValue()

    override fun getDataType(): Int = wrapped.getDataType()

    override val isRelevant: Boolean get() = wrapped.isRelevant

    override fun getNamespace(): String? = wrapped.getNamespace()

    override fun tryBatchChildFetch(
        name: String,
        mult: Int,
        predicates: Vector<XPathExpression>,
        evalContext: EvaluationContext
    ): Collection<TreeReference>? =
        wrapped.tryBatchChildFetch(name, mult, predicates, evalContext)

    companion object {
        @JvmStatic
        fun WrapWithContext(element: AbstractTreeElement, context: QueryContext?): AbstractTreeElement {
            if (context == null) {
                return element
            }
            return if (element is QuerySensitiveTreeElement) {
                QuerySensitiveTreeElementWrapper(element, context)
            } else {
                element
            }
        }
    }
}
