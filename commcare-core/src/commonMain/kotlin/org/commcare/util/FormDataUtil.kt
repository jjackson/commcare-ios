package org.commcare.util

import org.commcare.cases.model.Case
import org.commcare.core.interfaces.UserSandbox
import org.commcare.session.CommCareSession
import org.commcare.suite.model.ComputedDatum
import org.commcare.suite.model.EntityDatum
import org.commcare.suite.model.SessionDatum
import org.commcare.suite.model.StackFrameStep
import org.commcare.suite.model.Text
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import kotlin.jvm.JvmStatic

/**
 * Use the session state descriptor attached to saved forms to load case
 * information, such as the case name.
 */
object FormDataUtil {

    @JvmStatic
    fun getTitleFromSession(
        userSandbox: UserSandbox,
        session: CommCareSession,
        evalContext: EvaluationContext
    ): String? {
        val sessionCopy = CommCareSession(session)
        var datumValue: String? = null

        while (sessionCopy.getFrame().getSteps().size > 0) {
            val datum = sessionCopy.getNeededDatum()
            val poppedStep = sessionCopy.getPoppedStep()
            if (isCaseIdComputedDatum(datum, datumValue, poppedStep)) {
                datumValue = poppedStep?.getValue()
            } else if (datum is EntityDatum) {
                val tmpDatumValue = poppedStep?.getValue()
                if (tmpDatumValue != null) {
                    datumValue = tmpDatumValue
                }
                if (datum.getLongDetail() == null) {
                    // In the absence of a case detail, use the plain case name as the title
                    break
                } else {
                    return loadTitleFromEntity(datum, datumValue, evalContext, sessionCopy, userSandbox)
                }
            }
            sessionCopy.popStep(evalContext)
        }

        return if (datumValue == null) {
            null
        } else {
            getCaseName(userSandbox, datumValue)
        }
    }

    private fun isCaseIdComputedDatum(
        datum: SessionDatum?,
        currentDatumValue: String?,
        poppedStep: StackFrameStep?
    ): Boolean {
        return datum is ComputedDatum &&
                (currentDatumValue == null || poppedStep?.getId() == datum.getDataId())
    }

    private fun loadTitleFromEntity(
        entityDatum: EntityDatum, value: String?,
        evalContext: EvaluationContext,
        sessionCopy: CommCareSession,
        userSandbox: UserSandbox
    ): String? {
        val elem = entityDatum.getEntityFromID(evalContext, value ?: "")
        if (elem == null) {
            return null
        }

        val detailText = sessionCopy.getDetail(entityDatum.getLongDetail())?.title?.text
        var isPrettyPrint = true

        // CTS: this is... not awesome.
        // But we're going to use this to test whether we _need_ an evaluation context
        // for this. (If not, the title doesn't have prettyprint for us)
        try {
            val outcome = detailText?.evaluate()
            if (outcome != null) {
                isPrettyPrint = false
            }
        } catch (e: Exception) {
            // Cool. Got us a fancy string.
        }

        return if (isPrettyPrint) {
            // Get the detail title for that element
            val elementContext = EvaluationContext(evalContext, elem)
            detailText?.evaluate(elementContext)
        } else {
            getCaseName(userSandbox, value!!)
        }
    }

    @JvmStatic
    fun getCaseName(userSandbox: UserSandbox, caseId: String): String? {
        return try {
            val ourCase = userSandbox.getCaseStorage().getRecordForValue(Case.INDEX_CASE_ID, caseId)
            ourCase?.getName()
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun getCaseName(
        userSandbox: UserSandbox,
        caseSearchStorage: IStorageUtilityIndexed<Case>?,
        caseId: String
    ): String? {
        val caseName = getCaseName(userSandbox, caseId)
        if (caseName != null) {
            return caseName
        }

        if (caseSearchStorage != null && caseSearchStorage.isStorageExists()) {
            try {
                val ourCase = caseSearchStorage.getRecordForValue(Case.INDEX_CASE_ID, caseId)
                if (ourCase != null) {
                    return ourCase.getName()
                }
            } catch (searchException: Exception) {
                return null
            }
        }

        return null
    }
}
