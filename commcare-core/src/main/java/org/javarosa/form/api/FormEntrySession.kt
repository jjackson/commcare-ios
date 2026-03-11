package org.javarosa.form.api

import org.javarosa.core.model.FormIndex
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Records form entry actions, associating question references with user (string)
 * answers. Updating an answer does not change its ordering in the action list.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
class FormEntrySession : FormEntrySessionRecorder, Externalizable {

    private var actions: ArrayList<FormEntryAction> = ArrayList()
    private var sessionStopRef: String? = null
    private var stopRefWasReplayed: Boolean = false

    /**
     * For Externalization
     */
    constructor()

    override fun addNewRepeat(formIndex: FormIndex) {
        val questionRefString = formIndex.getReference().toString()
        val insertIndex = removeDuplicateAction(questionRefString)
        actions.add(insertIndex, FormEntryAction.buildNewRepeatAction(questionRefString))
    }

    private fun removeDuplicateAction(questionRefString: String): Int {
        for (i in actions.size - 1 downTo 0) {
            if (actions[i].getQuestionRefString() == questionRefString) {
                actions.removeAt(i)
                return i
            }
        }
        return actions.size
    }

    override fun addValueSet(formIndex: FormIndex, value: String) {
        val questionRefString = formIndex.getReference().toString()
        val insertIndex = removeDuplicateAction(questionRefString)
        actions.add(insertIndex, FormEntryAction.buildValueSetAction(questionRefString, value))
    }

    override fun addQuestionSkip(formIndex: FormIndex) {
        val questionRefString = formIndex.getReference().toString()
        val insertIndex = removeDuplicateAction(questionRefString)
        actions.add(insertIndex, FormEntryAction.buildSkipAction(questionRefString))
    }

    fun peekAction(): FormEntryAction {
        return if (actions.size > 0) {
            actions[0]
        } else {
            FormEntryAction.buildNullAction()
        }
    }

    /**
     * @return the ref path for the last action in this form entry session (e.g. where the user
     * stopped form entry)
     */
    fun getStopRef(): String? {
        return this.sessionStopRef
    }

    fun stopRefWasReplayed(): Boolean {
        return stopRefWasReplayed
    }

    /**
     * Remove and return the FormEntryAction corresponding to the given FormIndex, if there is
     * one in this session
     */
    fun getAndRemoveActionForRef(questionRef: TreeReference): FormEntryAction? {
        for (i in 0 until actions.size) {
            val action = actions[i]
            if (action.getQuestionRefString() == questionRef.toString()) {
                if (sessionStopRef == action.getQuestionRefString()) {
                    stopRefWasReplayed = true
                }
                actions.removeAt(i)
                return action
            }
        }
        return null
    }

    /**
     * Returns whether a NEW_REPEAT action exists for this questionRef, and removes it if it does
     */
    fun getAndRemoveRepeatActionForRef(questionRef: TreeReference): Boolean {
        for (action in actions) {
            if (action.isNewRepeatAction() &&
                action.getQuestionRefString() == questionRef.toString()
            ) {
                return actions.remove(action)
            }
        }
        return false
    }

    fun size(): Int {
        return actions.size
    }

    override fun toString(): String {
        val sessionStringBuilder = StringBuilder()

        for (formEntryAction in actions) {
            sessionStringBuilder.append(formEntryAction).append(" ")
        }

        return sessionStringBuilder.toString().trim()
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        actions = SerializationHelpers.readList(`in`, pf) { FormEntryAction() }
        sessionStopRef = computeStopRef(actions)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeList(out, actions)
    }

    companion object {
        @JvmStatic
        fun fromString(sessionString: String): FormEntrySession {
            val formEntrySession = FormEntrySession()
            for (actionString in splitTopParens(sessionString)) {
                formEntrySession.actions.add(FormEntryAction.fromString(actionString))
            }

            formEntrySession.sessionStopRef = computeStopRef(formEntrySession.actions)
            return formEntrySession
        }

        @JvmStatic
        fun splitTopParens(sessionString: String): ArrayList<String> {
            var wasEscapeChar = false
            var parenDepth = 0
            var topParenStart = 0
            val tokens = ArrayList<String>()

            for (i in 0 until sessionString.length) {
                val c = sessionString[i]
                if (c == '\\') {
                    wasEscapeChar = !wasEscapeChar
                } else if ((c == '(' || c == ')') && wasEscapeChar) {
                    wasEscapeChar = false
                } else if (c == '(') {
                    parenDepth++
                    if (parenDepth == 1) {
                        topParenStart = i
                    }
                } else if (c == ')') {
                    if (parenDepth == 1) {
                        tokens.add(sessionString.substring(topParenStart, i + 1))
                    }
                    parenDepth--
                }
            }

            return tokens
        }

        private fun computeStopRef(actions: ArrayList<FormEntryAction>): String {
            return actions[actions.size - 1].getQuestionRefString()
        }
    }
}
