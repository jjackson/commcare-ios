package org.commcare.core.process

import org.commcare.cases.instance.CaseDataInstance
import org.commcare.cases.instance.CaseInstanceTreeElement
import org.commcare.cases.instance.LedgerInstanceTreeElement
import org.commcare.core.interfaces.UserSandbox
import org.commcare.core.interfaces.VirtualDataInstanceStorage
import org.commcare.core.sandbox.SandboxUtils
import org.commcare.data.xml.VirtualInstances
import org.commcare.modern.session.SessionWrapper
import org.commcare.session.SessionFrame
import org.commcare.session.SessionInstanceBuilder
import org.commcare.suite.model.StackFrameStep
import org.commcare.util.CommCarePlatform
import org.javarosa.core.model.User
import org.javarosa.core.model.instance.ConcreteInstanceRoot
import org.javarosa.core.model.instance.ExternalDataInstance
import org.javarosa.core.model.instance.ExternalDataInstanceSource
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.InstanceInitializationFactory
import org.javarosa.core.model.instance.InstanceRoot
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.services.locale.Localization
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.util.LocalCacheTable

/**
 * Initializes a CommCare DataInstance against a UserDataInterface and (sometimes) optional
 * CommCareSession/Platform
 *
 * @author ctsims
 * @author wspride
 */
open class CommCareInstanceInitializer : InstanceInitializationFactory {

    protected val sessionWrapper: SessionWrapper?
    protected var casebase: CaseInstanceTreeElement? = null
    protected var stockbase: LedgerInstanceTreeElement? = null
    private val fixtureBases: LocalCacheTable<String, TreeElement> = LocalCacheTable()
    protected val mSandbox: UserSandbox?
    @JvmField
    protected val mPlatform: CommCarePlatform?

    // default constructor because Jython is annoying
    constructor() : this(null, null, null)

    constructor(sandbox: UserSandbox?) : this(null, sandbox, null)

    constructor(sessionWrapper: SessionWrapper?, sandbox: UserSandbox?, platform: CommCarePlatform?) {
        this.sessionWrapper = sessionWrapper
        this.mSandbox = sandbox
        this.mPlatform = platform
    }

    override fun getSpecializedExternalDataInstance(instance: ExternalDataInstance): ExternalDataInstance {
        return if (instance.useCaseTemplate()) {
            CaseDataInstance(instance)
        } else {
            instance
        }
    }

    override fun generateRoot(instance: ExternalDataInstance): InstanceRoot {
        val ref = instance.getReference()
        if (ref!!.contains(LedgerInstanceTreeElement.MODEL_NAME)) {
            return setupLedgerData(instance)
        } else if (ref.contains(CaseInstanceTreeElement.MODEL_NAME)) {
            return setupCaseData(instance)
        } else if (ref.contains("fixture")) {
            return setupFixtureData(instance)
        } else if (instance.getReference()!!.contains("session")) {
            return setupSessionData(instance)
        } else if (ref.startsWith(ExternalDataInstance.JR_REMOTE_REFERENCE)) {
            return setupExternalDataInstance(instance, ref, SessionFrame.STATE_QUERY_REQUEST)
        } else if (ref.startsWith(ExternalDataInstance.JR_SELECTED_ENTITIES_REFERENCE)) {
            return setupSelectedEntitiesInstance(instance, ref)
        } else if (ref.startsWith(ExternalDataInstance.JR_SEARCH_INPUT_REFERENCE)) {
            return setupExternalDataInstance(instance, ref, SessionFrame.STATE_QUERY_REQUEST)
        }
        return ConcreteInstanceRoot.NULL
    }

    private fun setupSelectedEntitiesInstance(instance: ExternalDataInstance, ref: String): InstanceRoot {
        val stepType = SessionFrame.STATE_MULTIPLE_DATUM_VAL
        var instanceRoot = setupExternalDataInstance(instance, ref, stepType)
        if (instanceRoot === ConcreteInstanceRoot.NULL) {
            instanceRoot = getExternalDataInstanceSourceByStepValue(instance, stepType)
        }
        return instanceRoot
    }

    // Tries to get instance by looking for the instance with id equal to the datum value in the storage
    private fun getExternalDataInstanceSourceByStepValue(
        instance: ExternalDataInstance,
        stepType: String
    ): InstanceRoot {
        val instanceFetcher = sessionWrapper!!.getRemoteInstanceFetcher()
        if (instanceFetcher != null) {
            val instanceStorage: VirtualDataInstanceStorage = instanceFetcher.getVirtualDataInstanceStorage()
            for (step in sessionWrapper.getFrame().getSteps()) {
                if (step.getType() == stepType) {
                    try {
                        val loadedInstance = instanceStorage.read(
                            step.getValue()!!,
                            instance.getInstanceId()!!,
                            instance.getReference()!!
                        )
                        if (loadedInstance != null) {
                            return ConcreteInstanceRoot(loadedInstance.getRoot())
                        }
                    } catch (e: VirtualInstances.InstanceNotFoundException) {
                        // continue looping
                    }
                }
            }
        }
        return ConcreteInstanceRoot.NULL
    }

