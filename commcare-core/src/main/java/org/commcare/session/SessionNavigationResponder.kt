package org.commcare.session

import org.javarosa.core.model.condition.EvaluationContext
import java.util.concurrent.locks.ReentrantLock

/**
 * Interface defining all functionality to be implemented by any class that will receive and
 * process status codes from a SessionNavigator
 *
 * @author amstone
 */
interface SessionNavigationResponder {

    // Define responses to each of the status codes in SessionNavigator
    fun processSessionResponse(statusCode: Int)

    // Provide a hook to the current CommCareSession that the SessionNavigator should be polling
    fun getSessionForNavigator(): CommCareSession

    // Provide a hook to the current evaluation context that the SessionNavigator will use
    fun getEvalContextForNavigator(): EvaluationContext

    fun getBackgroundSyncLock(): ReentrantLock
}
