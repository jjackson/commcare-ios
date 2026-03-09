package org.javarosa.core.model.instance

import org.javarosa.core.model.FormDef
import org.javarosa.core.model.instance.utils.ITreeVisitor
import org.javarosa.core.model.utils.IInstanceVisitor
import org.javarosa.core.services.storage.IMetaData
import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapMap
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.PrototypeFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Date
import java.util.Hashtable

/**
 * This class represents the xform model instance
 */
open class FormInstance : DataInstance<TreeElement>, Persistable, IMetaData {

    /**
     * The date that this model was taken and recorded
     */
    private var dateSaved: Date? = null

    @JvmField
    var schema: String? = null
    @JvmField
    var formVersion: String? = null
    @JvmField
    var uiVersion: String? = null

    private var namespaces = Hashtable<String, String>()

    /**
     * The root of this tree
     */
    @JvmField
    protected var root: TreeElement = TreeElement()

    constructor() {
        // for externalization
    }

    constructor(root: TreeElement) : this(root, null)

    /**
     * Creates a new data model using the root given.
     *
     * @param root The root of the tree for this data model.
     */
    constructor(root: TreeElement, id: String?) : super(id) {
        setID(-1)
        setFormId(-1)
        setRoot(root)
    }

    override fun getBase(): TreeElement = root

    override fun getRoot(): TreeElement {
        if (root.getNumChildren() == 0)
            throw RuntimeException("root node has no children")

        return root.getChildAt(0)!!
    }

    /**
     * Sets the root element of this Model's tree
     *
     * @param topLevel root of the tree for this data model.
     */
    fun setRoot(topLevel: TreeElement?) {
        root = TreeElement()
        if (this.getInstanceId() != null) {
            root.setInstanceName(this.getInstanceId())
        }
        if (topLevel != null) {
            root.addChild(topLevel)
        }
    }

    @Throws(InvalidReferenceException::class)
    fun copyNode(from: TreeReference, to: TreeReference): TreeReference {
        if (!from.isAbsolute) {
            throw InvalidReferenceException("Source reference must be absolute for copying", from)
        }

        val src = resolveReference(from)
            ?: throw InvalidReferenceException("Null Source reference while attempting to copy node", from)

        return copyNode(src, to).getRef()
    }

    // for making new repeat instances; 'from' and 'to' must be unambiguous
    // references EXCEPT 'to' may be ambiguous at its final step
    // return true is successfully copied, false otherwise
    @Throws(InvalidReferenceException::class)
    fun copyNode(src: TreeElement, to: TreeReference): TreeElement {
        if (!to.isAbsolute)
            throw InvalidReferenceException("Destination reference must be absolute for copying", to)

        // strip out dest node info and get dest parent
        val dstName = to.getNameLast()
        var dstMult = to.getMultLast()
        val toParent = to.getParentRef()

        val parent = resolveReference(toParent!!)
            ?: throw InvalidReferenceException("Null parent reference whle attempting to copy", toParent)
        if (!parent.isChildable) {
            throw InvalidReferenceException("Invalid Parent Node: cannot accept children.", toParent)
        }

        if (dstMult == TreeReference.INDEX_UNBOUND) {
            dstMult = parent.getChildMultiplicity(dstName!!)
        } else if (parent.getChild(dstName!!, dstMult) != null) {
            throw InvalidReferenceException("Destination already exists!", to)
        }

        val dest = src.deepCopy(false)
        dest.setName(dstName)
        dest.setMult(dstMult)
        parent.addChild(dest)
        return dest
    }

    fun addNamespace(prefix: String, URI: String) {
        namespaces[prefix] = URI
    }

    fun getNamespacePrefixes(): Array<String?> {
        val prefixes = arrayOfNulls<String>(namespaces.size)
        var i = 0
        val en = namespaces.keys()
        while (en.hasMoreElements()) {
            prefixes[i] = en.nextElement() as String
            ++i
        }
        return prefixes
    }

    fun getNamespaceURI(prefix: String): String? {
        return namespaces[prefix]
    }

    public fun clone(): FormInstance {
        val cloned = FormInstance(this.getRoot().deepCopy(true))

        cloned.setID(this.getID())
        cloned.setFormId(this.getFormId())
        cloned.setName(this.getName())
        cloned.dateSaved = this.dateSaved
        cloned.schema = this.schema
        cloned.formVersion = this.formVersion
        cloned.uiVersion = this.uiVersion
        cloned.namespaces = Hashtable(namespaces)

        return cloned
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        super.readExternal(`in`, pf)
        schema = ExtUtil.read(`in`, ExtWrapNullable(String::class.java), pf) as String?
        dateSaved = ExtUtil.read(`in`, ExtWrapNullable(Date::class.java), pf) as Date?

        @Suppress("UNCHECKED_CAST")
        namespaces = ExtUtil.read(`in`, ExtWrapMap(String::class.java, String::class.java), pf) as Hashtable<String, String>
        setRoot(ExtUtil.read(`in`, TreeElement::class.java, pf) as TreeElement)
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        super.writeExternal(out)
        ExtUtil.write(out, ExtWrapNullable(schema))
        ExtUtil.write(out, ExtWrapNullable(dateSaved))
        ExtUtil.write(out, ExtWrapMap(namespaces))

        ExtUtil.write(out, getRoot())
    }

