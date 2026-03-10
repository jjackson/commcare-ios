package org.javarosa.core.model.instance

import org.commcare.cases.query.QuerySensitiveTreeElementWrapper
import org.commcare.cases.util.QueryUtils
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.utils.CacheHost
import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.LocalCacheTable
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.model.xform.XPathReference
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream

/**
 * A data instance represents a tree structure of abstract tree
 * elements which can be accessed and read with tree references. It is
 * a supertype of different types of concrete models which may or may not
 * be read only.
 *
 * @author ctsims
 */
abstract class DataInstance<T : AbstractTreeElement> : Persistable {

    /**
     * The integer Id of the model
     */
    private var recordid = -1

    /**
     * The name for this data model
     */
    @JvmField
    protected var name: String? = null

    /**
     * The ID of the form that this is a model for
     */
    private var formId: Int = 0

    @JvmField
    protected var instanceid: String? = null

    @JvmField
    protected var mCacheHost: CacheHost? = null

    private val referenceCache = LocalCacheTable<TreeReference, T>()

    constructor()

    constructor(instanceid: String?) {
        this.instanceid = instanceid
    }

    abstract fun getBase(): AbstractTreeElement?

    abstract fun getRoot(): T?

    fun getInstanceId(): String? = instanceid

    /**
     * Whether the structure of this instance is only available at runtime.
     *
     * @return true if the instance structure is available and runtime and can't
     * be checked for consistency until the reference is made available. False
     * otherwise.
     */
    open fun isRuntimeEvaluated(): Boolean = false

    fun resolveReference(binding: XPathReference): T? {
        return resolveReference(unpackReference(binding))
    }

    fun resolveReference(ref: TreeReference): T? {
        return resolveReference(ref, null)
    }

    fun resolveReference(ref: TreeReference, ec: EvaluationContext?): T? {
        if (!ref.isAbsolute) {
            return null
        }

        var t = referenceCache.retrieve(ref)

        if (t != null && t.getValue() != null) {
            return t
        }

        var node: AbstractTreeElement? = getBase()
        var result: T? = null
        for (i in 0 until ref.size()) {
            if (ec != null) {
                val context = ec.getCurrentQueryContext()
                QueryUtils.prepareSensitiveObjectForUseInCurrentContext(node, context)
                node = QuerySensitiveTreeElementWrapper.WrapWithContext(node!!, context)
            }
            val name = ref.getName(i)
            var mult = ref.getMultiplicity(i)

            if (mult == TreeReference.INDEX_ATTRIBUTE) {
                // Should we possibly just return here?
                // I guess technically we could step back...
                @Suppress("UNCHECKED_CAST")
                val attr = node!!.getAttribute(null, name!!) as T?
                node = attr
                result = attr
                continue
            }
            if (mult == TreeReference.INDEX_UNBOUND) {
                val inferredMultiplicity = node!!.getChildMultiplicity(name!!)
                if (inferredMultiplicity == 1 || inferredMultiplicity == 0) {
                    mult = 0
                } else {
                    // reference is not unambiguous
                    node = null
                    result = null
                    break
                }
            }

            @Suppress("UNCHECKED_CAST")
            val child = node!!.getChild(name!!, mult) as T?
            node = child
            result = child
            if (node == null) {
                break
            }
        }

        t = if (node === getBase()) null else result // never return a reference to '/'
        if (t != null) {
            referenceCache.register(ref, t)
        }
        return t
    }

    fun getTemplate(ref: TreeReference): T? {
        val node = getTemplatePath(ref) ?: return null

        if (!(node.isRepeatable || node.isAttribute)) {
            return null
        }
        return node
    }

