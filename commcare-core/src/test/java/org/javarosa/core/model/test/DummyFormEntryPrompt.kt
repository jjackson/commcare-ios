package org.javarosa.core.model.test

import org.javarosa.core.model.QuestionDef
import org.javarosa.core.services.locale.Localizer
import org.javarosa.form.api.FormEntryPrompt

class DummyFormEntryPrompt(
    private val _localizer: Localizer,
    private val textId: String,
    q: QuestionDef
) : FormEntryPrompt() {

    init {
        this.element = q
    }

    override fun getTextID(): String {
        return textId
    }

    override fun localizer(): Localizer {
        return _localizer
    }

    override fun substituteStringArgs(template: String?): String? {
        return template
    }
}
