package org.javarosa.form.api

import org.javarosa.core.model.FormIndex

/**
 * Record form entry actions for playback
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
interface FormEntrySessionRecorder {
    fun addNewRepeat(formIndex: FormIndex)
    fun addValueSet(formIndex: FormIndex, value: String)
    fun addQuestionSkip(formIndex: FormIndex)
}
