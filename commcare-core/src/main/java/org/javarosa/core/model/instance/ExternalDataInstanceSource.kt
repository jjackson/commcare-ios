package org.javarosa.core.model.instance

import org.javarosa.core.util.ListMultimap
import org.commcare.core.interfaces.RemoteInstanceFetcher
import org.javarosa.core.model.instance.ExternalDataInstance.Companion.JR_REMOTE_REFERENCE
import org.javarosa.core.model.instance.utils.InstanceUtils.setUpInstanceRoot
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapMultiMap
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Wrapper class for remote data instances which will materialize the instance data
 * from the source information when needed.
 */
class ExternalDataInstanceSource : InstanceRoot, Externalizable {

    private var root: AbstractTreeElement? = null
    private var instanceId: String? = null
    private var mUseCaseTemplate: Boolean = false
    private var reference: String? = null
    private var sourceUri: String? = null
    private var requestData: ListMultimap<String, String>? = null
    private var storageReferenceId: String? = null

    constructor()

    private constructor(
        instanceId: String?,
        root: TreeElement?,
        reference: String?,
        useCaseTemplate: Boolean,
        sourceUri: String?,
        requestData: ListMultimap<String, String>?,
        storageReferenceId: String?
    ) {
        if (sourceUri == null && storageReferenceId == null) {
            throw RuntimeException(
                javaClass.canonicalName +
                        " must be initialised with one of sourceUri or storageReferenceId"
            )
        }
        this.instanceId = instanceId
        this.root = root
        this.reference = reference
        this.mUseCaseTemplate = useCaseTemplate
        this.sourceUri = sourceUri
        this.requestData = requestData
        this.storageReferenceId = storageReferenceId
    }

    /**
     * Copy constructor
     */
    constructor(externalDataInstanceSource: ExternalDataInstanceSource) {
        this.instanceId = externalDataInstanceSource.instanceId
        this.root = externalDataInstanceSource.root
        this.reference = externalDataInstanceSource.reference
        this.mUseCaseTemplate = externalDataInstanceSource.useCaseTemplate()
        this.sourceUri = externalDataInstanceSource.sourceUri
        this.requestData = externalDataInstanceSource.requestData
        this.storageReferenceId = externalDataInstanceSource.storageReferenceId
    }

    fun needsInit(): Boolean {
        return root == null
    }

    override fun getRoot(): AbstractTreeElement? {
        if (needsInit()) {
            throw RuntimeException("Uninstantiated external instance source")
        }
        return root
    }

    fun init(root: AbstractTreeElement?) {
        if (this.root != null) {
            throw RuntimeException(
                "Initializing an already instantiated external instance source is not permitted"
            )
        }
        this.root = root
    }

    @Throws(RemoteInstanceFetcher.RemoteInstanceException::class)
    fun remoteInit(remoteInstanceFetcher: RemoteInstanceFetcher, refId: String?) {
        val instanceId = getInstanceId()!!
        init(remoteInstanceFetcher.getExternalRoot(instanceId, this, refId!!))
        setUpInstanceRoot(root, instanceId, InstanceBase(instanceId))
    }

    override fun setupNewCopy(instance: ExternalDataInstance) {
        instance.copyFromSource(this)
    }

    fun toInstance(): ExternalDataInstance {
        return ExternalDataInstance(getReference(), getInstanceId(), getRoot(), this)
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        instanceId = ExtUtil.readString(`in`)
        mUseCaseTemplate = ExtUtil.readBool(`in`)
        sourceUri = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        @Suppress("UNCHECKED_CAST")
        requestData = ExtUtil.read(`in`, ExtWrapMultiMap(String::class.java), pf) as ListMultimap<String, String>
        storageReferenceId = ExtUtil.read(`in`, ExtWrapNullable(String::class.java), pf) as String?
        reference = ExtUtil.readString(`in`)
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(instanceId))
        ExtUtil.writeBool(out, mUseCaseTemplate)
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(sourceUri))
        ExtUtil.write(out, ExtWrapMultiMap(requestData!!))
        ExtUtil.write(out, ExtWrapNullable(storageReferenceId?.toString()))
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(reference))
    }

    fun getInstanceId(): String? = instanceId

    fun useCaseTemplate(): Boolean = mUseCaseTemplate

    fun getReference(): String? = reference

    fun getSourceUri(): String? = sourceUri

    fun getRequestData(): ListMultimap<String, String>? = requestData

    fun getStorageReferenceId(): String? = storageReferenceId

    companion object {
        @JvmStatic
        fun buildRemote(
            instanceId: String?,
            root: TreeElement?,
            useCaseTemplate: Boolean,
            sourceUri: String?,
            requestData: ListMultimap<String, String>?
        ): ExternalDataInstanceSource {
            return ExternalDataInstanceSource(
                instanceId, root, getRemoteReference(instanceId),
                useCaseTemplate, sourceUri, requestData, null
            )
        }

        private fun getRemoteReference(instanceId: String?): String {
            return JR_REMOTE_REFERENCE.plus("/").plus(instanceId)
        }

        @JvmStatic
        fun buildVirtual(
            instance: ExternalDataInstance,
            storageReferenceId: String?
        ): ExternalDataInstanceSource {
            @Suppress("UNCHECKED_CAST")
            return buildVirtual(
                instance.getInstanceId(),
                instance.getRoot() as TreeElement?,
                instance.getReference(),
                instance.useCaseTemplate(),
                storageReferenceId
            )
        }

        @JvmStatic
        fun buildVirtual(
            instanceId: String?,
            root: TreeElement?,
            reference: String?,
            useCaseTemplate: Boolean,
            storageReferenceId: String?
        ): ExternalDataInstanceSource {
            return ExternalDataInstanceSource(
                instanceId, root, reference,
                useCaseTemplate, null, ListMultimap(), storageReferenceId
            )
        }
    }
}
