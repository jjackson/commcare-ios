package org.javarosa.form.api

import org.javarosa.core.model.FormIndex
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Vector

/**
 * Records form entry actions, associating question references with user (string)
 * answers. Updating an answer does not change its ordering in the action list.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
class FormEntrySession : FormEntrySessionRecorder, Externalizable {

    private var actions: Vector<FormEntryAction> = Vector()
    private var sessionStopRef: String? = null
    private var stopRefWasReplayed: Boolean = false

    /**
     * For Externalization
     */
    constructor()

    override fun addNewRepeat(formIndex: FormIndex) {
        val questionRefString = formIndex.getReference().toString()
        val insertIndex = removeDuplicateAction(questionRefString)
        actions.insertElementAt(FormEntryAction.buildNewRepeatAction(questionRefString), insertIndex)
    }

    private fun removeDuplicateAction(questionRefString: String): Int {
        for (i in actions.size - 1 downTo 0) {
            if (actions.elementAt(i).getQuestionRefString() == questionRefString) {
                actions.removeElementAt(i)
                return i
            }
        }
        return actions.size
    }

    override fun addValueSet(formIndex: FormIndex, value: String) {
        val questionRefString = formIndex.getReference().toString()
        val insertIndex = removeDuplicateAction(questionRefString)
        actions.insertElementAt(FormEntryAction.buildValueSetAction(questionRefString, value), insertIndex)
    }

    override fun addQuestionSkip(formIndex: FormIndex) {
        val questionRefString = formIndex.getReference().toString()
        val insertIndex = removeDuplicateAction(questionRefString)
        actions.insertElementAt(FormEntryAction.buildSkipAction(questionRefString), insertIndex)
    }

    fun peekAction(): FormEntryAction {
        return if (actions.size > 0) {
            actions.elementAt(0)
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
            val action = actions.elementAt(i)
            if (action.getQuestionRefString() == questionRef.toString()) {
                if (sessionStopRef == action.getQuestionRefString()) {
                    stopRefWasReplayed = true
                }
                actions.removeElementAt(i)
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
                return actions.removeElement(action)
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

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        @Suppress("UNCHECKED_CAST")
        actions = ExtUtil.read(`in`, ExtWrapList(FormEntryAction::class.java), pf) as Vector<FormEntryAction>
        sessionStopRef = computeStopRef(actions)
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.write(out, ExtWrapList(actions))
    }

    companion object {
        @JvmStatic
        fun fromString(sessionString: String): FormEntrySession {
            val formEntrySession = FormEntrySession()
            for (actionString in splitTopParens(sessionString)) {
                formEntrySession.actions.addElement(FormEntryAction.fromString(actionString))
            }

            formEntrySession.sessionStopRef = computeStopRef(formEntrySession.actions)
            return formEntrySession
        }

        @JvmStatic
        fun splitTopParens(sessionString: String): Vector<String> {
            var wasEscapeChar = false
            var parenDepth = 0
            var topParenStart = 0
            val tokens = Vector<String>()

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
                        tokens.addElement(sessionString.substring(topParenStart, i + 1))
                    }
                    parenDepth--
                }
            }

            return tokens
        }

        private fun computeStopRef(actions: Vector<FormEntryAction>): String {
            return actions.elementAt(actions.size - 1).getQuestionRefString()
        }
    }
}
