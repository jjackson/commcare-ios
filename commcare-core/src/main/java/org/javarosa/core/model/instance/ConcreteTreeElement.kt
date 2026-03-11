package org.javarosa.core.model.instance

import org.javarosa.core.model.Constants
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.data.UncastData
import org.javarosa.core.model.instance.utils.ITreeVisitor
import org.javarosa.xpath.expr.XPathExpression
import kotlin.jvm.JvmField

/**
 * An element of a FormInstance.
 *
 * TreeElements represent an XML node in the instance. It may either have a value (e.g., <name>Drew</name>),
 * a number of TreeElement children (e.g., <meta><device /><timestamp /><user_id /></meta>), or neither (e.g.,
 * <empty_node />)
 *
 * TreeElements can also represent attributes. Attributes are unique from normal elements in that they are
 * not "children" of their parent, and are always leaf nodes: IE cannot have children.
 *
 * TODO: Split out the bind-able session data from this class and leave only the mandatory values to speed up
 * new DOM-like models
 *
 * @author Clayton Sims
 */
open class ConcreteTreeElement : AbstractTreeElement {

    @JvmField
    protected var name: String? = null // can be null only for hidden root node
    @JvmField
    protected var multiplicity: Int = -1 // see TreeReference for special values
    @JvmField
    protected var parent: AbstractTreeElement? = null

    @JvmField
    protected var value: IAnswerData? = null

    // I made all of these null again because there are so many treeelements that they
    // take up a huuuge amount of space together.
    @JvmField
    protected var attributes: ArrayList<AbstractTreeElement>? = null
    @JvmField
    protected var children: ArrayList<AbstractTreeElement>? = null

    /* model properties */
    @JvmField
    protected var dataType: Int = Constants.DATATYPE_NULL // TODO

    @JvmField
    protected var namespace: String? = null

    private var instanceName: String? = null

    /**
     * TreeElement with null name and 0 multiplicity? (a "hidden root" node?)
     */
    constructor() : this(null, TreeReference.DEFAULT_MUTLIPLICITY)

    constructor(name: String?) : this(name, TreeReference.DEFAULT_MUTLIPLICITY)

    constructor(name: String?, multiplicity: Int) {
        this.name = name
        this.multiplicity = multiplicity
        this.parent = null
    }

    override val isLeaf: Boolean
        get() = children == null || children!!.size == 0

    override val isChildable: Boolean
        get() = value == null

    override fun getInstanceName(): String? {
        // CTS: I think this is a better way to do this, although I really, really don't like the duplicated code
        val currentParent = parent
        if (currentParent != null) {
            return currentParent.getInstanceName()
        }
        return instanceName
    }

    fun setInstanceName(instanceName: String?) {
        this.instanceName = instanceName
    }

    open fun setValue(value: IAnswerData?) {
        if (isLeaf) {
            this.value = value
        } else {
            throw RuntimeException("Can't set data value for node that has children!")
        }
    }

    override fun getChild(name: String, multiplicity: Int): AbstractTreeElement? {
        val currentChildren = this.children ?: return null

        if (name == TreeReference.NAME_WILDCARD) {
            if (multiplicity == TreeReference.INDEX_TEMPLATE || currentChildren.size < multiplicity + 1) {
                return null
            }
            return currentChildren[multiplicity] // droos: i'm suspicious of this
        } else {
            for (i in 0 until currentChildren.size) {
                val child = currentChildren[i]
                if (name == child.getName() && child.getMult() == multiplicity) {
                    return child
                }
            }
        }

        return null
    }

    override fun getChildrenWithName(name: String): ArrayList<AbstractTreeElement> {
        return getChildrenWithName(name, false)
    }

    private fun getChildrenWithName(name: String, includeTemplate: Boolean): ArrayList<AbstractTreeElement> {
        val v = ArrayList<AbstractTreeElement>()
        val currentChildren = children ?: return v

        for (i in 0 until currentChildren.size) {
            val child = currentChildren[i]
            if ((child.getName() == name || name == TreeReference.NAME_WILDCARD) &&
                (includeTemplate || child.getMult() != TreeReference.INDEX_TEMPLATE)
            )
                v.add(child)
        }

        return v
    }

    override fun getNumChildren(): Int = children?.size ?: 0

    override fun hasChildren(): Boolean = getNumChildren() > 0

    override fun getChildAt(i: Int): AbstractTreeElement? = children!![i]

    fun setDataType(dataType: Int) {
        this.dataType = dataType
    }

    fun addChild(child: AbstractTreeElement) {
        addChild(child, false)
    }

    private fun addChild(child: AbstractTreeElement, checkDuplicate: Boolean) {
        if (!isChildable) {
            throw RuntimeException("Can't add children to node that has data value!")
        }

        if (child.getMult() == TreeReference.INDEX_UNBOUND) {
            throw RuntimeException("Cannot add child with an unbound index!")
        }

        if (checkDuplicate) {
            val existingChild = getChild(child.getName()!!, child.getMult())
            if (existingChild != null) {
                throw RuntimeException("Attempted to add duplicate child!")
            }
        }
        if (children == null) {
            children = ArrayList()
        }

        // try to keep things in order
        var i = children!!.size
        if (child.getMult() == TreeReference.INDEX_TEMPLATE) {
            val anchor = getChild(child.getName()!!, 0)
            if (anchor != null)
                i = children!!.indexOf(anchor)
        } else {
            val anchor = getChild(
                child.getName()!!,
                if (child.getMult() == 0) TreeReference.INDEX_TEMPLATE else child.getMult() - 1
            )
            if (anchor != null)
                i = children!!.indexOf(anchor) + 1
        }
        children!!.add(i, child)
    }

