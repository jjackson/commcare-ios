package org.commcare.session

import org.commcare.suite.model.EntityDatum
import org.commcare.suite.model.SessionDatum
import org.commcare.util.DatumUtil
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.xpath.XPathException

/**
 * Performs all logic involved in polling the current CommCareSession to determine what information
 * or action is needed by the session, and then sends a signal to the registered
 * SessionNavigationResponder to indicate what should be done to get it (or if an error occurred)
 *
 * @author amstone
 */
class SessionNavigator(private val responder: SessionNavigationResponder) {

    private var currentSession: CommCareSession? = null
    private var ec: EvaluationContext? = null

    private var currentAutoSelectedCase: TreeReference? = null
    private var thrownException: XPathException? = null

    private fun sendResponse(resultCode: Int) {
        responder.processSessionResponse(resultCode)
    }

    fun getCurrentAutoSelection(): TreeReference? {
        return this.currentAutoSelectedCase
    }

    fun getCurrentException(): XPathException? {
        return this.thrownException
    }

    /**
     * Polls the CommCareSession to determine what information is needed in order to proceed with
     * the next entry step in the session, and then executes the action to get that info, OR
     * proceeds with trying to enter the form if no more info is needed
     */
    fun startNextSessionStep() {
        currentSession = responder.getSessionForNavigator()
        ec = responder.getEvalContextForNavigator()
        val needed: String?
        try {
            needed = currentSession!!.getNeededData(ec!!)
        } catch (e: XPathException) {
            thrownException = e
            sendResponse(XPATH_EXCEPTION_THROWN)
            return
        }

        dispatchOnNeededData(needed)
    }

    private fun dispatchOnNeededData(needed: String?) {
        if (needed == null) {
            readyToProceed()
            return
        }

        when (needed) {
            SessionFrame.STATE_COMMAND_ID -> sendResponse(GET_COMMAND)
            SessionFrame.STATE_SYNC_REQUEST -> sendResponse(START_SYNC_REQUEST)
            SessionFrame.STATE_QUERY_REQUEST -> sendResponse(PROCESS_QUERY_REQUEST)
            SessionFrame.STATE_DATUM_VAL -> handleGetDatum()
            SessionFrame.STATE_DATUM_COMPUTED -> handleCompute()
        }
    }

    private fun readyToProceed() {
        val session = currentSession!!
        val evalContext = ec!!
        val text = session.getCurrentEntry().getAssertions().getAssertionFailure(evalContext)
        if (text != null) {
            // We failed one of our assertions
            sendResponse(ASSERTION_FAILURE)
        } else if (session.getForm() == null) {
            sendResponse(NO_CURRENT_FORM)
        } else {
            // The current state indicate that a form needs to be instantiated but a background sync is ongoing
            if (responder.getBackgroundSyncLock().isLocked) {
                sendResponse(FORM_ENTRY_ATTEMPT_DURING_SYNC)
                return
            }

            sendResponse(START_FORM_ENTRY)
        }
    }

    private fun handleGetDatum() {
        val autoSelection = getAutoSelectedCase()
        if (autoSelection == null) {
            sendResponse(START_ENTITY_SELECTION)
        } else {
            sendResponse(REPORT_CASE_AUTOSELECT)
            this.currentAutoSelectedCase = autoSelection
            handleAutoSelect()
        }
    }

    private fun handleAutoSelect() {
        val session = currentSession!!
        val selectDatum = session.getNeededDatum()
        if (selectDatum is EntityDatum) {
            if (selectDatum.getLongDetail() == null) {
                // No confirm detail defined for this entity select, so just set the case id right away
                // and proceed
                val autoSelectedCaseId = DatumUtil.getReturnValueFromSelection(
                    currentAutoSelectedCase!!, selectDatum, ec!!
                )
                session.setEntityDatum(selectDatum, autoSelectedCaseId)
                startNextSessionStep()
            } else {
                sendResponse(LAUNCH_CONFIRM_DETAIL)
            }
        }
    }

    /**
     * Returns the auto-selected case for the next needed datum, if there should be one.
     * Returns null if auto selection is not enabled, or if there are multiple available cases
     * for the datum (and therefore auto-selection should not be used).
     */
    private fun getAutoSelectedCase(): TreeReference? {
        val selectDatum = currentSession!!.getNeededDatum()
        if (selectDatum is EntityDatum) {
            return selectDatum.getCurrentAutoselectableCase(this.ec!!)
        }
        return null
    }

    private fun handleCompute() {
        try {
            currentSession!!.setComputedDatum(ec!!)
        } catch (e: XPathException) {
            this.thrownException = e
            sendResponse(XPATH_EXCEPTION_THROWN)
            return
        }
        startNextSessionStep()
    }

    fun stepBack() {
        currentSession!!.stepBack(ec!!)
    }

    companion object {
        // Result codes to be interpreted by the SessionNavigationResponder
        const val ASSERTION_FAILURE: Int = 0
        const val NO_CURRENT_FORM: Int = 1
        const val START_FORM_ENTRY: Int = 2
        const val GET_COMMAND: Int = 3
        const val START_ENTITY_SELECTION: Int = 4
        const val LAUNCH_CONFIRM_DETAIL: Int = 5
        const val XPATH_EXCEPTION_THROWN: Int = 6
        const val START_SYNC_REQUEST: Int = 7
        const val PROCESS_QUERY_REQUEST: Int = 8
        const val REPORT_CASE_AUTOSELECT: Int = 9
        const val FORM_ENTRY_ATTEMPT_DURING_SYNC: Int = 10
    }
}
