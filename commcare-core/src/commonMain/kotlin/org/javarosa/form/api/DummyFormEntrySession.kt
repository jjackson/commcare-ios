package org.javarosa.form.api

import org.javarosa.core.model.FormIndex

/**
 * Empty form entry session implementation, useful when you don't want to
 * record form entry actions
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
class DummyFormEntrySession : FormEntrySessionRecorder {
    override fun addNewRepeat(formIndex: FormIndex) {
    }

    override fun addValueSet(formIndex: FormIndex, value: String) {
    }

    override fun addQuestionSkip(formIndex: FormIndex) {
    }

    override fun toString(): String {
        return ""
    }
}
