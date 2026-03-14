package org.javarosa.core.form.api.test

import org.javarosa.core.model.Constants
import org.javarosa.core.model.FormIndex
import org.javarosa.core.model.QuestionDef
import org.javarosa.core.model.QuestionString
import org.javarosa.core.model.SelectChoice
import org.javarosa.core.model.test.DummyFormEntryPrompt
import org.javarosa.core.services.PrototypeManager
import org.javarosa.core.services.locale.Localizer
import org.javarosa.core.services.locale.TableLocaleSource
import org.javarosa.core.test.FormParseInit
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.form.api.FormEntryCaption
import org.javarosa.form.api.FormEntryController
import org.javarosa.form.api.FormEntryPrompt
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TextFormTests {
    var q: QuestionDef? = null
    var fep: FormEntryPrompt? = null
    var fpi: FormParseInit? = null

    companion object {
        @JvmStatic
        val pf: PrototypeFactory

        init {
            PrototypeManager.registerPrototype("org.javarosa.model.xform.XPathReference")
            pf = ExtUtil.defaultPrototypes()
        }
    }

    @Before
    fun initStuff() {
        fpi = FormParseInit("/ImageSelectTester.xhtml")
        q = fpi!!.getFirstQuestionDef()
        fep = FormEntryPrompt(fpi!!.getFormDef()!!, fpi!!.getFormEntryModel().getFormIndex())
    }

    @Test
    fun testConstructors() {
        var q: QuestionDef

        q = QuestionDef()
        if (q.getID() != -1) {
            fail("QuestionDef not initialized properly (default constructor)")
        }

        q = QuestionDef(17, Constants.CONTROL_RANGE)
        if (q.getID() != 17) {
            fail("QuestionDef not initialized properly")
        }
        if (q.getControlType() != Constants.CONTROL_RANGE) {
            fail("QuestionDef not initialized properly")
        }
    }

    /**
     * Test that the long and short text forms work as expected
     * (fallback to default for example).
     * Test being able to retrieve other exotic forms
     */
    @Test
    fun testTextForms() {
        val fec = fpi!!.getFormEntryController()
        fec.jumpToIndex(FormIndex.createBeginningOfFormIndex())

        val l = fpi!!.getFormDef()!!.getLocalizer()!!

        l.setDefaultLocale(l.availableLocales[0])
        l.setLocale(l.availableLocales[0])
        var state = fec.getModel().getEvent()
        while (state != FormEntryController.EVENT_QUESTION) {
            state = fec.stepToNextEvent()
        }
        fep = fec.getModel().getQuestionPrompt()

        if (fep!!.getLongText() != "Patient ID") {
            fail("getLongText() not returning correct value")
        }
        if (fep!!.getShortText() != "ID") {
            fail("getShortText() not returning correct value")
        }
        if (fep!!.getAudioText() != "jr://audio/hah.mp3") {
            fail("getAudioText() not returning correct value")
        }

        state = -99
        while (state != FormEntryController.EVENT_QUESTION) {
            state = fec.stepToNextEvent()
        }
        fep = fec.getModel().getQuestionPrompt()

        if (fep!!.getLongText() != "Full Name")
            fail("getLongText() not falling back to default text form correctly, returned: " + fep!!.getLongText())
        if (fep!!.getSpecialFormQuestionText("long") != null)
            fail("getSpecialFormQuestionText() returning incorrect value")
    }

    @Test
    fun testNonLocalizedText() {
        val fec = fpi!!.getFormEntryController()
        fec.jumpToIndex(FormIndex.createBeginningOfFormIndex())
        var testFlag = false
        val l = fpi!!.getFormDef()!!.getLocalizer()!!

        l.setDefaultLocale(l.availableLocales[0])
        l.setLocale(l.availableLocales[0])

        do {
            if (fpi!!.getCurrentQuestion() == null) continue
            val q = fpi!!.getCurrentQuestion()
            fep = fpi!!.getFormEntryModel().getQuestionPrompt()
            val t = fep!!.getQuestionText() ?: continue
            if (t == "Non-Localized label inner text!") testFlag = true
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)

        if (!testFlag) fail("Failed to fallback to labelInnerText in testNonLocalizedText()")
    }

    @Test
    fun testSelectChoiceIDsNoLocalizer() {
        val q = fpi!!.getFirstQuestionDef()

        q!!.addSelectChoice(SelectChoice("choice1 id", "val1"))
        q.addSelectChoice(SelectChoice("loc: choice2", "val2", false))

        if (fep!!.getSelectChoices().toString() != "[{choice1 id} => val1, loc: choice2 => val2]") {
            fail("Could not add individual select choice ID" + fep!!.getSelectChoices().toString())
        }

        // clean up
        q.removeSelectChoice(q.getChoices()!![0])
        q.removeSelectChoice(q.getChoices()!![0])
    }

    @Test
    fun testSelectChoicesNoLocalizer() {
        val q = fpi!!.getFirstQuestionDef()
        if (q!!.getNumChoices() != 0) {
            fail("Select choices not empty on init")
        }

        val onetext = "choice"
        val twotext = "stacey's"
        val one = SelectChoice(null, onetext, "val", false)
        q.addSelectChoice(one)
        val two = SelectChoice(null, twotext, "mom", false)
        q.addSelectChoice(two)

        if (fep!!.getSelectChoices().toString() != "[choice => val, stacey's => mom]") {
            fail("Could not add individual select choice" + fep!!.getSelectChoices().toString())
        }

        val b = fep!!.getSelectChoiceText(one)
        assertEquals("Invalid select choice text returned", onetext, b)

        assertEquals("Invalid select choice text returned", twotext, fep!!.getSelectChoiceText(two))

        assertNull("Form Entry Caption incorrectly contains Image Text",
                fep!!.getSpecialFormSelectChoiceText(one, FormEntryCaption.TEXT_FORM_IMAGE))

        assertNull("Form Entry Caption incorrectly contains Audio Text",
                fep!!.getSpecialFormSelectChoiceText(one, FormEntryCaption.TEXT_FORM_AUDIO))

        q.removeSelectChoice(q.getChoice(0))
        q.removeSelectChoice(q.getChoice(0))
    }

    @Test
    fun testPromptsWithLocalizer() {
        val l = Localizer()

        val table = TableLocaleSource()
        l.addAvailableLocale("locale")
        l.setDefaultLocale("locale")
        table.setLocaleMapping("prompt;long", "loc: long text")
        table.setLocaleMapping("prompt;short", "loc: short text")
        table.setLocaleMapping("help", "loc: help text")
        l.registerLocaleResource("locale", table)

        l.setLocale("locale")

        val q = QuestionDef()

        val helpString = QuestionString("long")
        helpString.textId = "help"

        q.putQuestionString("long", helpString)
        val fep = DummyFormEntryPrompt(l, "prompt", q)

        if ("loc: long text" != fep.getLongText()) {
            fail("Long text did not localize properly")
        }
        if ("loc: short text" != fep.getShortText()) {
            fail("Short text did not localize properly")
        }
    }

    @Test
    fun testPromptIDsNoLocalizer() {
        val q = QuestionDef()

        q.setTextID("long text id")
        if ("long text id" != q.getTextID()) {
            fail("Long text ID getter/setter broken")
        }

        val hint = QuestionString("hint")
        hint.textId = "hint text id"
        q.putQuestionString("hint", hint)
        if ("hint text id" != q.getQuestionString("hint")!!.textId) {
            fail("hint text ID getter/setter broken")
        }
    }

    @Test
    fun testPromptsNoLocalizer() {
        val q = QuestionDef()

        q.putQuestionString("help", QuestionString("help", "help text"))
        if ("help text" != q.getQuestionString("help")!!.textInner) {
            fail("Help text getter/setter broken")
        }
    }
}
