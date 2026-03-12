package org.javarosa.form.api

import org.javarosa.core.model.FormIndex
import org.javarosa.core.model.data.AnswerDataFactory
import org.javarosa.core.model.data.UncastData
import kotlin.jvm.JvmStatic

/**
 * Replay form entry session. Steps through form, applying answers from the
 * session to corresponding questions by matching form indices. Replay aborts
 * if form indices don't match and stops when the session is empty.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
class FormEntrySessionReplayer private constructor(
    private val formEntryController: FormEntryController,
    private val formEntrySession: FormEntrySession
) {

    private fun hasSessionToReplay(): Boolean {
        return formEntrySession.size() > 0
    }

    private fun replayForm() {
        formEntryController.jumpToIndex(FormIndex.createBeginningOfFormIndex())
        var event = formEntryController.stepToNextEvent(FormEntryController.STEP_INTO_GROUP)
        var lastQuestionRefReplayed = ""
        while (event != FormEntryController.EVENT_END_OF_FORM && hasSessionToReplay()
            && !reachedEndOfReplay(lastQuestionRefReplayed)
        ) {
            lastQuestionRefReplayed = replayEvent(event)
            event = formEntryController.stepToNextEvent(FormEntryController.STEP_INTO_GROUP)
        }

        if (!formEntrySession.stopRefWasReplayed()) {
            // If the stop ref was removed in the current version of the app, we will have
            // replayed all the way to the end of the form; It seems like the least confusing
            // thing to do in this scenario is to just put them back at the beginning of the form
            formEntryController.jumpToIndex(FormIndex.createBeginningOfFormIndex())
        } else {
            // The while loop above actually lands us one question past where the user left off
            // (which is necessary in order to have actually replayed the answer to that question
            // if it exists), but now we should step back so we're sitting on the right question
            formEntryController.stepToPreviousEvent()
        }
    }

    private fun reachedEndOfReplay(lastQuestionRefReplayed: String): Boolean {
        return lastQuestionRefReplayed == formEntrySession.getStopRef()
    }

    private fun replayEvent(event: Int): String {
        if (event == FormEntryController.EVENT_QUESTION) {
            return replayQuestion()
        } else if (event == FormEntryController.EVENT_PROMPT_NEW_REPEAT) {
            return checkForRepeatCreation()
            // TODO PLM: can't handle proceeding to end of form after "Don't add" action
        }
        return ""
    }

    private fun checkForRepeatCreation(): String {
        val questionRef = formEntryController.getModel().getFormIndex().getReference()!!
        if (formEntrySession.getAndRemoveRepeatActionForRef(questionRef)) {
            formEntryController.newRepeat()
        }
        return questionRef.toString()
    }

    private fun replayQuestion(): String {
        val questionIndex = formEntryController.getModel().getFormIndex()
        val questionRef = questionIndex.getReference()!!
        val action = formEntrySession.getAndRemoveActionForRef(questionRef)
        if (action != null) {
            if (!action.isSkipAction()) {
                val entryPrompt =
                    formEntryController.getModel().getQuestionPrompt(questionIndex)
                val answerData =
                    AnswerDataFactory.template(
                        entryPrompt.getControlType(),
                        entryPrompt.getDataType()
                    ).cast(UncastData(action.getValue()))
                formEntryController.answerQuestion(questionIndex, answerData)
            }
        }
        return questionRef.toString()
    }

    class ReplayError(msg: String) : RuntimeException(msg)

    companion object {
        @JvmStatic
        fun tryReplayingFormEntry(
            formEntryController: FormEntryController,
            formEntrySession: FormEntrySession?
        ) {
            val replayer =
                FormEntrySessionReplayer(formEntryController, formEntrySession ?: return)
            if (replayer.hasSessionToReplay()) {
                replayer.replayForm()
            }
        }
    }
}
