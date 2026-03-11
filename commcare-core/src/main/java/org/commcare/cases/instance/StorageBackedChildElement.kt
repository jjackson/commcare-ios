package org.commcare.cases.instance

import org.javarosa.core.util.platformSynchronized
import org.commcare.cases.query.QueryContext
import org.commcare.cases.query.QuerySensitiveTreeElement
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.data.StringData
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.instance.utils.ITreeVisitor
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.xpath.expr.XPathExpression

/**
 * Semi-structured TreeElement for direct child of an external data instance
 * that loads its data from a database.
 *
 * For example represents 'case' in the path "instance('casedb')/casedb/case"
 *
 * @author ctsims
 * @author Phillip Mates (pmates@dimagi.com)
 */
abstract class StorageBackedChildElement<Model : Externalizable> protected constructor(
    protected val parent: StorageInstanceTreeElement<Model, *>,
    protected val _mult: Int,
    protected var recordId: Int,
    protected var entityId: String?,
    protected val nameId: String
) : QuerySensitiveTreeElement {

    private var ref: TreeReference? = null
    private var numChildren = -1

    init {
        if (recordId == -1 && entityId == null) {
            throw RuntimeException("Cannot create a lazy case element with no lookup identifiers!")
        }
    }

    override val isLeaf: Boolean
        get() = false

    override val isChildable: Boolean
        get() = false

    override fun getInstanceName(): String? {
        return parent.getInstanceName()
    }

    override fun getChild(context: QueryContext, name: String, multiplicity: Int): TreeElement? {
        val cached = cache(context)
        val child = cached.getChild(name, multiplicity)
        if (multiplicity >= 0 && child == null) {
            val emptyNode = TreeElement(name)
            cached.addChild(emptyNode)
            emptyNode.setParent(cached)
            return emptyNode
        }
        return child
    }

    override fun getChild(name: String, multiplicity: Int): AbstractTreeElement? {
        val cached = cache()
        val child = cached.getChild(name, multiplicity)
        if (multiplicity >= 0 && child == null) {
            val emptyNode = TreeElement(name)
            cached.addChild(emptyNode)
            emptyNode.setParent(cached)
            return emptyNode
        }
        return child
    }

    override fun getChildrenWithName(name: String): ArrayList<AbstractTreeElement> {
        return cache().getChildrenWithName(name)
    }

    override fun hasChildren(): Boolean {
        return true
    }

    override fun getNumChildren(): Int {
        if (numChildren == -1) {
            numChildren = cache().getNumChildren()
        }
        return numChildren
    }

    override fun getChildAt(i: Int): AbstractTreeElement? {
        return cache().getChildAt(i)
    }

    override val isRepeatable: Boolean
        get() = false

    override val isAttribute: Boolean
        get() = false

    override fun getChildMultiplicity(name: String): Int {
        return cache().getChildMultiplicity(name)
    }

    override fun getChildMultiplicity(context: QueryContext, name: String): Int {
        return cache(context).getChildMultiplicity(name)
    }

    override fun accept(visitor: ITreeVisitor) {
        visitor.visit(this)
    }

    override fun getAttributeCount(): Int {
        //TODO: Attributes should be fixed and possibly only include meta-details
        return cache().getAttributeCount()
    }

    override fun getAttributeNamespace(index: Int): String? {
        return cache().getAttributeNamespace(index)
    }

    override fun getAttributeName(index: Int): String? {
        return cache().getAttributeName(index)
    }

    override fun getAttributeValue(index: Int): String? {
        return cache().getAttributeValue(index)
    }

    override fun tryBatchChildFetch(
        name: String,
        mult: Int,
        predicates: ArrayList<XPathExpression>,
        evalContext: EvaluationContext
    ): Collection<TreeReference>? {
        //TODO: We should be able to catch the index case here?
        return null
    }

    override fun getNamespace(): String? {
        return null
    }

    override val isRelevant: Boolean
        get() = true

    override fun getMult(): Int {
        return _mult
    }

    override fun getParent(): AbstractTreeElement? {
        return parent
    }

    override fun getValue(): IAnswerData? {
        return null
    }

    override fun getDataType(): Int {
        return 0
    }

    override fun getRef(): TreeReference {
        if (ref == null) {
            ref = TreeReference.buildRefFromTreeElement(this)
        }
        return ref!!
    }

    override fun clearVolatiles() {
        ref = null
        val cachedElement = cache()
        cachedElement?.clearVolatiles()
    }

    //Context Sensitive Methods
    override fun getAttribute(context: QueryContext, namespace: String?, name: String): AbstractTreeElement? {
        if (name == nameId) {
            if (recordId != TreeReference.INDEX_TEMPLATE) {
                //if we're already cached, don't bother with this nonsense
                platformSynchronized(parent.treeCache) {
                    val element = parent.treeCache.retrieve(recordId)
                    if (element != null) {
                        return cache(context).getAttribute(namespace, name)
                    }
                }
            }

            //TODO: CACHE GET ID THING
            val eid = entityId
            if (eid == null) {
                return cache(context).getAttribute(namespace, name)
            }

            //otherwise, don't cache this just yet if we have the ID handy
            val entity = TreeElement.constructAttributeElement(null, name)
            entity.setValue(StringData(eid))
            entity.setParent(this)
            return entity
        }
        return cache(context).getAttribute(namespace, name)
    }

    override fun getAttribute(namespace: String?, name: String): AbstractTreeElement? {
        if (name == nameId) {
            if (recordId != TreeReference.INDEX_TEMPLATE) {
                platformSynchronized(parent.treeCache) {
                    val element = parent.treeCache.retrieve(recordId)
                    if (element != null) {
                        return cache().getAttribute(namespace, name)
                    }
                }
            }

            val eid = entityId
            if (eid == null) {
                return cache().getAttribute(namespace, name)
            }

            val entity = TreeElement.constructAttributeElement(null, name)
            entity.setValue(StringData(eid))
            entity.setParent(this)
            return entity
        }
        return cache().getAttribute(namespace, name)
    }

    override fun getAttributeValue(namespace: String?, name: String): String? {
        if (name == nameId) {
            return entityId
        }
        return cache().getAttributeValue(namespace, name)
    }

    protected fun cache(): TreeElement {
        return cache(null)
    }

    protected abstract fun cache(context: QueryContext?): TreeElement
}
