package org.commcare.test.utilities

import org.commcare.modern.session.SessionWrapper
import org.commcare.session.CommCareSession
import org.commcare.session.SessionNavigationResponder
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.util.PlatformLock
import java.util.concurrent.locks.ReentrantLock

/**
 * A mock implementer of the SessionNavigationResponder interface, for testing purposes
 *
 * @author amstone
 */
class MockSessionNavigationResponder(
    private val sessionWrapper: SessionWrapper
) : SessionNavigationResponder {

    var lastResultCode: Int = 0
        private set

    private val backgroundSyncLock = ReentrantLock()

    override fun processSessionResponse(statusCode: Int) {
        lastResultCode = statusCode
    }

    override fun getSessionForNavigator(): CommCareSession {
        return sessionWrapper
    }

    override fun getEvalContextForNavigator(): EvaluationContext {
        return sessionWrapper.getEvaluationContext()
    }

    override fun getBackgroundSyncLock(): PlatformLock {
        return backgroundSyncLock
    }
}
