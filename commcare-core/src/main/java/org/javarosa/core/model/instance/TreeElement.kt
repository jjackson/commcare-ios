package org.javarosa.core.model.instance

import org.javarosa.core.model.Constants
import org.javarosa.core.model.FormDef
import org.javarosa.core.model.condition.Constraint
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.data.AnswerDataFactory
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.data.UncastData
import org.javarosa.core.model.instance.utils.ITreeVisitor
import org.javarosa.core.model.instance.utils.TreeUtilities
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.expr.XPathPathExpr
import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

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
open class TreeElement : Externalizable, AbstractTreeElement {

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
    protected var attributes: ArrayList<TreeElement>? = null
    @JvmField
    protected var children: ArrayList<TreeElement>? = null

    /* model properties */
    @JvmField
    protected var dataType: Int = Constants.DATATYPE_NULL // TODO

    private var constraint: Constraint? = null
    private var preloadHandler: String? = null
    private var preloadParams: String? = null

    private var flags: Int = MASK_RELEVANT or MASK_ENABLED or MASK_RELEVANT_INH or MASK_ENABLED_INH

    @JvmField
    protected var namespace: String? = null

    private var instanceName: String? = null

    // TODO: This is probably silly because this object is likely already
    // not thread safe in any way. Also, we should be wrapping all of the
    // setters.
    private val refCache = arrayOfNulls<TreeReference>(1)

