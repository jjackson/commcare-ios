package org.javarosa.form.api

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Describes one form entry action used to replay form entry.
 * Actions include value setting, repeat creation, and skipping over questions
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
class FormEntryAction : Externalizable {
    private var questionRefString: String = ""
    private var value: String = ""
    private var action: String = ""

    /**
     * For Externalization
     */
    constructor()

    private constructor(questionRefString: String, value: String, action: String) {
        this.questionRefString = questionRefString
        this.value = value
        this.action = action
    }

    override fun toString(): String {
        return when {
            NEW_REPEAT == action -> "(($questionRefString) (NEW_REPEAT))"
            VALUE == action -> "(($questionRefString) (VALUE) ($value))"
            SKIP == action -> "(($questionRefString) (SKIP))"
            else -> ""
        }
    }

    override fun hashCode(): Int {
        return questionRefString.hashCode() xor value.hashCode() xor action.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other is FormEntryAction) {
            return questionRefString == other.questionRefString &&
                    value == other.value &&
                    action == other.action
        }
        return false
    }

    fun isNewRepeatAction(): Boolean {
        return NEW_REPEAT == action
    }

    fun isSkipAction(): Boolean {
        return SKIP == action
    }

    fun getValue(): String {
        return value
    }

    fun getQuestionRefString(): String {
        return questionRefString
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        questionRefString = ExtUtil.readString(`in`)
        value = ExtUtil.readString(`in`)
        action = ExtUtil.readString(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeString(out, questionRefString)
        ExtUtil.writeString(out, value)
        ExtUtil.writeString(out, action)
    }

    companion object {
        private const val NEW_REPEAT = "NEW_REPEAT"
        private const val SKIP = "SKIP"
        private const val VALUE = "VALUE"

        @JvmStatic
        fun buildNewRepeatAction(questionRefString: String): FormEntryAction {
            return FormEntryAction(questionRefString, "", NEW_REPEAT)
        }

        @JvmStatic
        fun buildValueSetAction(questionRefString: String, value: String): FormEntryAction {
            return FormEntryAction(questionRefString, value, VALUE)
        }

        @JvmStatic
        fun buildSkipAction(questionRefString: String): FormEntryAction {
            return FormEntryAction(questionRefString, "", SKIP)
        }

        @JvmStatic
        fun buildNullAction(): FormEntryAction {
            return FormEntryAction("", "", "")
        }

        @JvmStatic
        fun fromString(entryActionString: String): FormEntryAction {
            val unwrappedEntryActionString =
                entryActionString.substring(1, entryActionString.length - 1)
            val actionEntries = FormEntrySession.splitTopParens(unwrappedEntryActionString)
            val entryCount = actionEntries.size

            if (entryCount != 2 && entryCount != 3) {
                throw RuntimeException(
                    "Form entry action '$entryActionString' has an incorrect number of entries, expected 2 or 3, got $entryCount"
                )
            }

            val wrappedQuestionRefString = actionEntries[0]
            val questionRefString = wrappedQuestionRefString.substring(1, wrappedQuestionRefString.length - 1)
            return if (entryCount == 2) {
                if ("($NEW_REPEAT)" == actionEntries[1]) {
                    buildNewRepeatAction(questionRefString)
                } else {
                    buildSkipAction(questionRefString)
                }
            } else {
                val wrappedValue = actionEntries[2]
                val value = wrappedValue.substring(1, wrappedValue.length - 1)
                buildValueSetAction(questionRefString, value)
            }
        }
    }
}