    @Throws(InvalidReferenceException::class)
    fun copyItemsetNode(copyNode: TreeElement, destRef: TreeReference, f: FormDef) {
        val templateNode = getTemplate(destRef)
        val newNode = copyNode(templateNode!!, destRef)
        newNode.populateTemplate(copyNode, f)
    }

    fun accept(visitor: IInstanceVisitor) {
        visitor.visit(this)

        if (visitor is ITreeVisitor) {
            root.accept(visitor)
        }
    }

    override fun initialize(initializer: InstanceInitializationFactory?, instanceId: String?): DataInstance<*> {
        this.instanceid = instanceId
        root.setInstanceName(instanceId)

        return this
    }

    override fun getMetaDataFields(): Array<String> {
        return arrayOf(META_XMLNS, META_ID)
    }

    /**
     * used by TouchForms
     */
    @Suppress("unused")
    fun getMetaData(): Hashtable<String, Any> {
        val data = Hashtable<String, Any>()
        for (key in getMetaDataFields()) {
            data[key] = getMetaData(key)
        }
        return data
    }

    override fun getMetaData(fieldName: String): Any {
        if (META_XMLNS == fieldName) {
            return ExtUtil.emptyIfNull(schema)
        } else if (META_ID == fieldName) {
            return ExtUtil.emptyIfNull(this.getInstanceId())
        }
        throw IllegalArgumentException("No metadata field $fieldName in the form instance storage system")
    }

    /**
     * Custom deserializer for migrating fixtures off of CommCare 2.24.
     *
     * The migration is needed because attribute serialization was redone to
     * capture data-type information.  If this migration is not performed
     * between 2.24 and subsequent versions, fixtures can not be opened. If the
     * migration fails the user can always sync, clear user data, and restore
     * to get reload the fixtures.
     *
     * Used in Android app db migration V.7 and user db migration V.9
     *
     * This can be removed once no devices running 2.24 remain
     */
    @Throws(IOException::class, DeserializationException::class)
    fun migrateSerialization(`in`: DataInputStream, pf: PrototypeFactory?) {
        super.readExternal(`in`, pf!!)
        schema = ExtUtil.read(`in`, ExtWrapNullable(String::class.java), pf) as String?
        dateSaved = ExtUtil.read(`in`, ExtWrapNullable(Date::class.java), pf) as Date?

        @Suppress("UNCHECKED_CAST")
        namespaces = ExtUtil.read(`in`, ExtWrapMap(String::class.java, String::class.java), pf) as Hashtable<String, String>
        val newRoot: TreeElement
        try {
            newRoot = TreeElement::class.java.newInstance()
        } catch (e: InstantiationException) {
            e.printStackTrace()
            throw RuntimeException(e.message)
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
            throw RuntimeException(e.message)
        }
        newRoot.readExternalMigration(`in`, pf)
        setRoot(newRoot)
    }

    companion object {
        const val STORAGE_KEY: String = "FORMDATA"
        const val META_XMLNS: String = "XMLNS"
        const val META_ID: String = "instance_id"

        @JvmStatic
        fun isHomogeneous(a: TreeElement, b: TreeElement): Boolean {
            if (a.isLeaf && b.isLeaf) {
                return true
            } else if (a.isChildable && b.isChildable) {
                // verify that every (non-repeatable) node in a exists in b and vice
                // versa
                for (k in 0 until 2) {
                    val n1 = if (k == 0) a else b
                    val n2 = if (k == 0) b else a

                    for (i in 0 until n1.getNumChildren()) {
                        val child1 = n1.getChildAt(i)!!
                        if (child1.isRepeatable)
                            continue
                        val child2 = n2.getChild(child1.getName()!!, 0)
                            ?: return false
                        if (child2.isRepeatable)
                            throw RuntimeException("shouldn't happen")
                    }
                }

                // compare children
                for (i in 0 until a.getNumChildren()) {
                    val childA = a.getChildAt(i)!!
                    if (childA.isRepeatable)
                        continue
                    val childB = b.getChild(childA.getName()!!, 0)!!
                    if (!isHomogeneous(childA, childB))
                        return false
                }

                return true
            } else {
                return false
            }
        }
    }
}