    fun getTemplatePath(ref: TreeReference): T? {
        if (!ref.isAbsolute) {
            return null
        }

        var walker: T? = null
        var node: AbstractTreeElement? = getBase()
        for (i in 0 until ref.size()) {
            val name = ref.getName(i)

            if (ref.getMultiplicity(i) == TreeReference.INDEX_ATTRIBUTE) {
                @Suppress("UNCHECKED_CAST")
                val attr = node!!.getAttribute(null, name!!) as T?
                node = attr
                walker = attr
            } else {
                @Suppress("UNCHECKED_CAST")
                var newNode = node!!.getChild(name!!, TreeReference.INDEX_TEMPLATE) as T?
                if (newNode == null) {
                    @Suppress("UNCHECKED_CAST")
                    newNode = node.getChild(name, 0) as T?
                }
                if (newNode == null) {
                    return null
                }
                node = newNode
                walker = newNode
            }
        }
        return walker
    }

    /**
     * Determines if a path exists for a reference; template elements are
     * followed when available. Non-absolute references aren't followed.
     *
     * @param ref the reference path to be followed
     * @return was a valid path found for the reference?
     */
    open fun hasTemplatePath(ref: TreeReference): Boolean {
        return ref.isAbsolute && hasTemplatePathRec(ref, getBase(), 0)
    }

    /**
     * Determines if a path exists for a reference using a given node; template
     * nodes followed first when available.
     *
     * @param topRef      the reference path being followed
     * @param currentNode the current element we are at along the path
     * @param depth       the depth of the current element
     * @return was a valid path found?
     */
    private fun hasTemplatePathRec(
        topRef: TreeReference,
        currentNode: AbstractTreeElement?,
        depth: Int
    ): Boolean {
        // stop when at the end of reference
        if (depth == topRef.size()) {
            return true
        }
        // stop if we are trying to proceed on a null element
        if (currentNode == null) {
            return false
        }

        val name = topRef.getName(depth)

        if (topRef.getMultiplicity(depth) == TreeReference.INDEX_ATTRIBUTE) {
            // recur on attribute node if the multiplicity designates it
            return hasTemplatePathRec(topRef, currentNode.getAttribute(null, name!!), depth + 1)
        } else {
            // try to grab template node
            val nextNode = currentNode.getChild(name!!, TreeReference.INDEX_TEMPLATE)
            if (nextNode != null) {
                return hasTemplatePathRec(topRef, nextNode, depth + 1)
            } else {
                // if there isn't a template element, recur through normal children
                // looking for the first valid path forward
                val children = currentNode.getChildrenWithName(name)
                for (child in children) {
                    if (hasTemplatePathRec(topRef, child, depth + 1)) {
                        // stop if we found a path
                        return true
                    }
                }
            }
        }
        // no way forward
        return false
    }

    fun setFormId(formId: Int) {
        this.formId = formId
    }

    fun getFormId(): Int = this.formId

    fun getName(): String? = name

    fun setName(name: String?) {
        this.name = name
    }

    override fun toString(): String {
        val displayName = this.name ?: "NULL"
        return "DataInstance{" +
                "name='" + displayName + '\'' +
                ", instanceid='" + instanceid + '\'' +
                '}'
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        recordid = ExtUtil.readInt(`in`)
        formId = ExtUtil.readInt(`in`)
        name = ExtUtil.read(`in`, ExtWrapNullable(String::class.java), pf) as String?
        instanceid = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeNumeric(out, recordid.toLong())
        ExtUtil.writeNumeric(out, formId.toLong())
        ExtUtil.write(out, ExtWrapNullable(name))
        ExtUtil.write(out, ExtUtil.emptyIfNull(instanceid))
    }

    override fun getID(): Int = recordid

    override fun setID(recordid: Int) {
        this.recordid = recordid
    }

    abstract fun initialize(initializer: InstanceInitializationFactory?, instanceId: String?): DataInstance<*>

    fun getCacheHost(): CacheHost? = mCacheHost

    fun setCacheHost(cacheHost: CacheHost?) {
        this.mCacheHost = cacheHost
    }

    /**
     * Cleans reference caches maintained by the instance and TreeElements contained in the contextNode
     */
    fun cleanCache() {
        referenceCache.clear()
        getBase()?.clearVolatiles()
    }

    companion object {
        @JvmStatic
        fun unpackReference(ref: XPathReference): TreeReference {
            return ref.reference
        }
    }
}