    /**
     * Initialises instances with reference to 'selected_cases'
     *
     * @param instance  External data Instance that needs to be initialised
     * @param reference instance source reference
     * @param stepType  type of CommCare session frame step with which the given instance is bundled with
     * @return Initialised instance root for the given instance
     */
    protected open fun setupExternalDataInstance(
        instance: ExternalDataInstance,
        reference: String,
        stepType: String
    ): InstanceRoot {
        var instanceRoot: InstanceRoot? = getExternalDataInstanceSource(reference, stepType)

        if (instanceRoot == null) {
            // Maintain backward compatibility with instance references that don't have a id in reference
            // should be removed once we move all external data instance connectors in existing apps to new
            // reference style jr://instance/<schema>/<id>
            if (isNonUniqueReference(reference)) {
                val referenceWithId = reference + "/" + instance.getInstanceId()
                instanceRoot = getExternalDataInstanceSource(referenceWithId, stepType)

                // last attempt to find the instance
                // this is necessary for 'search-input' instances which do not follow the convention
                // of instance ref = base + instance Id:
                //    <instance id="search-input:results" ref="jr://instance/search-input/results />
                if (instanceRoot == null) {
                    instanceRoot = getExternalDataInstanceSourceById(instance.getInstanceId()!!, stepType)
                }
            }
        }

        if (instanceRoot == null) {
            instanceRoot = instance.getSource()
        }

        return instanceRoot ?: ConcreteInstanceRoot.NULL
    }

    protected open fun setupLedgerData(instance: ExternalDataInstance): InstanceRoot {
        if (stockbase == null) {
            stockbase = LedgerInstanceTreeElement(instance.getBase(), mSandbox!!.getLedgerStorage())
        } else {
            // re-use the existing model if it exists.
            stockbase!!.rebase(instance.getBase())
        }
        return ConcreteInstanceRoot(stockbase)
    }

    protected open fun setupCaseData(instance: ExternalDataInstance): InstanceRoot {
        if (casebase == null) {
            casebase = CaseInstanceTreeElement(instance.getBase(), mSandbox!!.getCaseStorage())
        } else {
            // re-use the existing model if it exists.
            casebase!!.rebase(instance.getBase())
        }
        return ConcreteInstanceRoot(casebase)
    }

    protected open fun setupFixtureData(instance: ExternalDataInstance): InstanceRoot {
        return ConcreteInstanceRoot(loadFixtureRoot(instance, instance.getReference()!!))
    }

    protected open fun loadFixtureRoot(
        instance: ExternalDataInstance,
        reference: String
    ): TreeElement {
        val refId = VirtualInstances.getReferenceId(reference)
        val instanceBase = instance.getBase()!!.getInstanceName()

        var userId = ""
        val u = mSandbox!!.getLoggedInUser()

        if (u != null) {
            userId = u.getUniqueId() ?: ""
        }

        try {
            val key = refId + userId + instanceBase

            var root = fixtureBases.retrieve(key)
            if (root == null) {
                var fixtureStorage: IStorageUtilityIndexed<FormInstance>? = null
                if (mPlatform != null) {
                    fixtureStorage = mPlatform.getFixtureStorage()
                }

                val fixture = SandboxUtils.loadFixture(
                    mSandbox,
                    refId,
                    userId,
                    fixtureStorage
                ) ?: throw FixtureInitializationException(reference)

                root = fixture.getRoot()
                fixtureBases.register(key, root)
            }

            root!!.setParent(instance.getBase())
            return root
        } catch (ise: IllegalStateException) {
            throw FixtureInitializationException(reference)
        }
    }

    protected open fun setupSessionData(instance: ExternalDataInstance): InstanceRoot {
        if (this.mPlatform == null) {
            throw RuntimeException("Cannot generate session instance with undeclared platform!")
        }
        val u = mSandbox!!.getLoggedInUserUnsafe()
        val root = SessionInstanceBuilder.getSessionInstance(
            sessionWrapper!!.getFrame(), getDeviceId(),
            getVersionString(), getCurrentDrift(), u.getUsername(), u.getUniqueId(),
            u.properties, getWindowWidth(), getLocale()
        )
        root.setParent(instance.getBase())
        return ConcreteInstanceRoot(root)
    }

    protected open fun getWindowWidth(): String? {
        return sessionWrapper!!.getWindowWidth()
    }

    protected open fun getLocale(): String? {
        return Localization.getCurrentLocale()
    }

    protected open fun getCurrentDrift(): Long {
        return 0
    }

    protected open fun getExternalDataInstanceSource(reference: String, stepType: String): InstanceRoot? {
        for (step in sessionWrapper!!.getFrame().getSteps()) {
            if (step.getType() == stepType && step.hasDataInstanceSource(reference)) {
                return step.getDataInstanceSource(reference)
            }
        }
        return null
    }

    /**
     * Required for legacy instance support
     */
    protected open fun getExternalDataInstanceSourceById(instanceId: String, stepType: String): InstanceRoot? {
        for (step in sessionWrapper!!.getFrame().getSteps()) {
            if (step.getType() == stepType) {
                val source: ExternalDataInstanceSource? = step.getDataInstanceSourceById(instanceId)
                if (source != null) {
                    return source
                }
            }
        }
        return null
    }

    protected open fun getDeviceId(): String {
        return "----"
    }

    open fun getVersionString(): String {
        return "CommCare Version: " + mPlatform!!.majorVersion + "." + mPlatform.minorVersion
    }

    class FixtureInitializationException(val reference: String) : RuntimeException(
        Localization.getWithDefault(
            "lookup.table.missing.error",
            arrayOf(reference),
            "Unable to find lookup table: $reference"
        )
    )

    companion object {
        @JvmStatic
        fun isNonUniqueReference(reference: String): Boolean {
            return reference.contentEquals(ExternalDataInstance.JR_REMOTE_REFERENCE) ||
                    reference.contentEquals(ExternalDataInstance.JR_SELECTED_ENTITIES_REFERENCE) ||
                    reference.contentEquals(ExternalDataInstance.JR_SEARCH_INPUT_REFERENCE)
        }
    }
}