    /**
     * An optional mapping of this element's children based on a path step
     * result that can be used to quickly index child nodes
     */
    private var mChildStepMapping: HashMap<XPathPathExpr, HashMap<String, Array<TreeElement>>>? = null

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
        expireReferenceCache()
        this.instanceName = instanceName
    }

    fun setValue(value: IAnswerData?) {
        if (isLeaf) {
            this.value = value
        } else {
            throw RuntimeException("Can't set data value for node that has children!")
        }
    }

    override fun getChild(name: String, multiplicity: Int): TreeElement? {
        val currentChildren = children ?: return null

        if (TreeReference.NAME_WILDCARD == name) {
            if (multiplicity == TreeReference.INDEX_TEMPLATE || currentChildren.size < multiplicity + 1) {
                return null
            }
            return currentChildren[multiplicity] // droos: i'm suspicious of this
        } else {
            for (child in currentChildren) {
                if (child.getMult() == multiplicity &&
                    (name.hashCode() == child.getName().hashCode() && name == child.getName())
                ) {
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

        for (child in currentChildren) {
            if ((child.getName() == name || name == TreeReference.NAME_WILDCARD) &&
                (includeTemplate || child.getMult() != TreeReference.INDEX_TEMPLATE)
            )
                v.add(child)
        }

        return v
    }

    override fun getNumChildren(): Int = children?.size ?: 0

    override fun hasChildren(): Boolean = getNumChildren() > 0

    override fun getChildAt(i: Int): TreeElement? = children!![i]

    override val isRepeatable: Boolean
        get() = getMaskVar(MASK_REPEATABLE)

    override val isAttribute: Boolean
        get() = getMaskVar(MASK_ATTRIBUTE)

    fun setDataType(dataType: Int) {
        this.dataType = dataType
    }

    fun addChild(child: TreeElement) {
        addChild(child, false)
    }

    fun addChild(child: TreeElement, assumeUniqueChildNames: Boolean) {
        if (!isChildable) {
            throw RuntimeException("Can't add children to node that has data value!")
        }

        if (child.multiplicity == TreeReference.INDEX_UNBOUND) {
            throw RuntimeException("Cannot add child with an unbound index!")
        }

        if (children == null) {
            children = ArrayList()
        }

        // try to keep things in order
        var i = children!!.size
        if (child.getMult() == TreeReference.INDEX_TEMPLATE) {
            val anchor = getChild(child.getName()!!, 0)
            if (anchor != null) {
                i = referenceIndexOf(children!!, anchor)
            }
        } else if (!assumeUniqueChildNames) {
            val anchor = getChild(
                child.getName()!!,
                if (child.getMult() == 0) TreeReference.INDEX_TEMPLATE else child.getMult() - 1
            )
            if (anchor != null) {
                i = referenceIndexOf(children!!, anchor) + 1
            }
        }
        children!!.add(i, child)

        initAddedSubNode(child)
    }

    private fun initAddedSubNode(node: TreeElement) {
        node.setParent(this)
        node.setRelevant(isRelevant, true)
        node.setEnabled(isEnabled(), true)
        node.setInstanceName(getInstanceName())
    }

    fun removeChild(child: TreeElement) {
        children?.remove(child)
    }

    fun removeChildAt(i: Int) {
        children!!.removeAt(i)
    }

    override fun getChildMultiplicity(name: String): Int {
        return getChildrenWithName(name, false).size
    }

    fun shallowCopy(): TreeElement {
        val newNode = TreeElement(name, multiplicity)
        newNode.parent = parent
        newNode.setRepeatable(this.isRepeatable)
        newNode.dataType = dataType

        // Just set the flag? side effects?
        newNode.setMaskVar(MASK_RELEVANT, this.getMaskVar(MASK_RELEVANT))
        newNode.setMaskVar(MASK_REQUIRED, this.getMaskVar(MASK_REQUIRED))
        newNode.setMaskVar(MASK_ENABLED, this.getMaskVar(MASK_ENABLED))

        newNode.constraint = constraint
        newNode.preloadHandler = preloadHandler
        newNode.preloadParams = preloadParams
        newNode.instanceName = instanceName
        newNode.namespace = namespace

        if (value != null) {
            newNode.value = value!!.clone()
        }

        newNode.children = children
        newNode.attributes = attributes
        return newNode
    }

    fun deepCopy(includeTemplates: Boolean): TreeElement {
        val newNode = shallowCopy()

        if (children != null) {
            newNode.children = ArrayList()
            for (i in 0 until children!!.size) {
                val child = children!![i]
                if (includeTemplates || child.getMult() != TreeReference.INDEX_TEMPLATE) {
                    newNode.addChild(child.deepCopy(includeTemplates))
                }
            }
        }

        if (attributes != null) {
            newNode.attributes = ArrayList()
            for (attr in attributes!!) {
                if (includeTemplates || attr.getMult() != TreeReference.INDEX_TEMPLATE) {
                    newNode.addAttribute(attr.deepCopy(includeTemplates))
                }
            }
        }

        return newNode
    }

    private fun addAttribute(attr: TreeElement) {
        if (attr.multiplicity != TreeReference.INDEX_ATTRIBUTE) {
            throw RuntimeException("Attribute doesn't have the correct index!")
        }

        if (attributes == null) {
            attributes = ArrayList()
        }

        attributes!!.add(attr)

        initAddedSubNode(attr)
    }

    /* ==== MODEL PROPERTIES ==== */

    // factoring inheritance rules
    override val isRelevant: Boolean
        get() = getMaskVar(MASK_RELEVANT_INH) && getMaskVar(MASK_RELEVANT)

    // factoring in inheritance rules
    fun isEnabled(): Boolean {
        return getMaskVar(MASK_ENABLED_INH) && getMaskVar(MASK_ENABLED)
    }

    /* ==== SPECIAL SETTERS (SETTERS WITH SIDE-EFFECTS) ==== */

    fun setAnswer(answer: IAnswerData?): Boolean {
        return if (value != null || answer != null) {
            setValue(answer)
            true
        } else {
            false
        }
    }

    fun setRequired(required: Boolean) {
        if (getMaskVar(MASK_REQUIRED) != required) {
            setMaskVar(MASK_REQUIRED, required)
        }
    }

    internal fun getMaskVar(mask: Int): Boolean {
        return (flags and mask) == mask
    }

    private fun setMaskVar(mask: Int, value: Boolean) {
        if (value) {
            flags = flags or mask
        } else {
            flags = flags and (Int.MAX_VALUE - mask)
        }
    }

    fun setRelevant(relevant: Boolean) {
        setRelevant(relevant, false)
    }

    private fun setRelevant(relevant: Boolean, inherited: Boolean) {
        val oldRelevancy = isRelevant
        if (inherited) {
            setMaskVar(MASK_RELEVANT_INH, relevant)
        } else {
            setMaskVar(MASK_RELEVANT, relevant)
        }

        if (isRelevant != oldRelevancy) {
            attributes?.let { attrs ->
                for (i in 0 until attrs.size) {
                    attrs[i].setRelevant(isRelevant, true)
                }
            }
            children?.let { kids ->
                for (i in 0 until kids.size) {
                    kids[i].setRelevant(isRelevant, true)
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        setEnabled(enabled, false)
    }

    fun setEnabled(enabled: Boolean, inherited: Boolean) {
        val oldEnabled = isEnabled()
        if (inherited) {
            setMaskVar(MASK_ENABLED_INH, enabled)
        } else {
            setMaskVar(MASK_ENABLED, enabled)
        }

        if (isEnabled() != oldEnabled) {
            children?.let { kids ->
                for (i in 0 until kids.size) {
                    kids[i].setEnabled(isEnabled(), true)
                }
            }
        }
    }

    /* ==== VISITOR PATTERN ==== */

    override fun accept(visitor: ITreeVisitor) {
        visitor.visit(this)

        val currentChildren = children ?: return
        val en = currentChildren.iterator()
        while (en.hasNext()) {
            (en.next() as TreeElement).accept(visitor)
        }
    }

    /* ==== Attributes ==== */

    override fun getAttributeCount(): Int = attributes?.size ?: 0

    override fun getAttributeNamespace(index: Int): String? {
        return attributes!![index].namespace
    }

    override fun getAttributeName(index: Int): String? {
        return attributes!![index].name
    }

    override fun getAttributeValue(index: Int): String? {
        return getAttributeValue(attributes!![index])
    }

    /**
     * Get the String value of the provided attribute
     */
    private fun getAttributeValue(attribute: TreeElement): String? {
        return attribute.getValue()?.uncast()?.getString()
    }

    override fun getAttribute(namespace: String?, name: String): TreeElement? {
        val currentAttributes = attributes ?: return null
        for (attribute in currentAttributes) {
            if (attribute.getName() == name && (namespace == null || namespace == attribute.namespace)) {
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

        var ns = namespace
        if ("" == ns) {
            // normalize to match how non-attribute TreeElements store namespaces
            // NOTE PLM: "" and null are quite conflated, especially in read/writeExternal.
            ns = null
        }

        for (i in attributes!!.size - 1 downTo 0) {
            val attribut = attributes!![i]
            if (attribut.name == name && (ns == null || ns == attribut.namespace)) {
                if (value == null) {
                    attributes!!.removeAt(i)
                } else {
                    attribut.setValue(UncastData(value))
                }
                return
            }
        }

        val attr = constructAttributeElement(ns, name)
        attr.setValue(UncastData(value!!))
        attr.setParent(this)

        attributes!!.add(attr)
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        name = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        multiplicity = ExtUtil.readInt(`in`)
        flags = ExtUtil.readInt(`in`)
        value = ExtUtil.read(`in`, ExtWrapNullable(ExtWrapTagged()), pf) as IAnswerData?

        readChildrenFromExternal(`in`, pf)

        dataType = ExtUtil.readInt(`in`)
        instanceName = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        constraint = ExtUtil.read(
            `in`, ExtWrapNullable(Constraint::class.java), pf
        ) as Constraint?
        preloadHandler = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        preloadParams = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        namespace = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))

        readAttributesFromExternal(`in`, pf)
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    private fun readChildrenFromExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        if (!ExtUtil.readBool(`in`)) {
            children = null
        } else {
            children = ArrayList()
            val numChildren = ExtUtil.readNumeric(`in`).toInt()
            for (i in 0 until numChildren) {
                val child = TreeElement()
                child.readExternal(`in`, pf)
                child.setParent(this)
                children!!.add(child)
            }
        }
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    private fun readAttributesFromExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        if (!ExtUtil.readBool(`in`)) {
            attributes = null
        } else {
            attributes = ArrayList()
            val attrCount = ExtUtil.readNumeric(`in`).toInt()
            for (i in 0 until attrCount) {
                val attr = TreeElement()
                attr.readExternal(`in`, pf)
                attr.setParent(this)
                attributes!!.add(attr)
            }
        }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(name))
        ExtUtil.writeNumeric(out, multiplicity.toLong())
        ExtUtil.writeNumeric(out, flags.toLong())
        val currentValue = value
        ExtUtil.write(out, ExtWrapNullable(if (currentValue == null) null else ExtWrapTagged(currentValue)))

        writeChildrenToExternal(out)

        ExtUtil.writeNumeric(out, dataType.toLong())
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(instanceName))
        ExtUtil.write(out, ExtWrapNullable(constraint)) // TODO: inefficient for repeats
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(preloadHandler))
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(preloadParams))
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(namespace))

        writeAttributesToExternal(out)
    }

    @Throws(PlatformIOException::class)
    private fun writeChildrenToExternal(out: DataOutputStream) {
        if (children == null) {
            ExtUtil.writeBool(out, false)
        } else {
            ExtUtil.writeBool(out, true)
            ExtUtil.writeNumeric(out, children!!.size.toLong())
            val en = children!!.iterator()
            while (en.hasNext()) {
                val child = en.next() as TreeElement
                child.writeExternal(out)
            }
        }
    }

    @Throws(PlatformIOException::class)
    private fun writeAttributesToExternal(out: DataOutputStream) {
        if (attributes == null) {
            ExtUtil.writeBool(out, false)
        } else {
            ExtUtil.writeBool(out, true)
            ExtUtil.writeNumeric(out, attributes!!.size.toLong())
            val en = attributes!!.iterator()
            while (en.hasNext()) {
                val attr = en.next() as TreeElement
                attr.writeExternal(out)
            }
        }
    }

    /**
     * Rebuilding this node from an imported instance
     */
    fun populate(incoming: TreeElement) {
        if (this.isLeaf) {
            // copy incoming element's value over
            val incomingValue = incoming.getValue()
            if (incomingValue == null) {
                this.setValue(null)
            } else {
                this.setValue(
                    AnswerDataFactory.templateByDataType(this.dataType).cast(incomingValue.uncast())
                )
            }
        } else {
            // recur on children
            // remove all default repetitions from skeleton data model, preserving templates
            var i = 0
            while (i < this.getNumChildren()) {
                val child = this.getChildAt(i)!!
                if (child.getMaskVar(MASK_REPEATABLE) &&
                    child.getMult() != TreeReference.INDEX_TEMPLATE
                ) {
                    this.removeChildAt(i)
                    i--
                }
                i++
            }

            var j = 0
            while (j < this.getNumChildren()) {
                val child = this.getChildAt(j)!!
                val newChildren = incoming.getChildrenWithName(child.getName()!!)

                if (child.getMaskVar(MASK_REPEATABLE)) {
                    for (k in 0 until newChildren.size) {
                        val newChild = child.deepCopy(true)
                        newChild.setMult(k)
                        if (children == null) {
                            children = ArrayList()
                        }
                        this.children!!.add(j + k + 1, newChild)
                        newChild.populate(newChildren[k] as TreeElement)
                    }
                    j += newChildren.size
                } else {
                    if (newChildren.size == 0) {
                        child.setRelevant(false)
                    } else {
                        child.populate(newChildren[0] as TreeElement)
                    }
                }
                j++
            }
        }

        // copy incoming element's attributes over
        for (i in 0 until incoming.getAttributeCount()) {
            val attrName = incoming.getAttributeName(i)
            val ns = incoming.getAttributeNamespace(i)
            val attrValue = incoming.getAttributeValue(i)

            this.setAttribute(ns, attrName!!, attrValue)
        }
    }

    // this method is for copying in the answers to an itemset. the template node of the destination
    // is used for overall structure (including data types), and the itemset source node is used for
    // raw data. note that data may be coerced across types, which may result in type conversion error
    // very similar in structure to populate()
    fun populateTemplate(incoming: TreeElement, f: FormDef) {
        if (this.isLeaf) {
            val incomingValue = incoming.getValue()
            if (incomingValue == null) {
                this.setValue(null)
            } else {
                this.setValue(
                    AnswerDataFactory.templateByDataType(dataType).cast(incomingValue.uncast())
                )
            }
        } else {
            var i = 0
            while (i < this.getNumChildren()) {
                val child = this.getChildAt(i)!!
                val newChildren = incoming.getChildrenWithName(child.getName()!!)

                if (child.getMaskVar(MASK_REPEATABLE)) {
                    for (k in 0 until newChildren.size) {
                        val template = f.getMainInstance()!!.getTemplate(child.getRef())
                        val newChild = template!!.deepCopy(false)
                        newChild.setMult(k)
                        if (children == null) {
                            children = ArrayList()
                        }
                        this.children!!.add(i + k + 1, newChild)
                        newChild.populateTemplate(newChildren[k] as TreeElement, f)
                    }
                    i += newChildren.size
                } else {
                    child.populateTemplate(newChildren[0] as TreeElement, f)
                }
                i++
            }
        }
    }

    private fun expireReferenceCache() {
        synchronized(refCache) {
            refCache[0] = null
        }
    }

    // return the tree reference that corresponds to this tree element
    override fun getRef(): TreeReference {
        synchronized(refCache) {
            if (refCache[0] == null) {
                refCache[0] = TreeReference.buildRefFromTreeElement(this)
            }
            return refCache[0]!!
        }
    }

    fun getPreloadHandler(): String? = preloadHandler

    fun getConstraint(): Constraint? = constraint

    fun setPreloadHandler(preloadHandler: String?) {
        this.preloadHandler = preloadHandler
    }

    fun setConstraint(constraint: Constraint?) {
        this.constraint = constraint
    }

    fun getPreloadParams(): String? = preloadParams

    fun setPreloadParams(preloadParams: String?) {
        this.preloadParams = preloadParams
    }

    override fun getName(): String? = name

    fun setName(name: String?) {
        expireReferenceCache()
        this.name = name
    }

    override fun getMult(): Int = multiplicity

    fun setMult(multiplicity: Int) {
        expireReferenceCache()
        this.multiplicity = multiplicity
    }

    fun setParent(parent: AbstractTreeElement?) {
        expireReferenceCache()
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

    fun isRequired(): Boolean = getMaskVar(MASK_REQUIRED)

    fun setRepeatable(repeatable: Boolean) {
        setMaskVar(MASK_REPEATABLE, repeatable)
    }

    override fun getNamespace(): String? = namespace

    override fun clearVolatiles() {
        refCache[0] = null
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

    fun setNamespace(namespace: String?) {
        this.namespace = namespace
    }

    /**
     * Adds a hint mapping which can be used to directly index this node's children. This is used
     * when performing batch child fetches through static evaluation.
     *
     * This map should contain a table of cannonical XPath path expression steps which can be optimized
     * (like "@someattr") along with a table of which values of that attribute correspond with which
     * of this element's children.
     *
     * Notes:
     * 1) The table of string -> child elements must be _comprehensive_, but this is not checked by
     * this method, so the caller is responsible for ensuring that the map is valid
     *
     * 2) TreeElements also do not automatically expire their attribute maps, so this method
     * should only be used on static tree structures.
     *
     * 3) The path steps matched must be direct, ie: @someattr = 'value', no other operations are supported.
     *
     * @param childAttributeHintMap A table of Path Steps which can be indexed during batch fetch, along with
     *                              a mapping of which values of those steps match which children
     */
    fun addAttributeMap(childAttributeHintMap: HashMap<XPathPathExpr, HashMap<String, Array<TreeElement>>>) {
        this.mChildStepMapping = childAttributeHintMap
    }

    override fun tryBatchChildFetch(
        name: String,
        mult: Int,
        predicates: ArrayList<XPathExpression>,
        evalContext: EvaluationContext
    ): Collection<TreeReference>? {
        return TreeUtilities.tryBatchChildFetch(this, mChildStepMapping, name, mult, predicates, evalContext)
    }

    // Return true if these elements are the same EXCEPT for their multiplicity (IE are members of the same repeat)
    fun doFieldsMatch(otherTreeElement: TreeElement): Boolean {
        return (name == otherTreeElement.name &&
                flags == otherTreeElement.flags &&
                dataType == otherTreeElement.dataType &&
                ((instanceName != null && instanceName == otherTreeElement.instanceName) ||
                        (instanceName == null && otherTreeElement.instanceName == null)) &&
                ((constraint != null && constraint == otherTreeElement.constraint) ||
                        (constraint == null && otherTreeElement.constraint == null)) &&
                ((preloadHandler != null && preloadHandler == otherTreeElement.preloadHandler) ||
                        (preloadHandler == null && otherTreeElement.preloadHandler == null)) &&
                ((preloadParams != null && preloadParams == otherTreeElement.preloadParams) ||
                        (preloadParams == null && otherTreeElement.preloadParams == null)) &&
                ((namespace != null && namespace == otherTreeElement.namespace) ||
                        (namespace == null && otherTreeElement.namespace == null)) &&
                ((value != null && value!!.uncast().getString() == otherTreeElement.value!!.uncast().getString()) ||
                        value == null && otherTreeElement.value == null))
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }

        // NOTE PLM: does not compare equality of parents because that requires
        // trickery to avoid looping indefinitely
        if (o is TreeElement) {
            val otherTreeElement = o
            val doFieldsMatch = doFieldsMatch(otherTreeElement) &&
                    multiplicity == otherTreeElement.multiplicity
            if (doFieldsMatch) {
                if (children != null) {
                    if (otherTreeElement.children == null) {
                        return false
                    }
                    for (child in children!!) {
                        if (!otherTreeElement.children!!.contains(child)) {
                            return false
                        }
                    }
                } else {
                    if (otherTreeElement.children != null) {
                        return false
                    }
                }
                if (attributes != null) {
                    if (otherTreeElement.attributes == null) {
                        return false
                    }
                    for (attr in attributes!!) {
                        if (!otherTreeElement.attributes!!.contains(attr)) {
                            return false
                        }
                    }
                } else {
                    if (otherTreeElement.attributes != null) {
                        return false
                    }
                }
                return true
            }
        }
        return false
    }

    override fun hashCode(): Int {
        var childrenHashCode = 0
        if (children != null) {
            for (child in children!!) {
                childrenHashCode = childrenHashCode xor child.hashCode()
            }
        }

        var attributesHashCode = 0
        if (attributes != null) {
            for (attr in attributes!!) {
                attributesHashCode = attributesHashCode xor attr.hashCode()
            }
        }

        return multiplicity xor flags xor dataType xor
                (instanceName?.hashCode() ?: 0) xor
                (constraint?.hashCode() ?: 0) xor
                (preloadHandler?.hashCode() ?: 0) xor
                (preloadParams?.hashCode() ?: 0) xor
                (namespace?.hashCode() ?: 0) xor
                (value?.hashCode() ?: 0) xor
                childrenHashCode xor attributesHashCode
    }

    /**
     * Old externalization scheme used to migrate fixtures from CommCare 2.24 to 2.25
     *
     * This can be removed once we are certain no devices will be migrated up from 2.24
     */
    @Throws(PlatformIOException::class, DeserializationException::class)
    fun readExternalMigration(`in`: DataInputStream, pf: PrototypeFactory?) {
        name = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        multiplicity = ExtUtil.readInt(`in`)
        flags = ExtUtil.readInt(`in`)
        value = ExtUtil.read(`in`, ExtWrapNullable(ExtWrapTagged()), pf) as IAnswerData?

        if (!ExtUtil.readBool(`in`)) {
            children = null
        } else {
            children = ArrayList()
            val numChildren = ExtUtil.readNumeric(`in`).toInt()
            for (i in 0 until numChildren) {
                val normal = ExtUtil.readBool(`in`)
                val child: TreeElement

                if (normal) {
                    child = TreeElement()
                    child.readExternalMigration(`in`, pf)
                } else {
                    child = ExtUtil.read(`in`, ExtWrapTagged(), pf) as TreeElement
                }
                child.setParent(this)
                children!!.add(child)
            }
        }

        dataType = ExtUtil.readInt(`in`)
        instanceName = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        constraint = ExtUtil.read(
            `in`, ExtWrapNullable(Constraint::class.java), pf
        ) as Constraint?
        preloadHandler = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        preloadParams = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        namespace = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))

        @Suppress("UNCHECKED_CAST")
        val attStrings = ExtUtil.nullIfEmpty(
            ExtUtil.read(`in`, ExtWrapList(String::class.java), pf) as ArrayList<Any>
        )
        setAttributesFromSingleStringVector(attStrings)
    }

    private fun setAttributesFromSingleStringVector(attStrings: ArrayList<Any>?) {
        if (attStrings != null) {
            this.attributes = ArrayList()
            for (i in 0 until attStrings.size) {
                addSingleAttribute(i, attStrings)
            }
        }
    }

    private fun addSingleAttribute(i: Int, attStrings: ArrayList<Any>) {
        var att = attStrings[i] as String
        val array = arrayOfNulls<String>(3)

        var pos: Int

        // TODO: The only current assumption here is that the namespace/name of the attribute doesn't have
        // an equals sign in it. I think this is safe. not sure.

        // Split into first and second parts
        pos = att.indexOf("=")

        // put the value in our output
        array[2] = att.substring(pos + 1)

        // now we're left with the xmlns (possibly) and the
        // name. Get that into a single string.
        att = att.substring(0, pos)

        // reset position marker.
        pos = -1

        // Clayton Sims - Jun 1, 2009 : Updated this code:
        //    We want to find the _last_ possible ':', not the
        // first one. Namespaces can have URLs in them.
        while (att.indexOf(":", pos + 1) != -1) {
            pos = att.indexOf(":", pos + 1)
        }

        if (pos == -1) {
            // No namespace
            array[0] = null

            // for the name eval below
            pos = 0
        } else {
            // there is a namespace, grab it
            array[0] = att.substring(0, pos)
        }
        // Now get the name part
        array[1] = att.substring(pos)

        setAttribute(array[0], array[1]!!, array[2])
    }

    companion object {
        private const val MASK_REQUIRED = 0x01
        private const val MASK_REPEATABLE = 0x02
        private const val MASK_ATTRIBUTE = 0x04
        private const val MASK_RELEVANT = 0x08
        private const val MASK_ENABLED = 0x10
        private const val MASK_RELEVANT_INH = 0x20
        private const val MASK_ENABLED_INH = 0x40

        /**
         * Construct a TreeElement which represents an attribute with the provided
         * namespace and name.
         *
         * @return A new instance of a TreeElement
         */
        @JvmStatic
        fun constructAttributeElement(namespace: String?, name: String): TreeElement {
            val element = TreeElement(name)
            element.setMaskVar(MASK_ATTRIBUTE, true)
            element.namespace = namespace
            element.multiplicity = TreeReference.INDEX_ATTRIBUTE
            return element
        }

        /**
         * Implementation of ArrayList.indexOf that avoids calling TreeElement.equals,
         * which is very slow.
         */
        private fun referenceIndexOf(list: ArrayList<*>, potentialEntry: Any): Int {
            for (i in 0 until list.size) {
                val element = list[i]
                if (potentialEntry === element) {
                    return i
                }
            }
            return -1
        }
    }
}
