package org.commcare.app.engine

import org.commcare.app.storage.SqlDelightUserSandbox
import org.commcare.modern.session.SessionWrapper
import org.commcare.session.SessionFrame
import org.commcare.session.SessionNavigationResponder
import org.commcare.suite.model.SessionDatum
import org.commcare.util.CommCarePlatform

/**
 * Navigates through the CommCare session state machine.
 * Wraps SessionWrapper and dispatches getNeededData() to determine the next UI screen.
 * Supports session stack operations for form linking and chained workflows.
 */
class SessionNavigatorImpl(
    val platform: CommCarePlatform,
    val sandbox: SqlDelightUserSandbox
) {
    val session = SessionWrapper(platform, sandbox)

    companion object {
        /** Maximum number of computed datums to auto-resolve before giving up. */
        private const val MAX_COMPUTED_DATUM_ITERATIONS = 100
    }

    /**
     * Determine what the session needs next to proceed.
     * Computed datums are resolved in a loop (bounded) rather than via recursion.
     */
    fun getNextStep(): NavigationStep {
        return try {
            var iterations = 0
            while (iterations < MAX_COMPUTED_DATUM_ITERATIONS) {
                val evalContext = session.getEvaluationContext()
                when (session.getNeededData(evalContext)) {
                    SessionFrame.STATE_COMMAND_ID -> return NavigationStep.ShowMenu
                    SessionFrame.STATE_DATUM_VAL -> {
                        val datum = session.getNeededDatum()
                        return NavigationStep.ShowCaseList(datum)
                    }
                    SessionFrame.STATE_DATUM_COMPUTED -> {
                        val evalCtx = session.getEvaluationContext()
                        session.setComputedDatum(evalCtx)
                        iterations++
                        // loop to resolve the next datum
                    }
                    SessionFrame.STATE_SYNC_REQUEST -> return NavigationStep.SyncRequired
                    SessionFrame.STATE_QUERY_REQUEST -> {
                        val datum = session.getNeededDatum()
                        return NavigationStep.ShowCaseSearch(datum)
                    }
                    null -> {
                        val formXmlns = session.getForm()
                        return NavigationStep.StartForm(formXmlns)
                    }
                    else -> return NavigationStep.Error("Unknown session state")
                }
            }
            NavigationStep.Error("Too many computed datums (exceeded $MAX_COMPUTED_DATUM_ITERATIONS)")
        } catch (e: Exception) {
            NavigationStep.Error("Navigation error: ${e.message}")
        }
    }

    fun selectCommand(commandId: String) {
        session.setCommand(commandId)
    }

    fun selectCase(caseId: String) {
        val datum = session.getNeededDatum()
        if (datum != null) {
            session.setEntityDatum(datum, caseId)
        }
    }

    fun stepBack() {
        try {
            val evalContext = session.getEvaluationContext()
            session.stepBack(evalContext)
        } catch (_: Exception) {
            // At root, nothing to step back to
        }
    }

    fun clearSession() {
        session.clearAllState()
    }

    /**
     * After form completion, execute post-entry stack operations and check for chained forms.
     * Returns true if a new frame was popped (another form in the chain), false if done.
     */
    fun finishAndPop(): Boolean {
        return try {
            val ec = session.getEvaluationContext()
            session.finishExecuteAndPop(ec)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Get the current stack depth (number of pending frames).
     */
    fun getStackDepth(): Int {
        return try {
            session.frameStack.size
        } catch (_: Exception) {
            0
        }
    }
}

sealed class NavigationStep {
    data object ShowMenu : NavigationStep()
    data class ShowCaseList(val datum: SessionDatum?) : NavigationStep()
    data class ShowCaseSearch(val datum: SessionDatum?) : NavigationStep()
    data class StartForm(val xmlns: String?) : NavigationStep()
    data object SyncRequired : NavigationStep()
    data class Error(val message: String) : NavigationStep()
}
