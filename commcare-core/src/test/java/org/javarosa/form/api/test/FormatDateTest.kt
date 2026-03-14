package org.javarosa.form.api.test

import org.javarosa.core.model.FormIndex
import org.javarosa.core.model.data.DateData
import org.javarosa.core.test.FormParseInit
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * @author wspride
 */
class FormatDateTest {
    private lateinit var fpi: FormParseInit

    /**
     * load and parse form
     */
    @Before
    fun initForm() {
        println("init FormDateTest")
        fpi = FormParseInit("/format_date_tests.xml")
    }

    /**
     * Tests whether format-date works for date strings,
     * both wrapped and not wrapped by date()
     */
    @Test
    fun testAnswerQuestion() {
        val fec = fpi.getFormEntryController()
        fec.jumpToIndex(FormIndex.createBeginningOfFormIndex())
        val l = fpi.getFormDef()!!.getLocalizer()!!
        l.setDefaultLocale(l.availableLocales[0])
        l.setLocale(l.availableLocales[0])
        fec.stepToNextEvent()

        val ans = DateData(Date())
        fec.answerQuestion(ans)
        fec.stepToNextEvent()

        var prompt = fpi.getFormEntryModel().getQuestionPrompt()
        var unwrappedDateString = prompt.getLongText()
        var javaDateString = SimpleDateFormat("d MMM, yyyy", Locale.US).format(Date())
        assertEquals(javaDateString, unwrappedDateString)

        fec.stepToNextEvent()
        prompt = fpi.getFormEntryModel().getQuestionPrompt()
        unwrappedDateString = prompt.getLongText()
        javaDateString = SimpleDateFormat("d MMM, yyyy", Locale.US).format(Date())
        assertEquals(javaDateString, unwrappedDateString)
    }
}
