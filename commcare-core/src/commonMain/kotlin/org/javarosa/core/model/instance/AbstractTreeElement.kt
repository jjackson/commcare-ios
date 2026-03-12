package org.javarosa.core.model.instance

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.instance.utils.ITreeVisitor
import org.javarosa.xpath.expr.XPathExpression

interface AbstractTreeElement {

    val isLeaf: Boolean

    val isChildable: Boolean

    fun getInstanceName(): String?

    /**
     * Get a child element with the given name and occurence position (multiplicity)
     *
     * @param name         the name of the child element to select
     * @param multiplicity is the n-th occurence of an element with a given name
     */
    fun getChild(name: String, multiplicity: Int): AbstractTreeElement?

    /**
     * Get all the child nodes of this element, with specific name
     */
    fun getChildrenWithName(name: String): ArrayList<AbstractTreeElement>

    fun hasChildren(): Boolean

    fun getNumChildren(): Int

    fun getChildAt(i: Int): AbstractTreeElement?

    val isRepeatable: Boolean

    val isAttribute: Boolean

    fun getChildMultiplicity(name: String): Int

    /**
     * Visitor pattern acceptance method.
     *
     * @param visitor The visitor traveling this tree
     */
    fun accept(visitor: ITreeVisitor)

    /**
     * Returns the number of attributes of this element.
     */
    fun getAttributeCount(): Int

    /**
     * get namespace of attribute at 'index' in the vector
     */
    fun getAttributeNamespace(index: Int): String?

    /**
     * get name of attribute at 'index' in the vector
     */
    fun getAttributeName(index: Int): String?

    /**
     * get value of attribute at 'index' in the vector
     */
    fun getAttributeValue(index: Int): String?

    /**
     * Retrieves the TreeElement representing the attribute at
     * the provided namespace and name, or null if none exists.
     *
     * If 'null' is provided for the namespace, it will match the first
     * attribute with the matching name.
     */
    fun getAttribute(namespace: String?, name: String): AbstractTreeElement?

    /**
     * get value of attribute with namespace:name' in the vector
     */
    fun getAttributeValue(namespace: String?, name: String): String?

    // return the tree reference that corresponds to this tree element
    fun getRef(): TreeReference

    fun getName(): String?

    fun getMult(): Int

    // Support?
    fun getParent(): AbstractTreeElement?

    fun getValue(): IAnswerData?

    fun getDataType(): Int

    val isRelevant: Boolean

    fun getNamespace(): String?

    // clear any cache maintained by the TreeElement implementation
    fun clearVolatiles()

    /**
     * TODO: Worst method name ever. Don't use this unless you know what's up.
     * @param predicates  possibly list of predicates to be evaluated. predicates will be removed from list if they are
     *                    able to be evaluated
     */
    fun tryBatchChildFetch(
        name: String,
        mult: Int,
        predicates: ArrayList<XPathExpression>,
        evalContext: EvaluationContext
    ): Collection<TreeReference>?
}
