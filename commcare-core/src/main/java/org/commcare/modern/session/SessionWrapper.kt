package org.commcare.modern.session

import org.commcare.core.interfaces.RemoteInstanceFetcher
import org.commcare.core.interfaces.UserSandbox
import org.commcare.core.process.CommCareInstanceInitializer
import org.commcare.session.CommCareSession
import org.commcare.util.CommCarePlatform
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.xpath.analysis.InstanceNameAccumulatingAnalyzer
import org.javarosa.xpath.analysis.XPathAnalyzable

/**
 * Extends a generic CommCare session to include context about the
 * current runtime environment
 *
 * @author ctsims
 */
open class SessionWrapper : CommCareSession, SessionWrapperInterface {

    @JvmField
    protected val mSandbox: UserSandbox
    @JvmField
    protected val mPlatform: CommCarePlatform
    @JvmField
    protected var initializer: CommCareInstanceInitializer? = null
    @JvmField
    protected var remoteInstanceFetcher: RemoteInstanceFetcher? = null
    /**
     * A string representing the width of the user's screen in pixels.
     * To be used in a display condition determining what content to show to the user.
     */
    private var windowWidth: String? = null

    constructor(session: CommCareSession, platform: CommCarePlatform, sandbox: UserSandbox,
                remoteInstanceFetcher: RemoteInstanceFetcher?, windowWidth: String?) : this(platform, sandbox, remoteInstanceFetcher, windowWidth) {
        this.frame = session.frame
        this.frameStack = session.frameStack
    }

    constructor(session: CommCareSession, platform: CommCarePlatform, sandbox: UserSandbox, windowWidth: String?) : this(session, platform, sandbox, null, windowWidth)

    constructor(session: CommCareSession, platform: CommCarePlatform, sandbox: UserSandbox) : this(session, platform, sandbox, null, null)

    constructor(platform: CommCarePlatform, sandbox: UserSandbox) : super(platform) {
        this.mSandbox = sandbox
        this.mPlatform = platform
    }

    constructor(platform: CommCarePlatform, sandbox: UserSandbox, remoteInstanceFetcher: RemoteInstanceFetcher?, windowWidth: String?) : super(platform) {
        this.mSandbox = sandbox
        this.mPlatform = platform
        this.remoteInstanceFetcher = remoteInstanceFetcher
        this.windowWidth = windowWidth
    }

    /**
     * @return The evaluation context for the current state.
     */
    override fun getEvaluationContext(): EvaluationContext {
        return getEvaluationContext(getIIF())
    }

    override fun getRestrictedEvaluationContext(commandId: String,
                                                instancesToInclude: Set<String>): EvaluationContext {
        return getEvaluationContext(getIIF(), commandId, instancesToInclude)
    }

    override fun getEvaluationContextWithAccumulatedInstances(commandID: String, xPathAnalyzable: XPathAnalyzable): EvaluationContext {
        val instancesNeededForTextCalculation =
            InstanceNameAccumulatingAnalyzer().accumulate(xPathAnalyzable) ?: emptySet()
        return getRestrictedEvaluationContext(commandID, instancesNeededForTextCalculation)
    }

    /**
     * @param commandId The id of the command to evaluate against
     * @return The evaluation context relevant for the provided command id
     */
    override fun getEvaluationContext(commandId: String): EvaluationContext {
        return getEvaluationContext(getIIF(), commandId, null)
    }

    override fun getIIF(): CommCareInstanceInitializer {
        if (initializer == null) {
            initializer = CommCareInstanceInitializer(this, mSandbox, mPlatform)
        }
        return initializer!!
    }

    @Throws(RemoteInstanceFetcher.RemoteInstanceException::class)
    override fun prepareExternalSources() {
        for (step in frame.getSteps()) {
            step.initDataInstanceSources(remoteInstanceFetcher)
        }
    }

    override fun getPlatform(): CommCarePlatform {
        return this.mPlatform
    }

    fun getSandbox(): UserSandbox {
        return this.mSandbox
    }

    fun clearVolatiles() {
        initializer = null
    }

    fun setComputedDatum() {
        setComputedDatum(getEvaluationContext())
    }

    override fun getNeededData(): String? {
        return super.getNeededData(getEvaluationContext())
    }

    fun stepBack() {
        super.stepBack(getEvaluationContext())
    }

    fun getRemoteInstanceFetcher(): RemoteInstanceFetcher? {
        return remoteInstanceFetcher
    }

    fun getWindowWidth(): String? {
        return this.windowWidth
    }
}
