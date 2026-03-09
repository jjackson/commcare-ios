package org.javarosa.core.model.instance

import org.commcare.cases.instance.CaseInstanceTreeElement
import org.javarosa.core.model.instance.utils.InstanceUtils.setUpInstanceRoot
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.PrototypeFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * @author ctsims
 */
open class ExternalDataInstance : DataInstance<AbstractTreeElement> {

    private var reference: String? = null
    private var root: AbstractTreeElement? = null
    private var base: InstanceBase? = null
    private var source: ExternalDataInstanceSource? = null

    constructor() : super()

    constructor(reference: String?, instanceid: String?) : super(instanceid) {
        this.reference = reference
    }

    /**
     * Copy constructor
     */
    constructor(instance: ExternalDataInstance) : super(instance.getInstanceId()) {
        this.reference = instance.getReference()
        this.base = instance.getBase()
        // Copy constructor avoids check.
        this.root = instance.root
        this.mCacheHost = instance.getCacheHost()
        this.source = instance.getSource()
    }

    constructor(reference: String?, instanceId: String?, topLevel: TreeElement?) :
            this(reference, instanceId, topLevel as AbstractTreeElement?, null)

    constructor(
        reference: String?,
        instanceId: String?,
        topLevel: AbstractTreeElement?,
        source: ExternalDataInstanceSource?
    ) : this(reference, instanceId) {
        base = InstanceBase(instanceId)
        this.source = source
        this.root = topLevel
        setUpInstanceRoot(root, instanceId, base!!)
        base!!.setChild(root)
    }

    open fun useCaseTemplate(): Boolean {
        return if (source == null) {
            CaseInstanceTreeElement.MODEL_NAME == instanceid
        } else {
            source!!.useCaseTemplate()
        }
    }

    override fun isRuntimeEvaluated(): Boolean = true

    override fun getBase(): InstanceBase? = base

    override fun getRoot(): AbstractTreeElement? {
        if (needsInit()) {
            throw RuntimeException("Attempt to use instance $instanceid without inititalization.")
        }

        val currentSource = source
        return if (currentSource != null) {
            currentSource.getRoot()
        } else {
            root
        }
    }

    fun getReference(): String? = reference

    fun getSource(): ExternalDataInstanceSource? = source

    fun needsInit(): Boolean {
        return if (source == null) {
            false
        } else {
            source!!.needsInit()
        }
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        super.readExternal(`in`, pf)
        reference = ExtUtil.readString(`in`)
        source = ExtUtil.read(
            `in`,
            ExtWrapNullable(ExternalDataInstanceSource::class.java), pf
        ) as ExternalDataInstanceSource?
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        super.writeExternal(out)
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(reference))
        ExtUtil.write(out, ExtWrapNullable(source))
    }

    override fun initialize(initializer: InstanceInitializationFactory?, instanceId: String?): DataInstance<*> {
        base = InstanceBase(instanceId)
        val instanceRoot = initializer!!.generateRoot(this)
        // this indirectly calls `this.copyFromSource` via the InstanceRoot so that we call the
        // correct method based on the type
        instanceRoot.setupNewCopy(this)
        return initializer.getSpecializedExternalDataInstance(this)
    }

    fun copyFromSource(instanceRoot: InstanceRoot) {
        root = instanceRoot.getRoot()
        base!!.setChild(root)
    }

    fun copyFromSource(source: ExternalDataInstanceSource) {
        // parent copy
        copyFromSource(source as InstanceRoot)
        this.source = source
    }

    /**
     * Copy method to allow creating copies of this instance without having to know
     * what the instance class is.
     */
    open fun copy(): ExternalDataInstance {
        return ExternalDataInstance(this)
    }

    override fun toString(): String {
        return "ExternalDataInstance{" +
                "reference='" + reference + '\'' +
                ", name='" + name + '\'' +
                ", instanceid='" + instanceid + '\'' +
                '}'
    }

    companion object {
        const val JR_SESSION_REFERENCE: String = "jr://instance/session"
        const val JR_CASE_DB_REFERENCE: String = "jr://instance/casedb"
        const val JR_LEDGER_DB_REFERENCE: String = "jr://instance/ledgerdb"
        const val JR_SEARCH_INPUT_REFERENCE: String = "jr://instance/search-input"
        const val JR_SELECTED_ENTITIES_REFERENCE: String = "jr://instance/selected-entities"
        const val JR_REMOTE_REFERENCE: String = "jr://instance/remote"
    }
}