    fun removeChild(child: AbstractTreeElement) {
        children?.remove(child)
    }

    fun removeChildAt(i: Int) {
        children!!.removeAt(i)
    }

    override fun getChildMultiplicity(name: String): Int {
        return getChildrenWithName(name, false).size
    }

    /* ==== MODEL PROPERTIES ==== */

    /* ==== SPECIAL SETTERS (SETTERS WITH SIDE-EFFECTS) ==== */

    fun setAnswer(answer: IAnswerData?): Boolean {
        return if (value != null || answer != null) {
            setValue(answer)
            true
        } else {
            false
        }
    }

    /* ==== VISITOR PATTERN ==== */

    override fun accept(visitor: ITreeVisitor) {
        visitor.visit(this)

        val currentChildren = children ?: return
        val en = currentChildren.iterator()
        while (en.hasNext()) {
            (en.next() as ConcreteTreeElement).accept(visitor)
        }
    }

    /* ==== Attributes ==== */

    override fun getAttributeCount(): Int = attributes?.size ?: 0

    override fun getAttributeNamespace(index: Int): String? {
        return attributes!![index].getNamespace()
    }

    override fun getAttributeName(index: Int): String? {
        return attributes!![index].getName()
    }

    override fun getAttributeValue(index: Int): String? {
        return getAttributeValue(attributes!![index])
    }

    /**
     * Get the String value of the provided attribute
     */
    private fun getAttributeValue(attribute: AbstractTreeElement): String? {
        return attribute.getValue()?.uncast()?.getString()
    }

    override fun getAttribute(namespace: String?, name: String): AbstractTreeElement? {
        val currentAttributes = attributes ?: return null
        for (attribute in currentAttributes) {
            if (attribute.getName() == name && (namespace == null || namespace == attribute.getNamespace())) {
                return attribute
            }
        }
        return null
    }

    override fun getAttributeValue(namespace: String?, name: String): String? {
        val element = getAttribute(namespace, name)
        return if (element == null) null else getAttributeValue(element)
    }

    fun setAttribute(namespace: String?, name: String, value: String?) {
        if (attributes == null) {
            this.attributes = ArrayList()
        }
        for (i in attributes!!.size - 1 downTo 0) {
            val attribut = attributes!![i]
            if (attribut.getName() == name && (namespace == null || namespace == attribut.getNamespace())) {
                if (value == null) {
                    attributes!!.removeAt(i)
                } else {
                    attributes!!.removeAt(i)
                    val attr = TreeElement.constructAttributeElement(namespace, name)
                    attr.setValue(UncastData(value))
                    attr.setParent(this)
                }
                return
            }
        }

        val ns = namespace ?: ""

        val attr = TreeElement.constructAttributeElement(ns, name)
        attr.setValue(UncastData(value!!))
        attr.setParent(this)

        attributes!!.add(attr)
    }

    // return the tree reference that corresponds to this tree element
    var refCache: TreeReference? = null

    override fun getRef(): TreeReference {
        // TODO: Expire cache somehow;
        if (refCache == null) {
            refCache = TreeReference.buildRefFromTreeElement(this)
        }
        return refCache!!
    }

    override fun clearVolatiles() {
        refCache = null
        children?.let { kids ->
            for (child in kids) {
                child.clearVolatiles()
            }
        }
        attributes?.let { attrs ->
            for (attribute in attrs) {
                attribute.clearVolatiles()
            }
        }
    }

    override fun getName(): String? = name

    fun setName(name: String?) {
        this.name = name
    }

    override fun getMult(): Int = multiplicity

    fun setMult(multiplicity: Int) {
        this.multiplicity = multiplicity
    }

    fun setParent(parent: AbstractTreeElement?) {
        this.parent = parent
    }

    override fun getParent(): AbstractTreeElement? = parent

    override fun getValue(): IAnswerData? = value

    override fun toString(): String {
        val displayName = this.name ?: "NULL"
        val childrenCount = this.children?.size?.toString() ?: "-1"
        return "$displayName - Children: $childrenCount"
    }

    override fun getDataType(): Int = dataType

    fun getMultiplicity(): Int = multiplicity

    override fun getNamespace(): String? = namespace

    fun setNamespace(namespace: String?) {
        this.namespace = namespace
    }

    override fun tryBatchChildFetch(
        name: String,
        mult: Int,
        predicates: ArrayList<XPathExpression>,
        evalContext: EvaluationContext
    ): Collection<TreeReference>? = null

    override val isRepeatable: Boolean
        get() = true

    override val isAttribute: Boolean
        get() = false

    override val isRelevant: Boolean
        get() = true
}
