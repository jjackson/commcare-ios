package org.javarosa.form.api.test

import org.javarosa.core.model.FormIndex
import org.javarosa.core.model.QuestionDef
import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.test.FormParseInit
import org.javarosa.form.api.FormEntryController
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
class FormEntryControllerTest {
    private lateinit var fpi: FormParseInit

    /**
     * load and parse form
     */
    @Before
    fun initForm() {
        println("init FormEntryControllerTest")
        fpi = FormParseInit("/test_form_entry_controller.xml")
    }

    /**
     * Tests constraint passing and failing when using FormEntryController to
     * answer form questions.
     *
     * TODO: create test cases that test complex questions, that is, those with
     * copy tags inside of them that need to processed.
     */
    @Test
    fun testAnswerQuestion() {
        var ans: IntegerData
        val fec = fpi.getFormEntryController()
        fec.jumpToIndex(FormIndex.createBeginningOfFormIndex())

        do {
            // get current question
            val q = fpi.getCurrentQuestion()

            if (q == null || q.getTextID() == null || "" == q.getTextID()) {
                continue
            }

            when (q.getTextID()) {
                "select-without-constraint-label" -> {
                    ans = IntegerData(20)
                    expectToPassConstraint(fec.answerQuestion(ans), q.getTextID()!!, ans.getDisplayText())
                }
                "select-with-constraint-pass-label" -> {
                    ans = IntegerData(10)
                    expectToPassConstraint(fec.answerQuestion(ans), q.getTextID()!!, ans.getDisplayText())
                }
                "select-with-constraint-fail-label" -> {
                    ans = IntegerData(31)
                    expectToFailConstraint(fec.answerQuestion(ans), q.getTextID()!!, ans.getDisplayText())
                }
                "simple-without-constraint-label" -> {
                    ans = IntegerData(40)
                    expectToPassConstraint(fec.answerQuestion(ans), q.getTextID()!!, ans.getDisplayText())
                }
                "simple-with-constraint-pass-label" -> {
                    ans = IntegerData(5)
                    expectToPassConstraint(fec.answerQuestion(ans), q.getTextID()!!, ans.getDisplayText())
                }
                "simple-with-constraint-fail-label" -> {
                    ans = IntegerData(15)
                    expectToFailConstraint(fec.answerQuestion(ans), q.getTextID()!!, ans.getDisplayText())
                }
            }
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)
    }

    /**
     * Check for response code signalling that the answer to a question passed
     * its constraint. Throw a useful error message if this isn't the case.
     */
    private fun expectToPassConstraint(responseCode: Int, questionText: String, answerAsString: String) {
        if (responseCode == FormEntryController.ANSWER_CONSTRAINT_VIOLATED) {
            fail("Answered question with a value that didn't pass its constraint: \n" +
                    "[Question] = $questionText \n" +
                    "[Answer] = $answerAsString")
        } else if (responseCode != FormEntryController.ANSWER_OK) {
            fail("Unexpected response from FormEntryController.answerQuestion(): \n" +
                    "[Response Code] = $responseCode \n" +
                    "[Question] = $questionText \n" +
                    "[Answer] = $answerAsString")
        }
    }

    /**
     * Check for response code signalling that the answer to a question failed
     * its constraint. Throw a useful error message if this isn't the case.
     */
    private fun expectToFailConstraint(responseCode: Int, questionText: String, answerAsString: String) {
        if (responseCode == FormEntryController.ANSWER_OK) {
            fail("Answered question with a value that should have failed the question's constraint: \n" +
                    "[Question] = $questionText \n" +
                    "[Answer] = $answerAsString")
        } else if (responseCode != FormEntryController.ANSWER_CONSTRAINT_VIOLATED) {
            fail("Unexpected response from FormEntryController.answerQuestion(): \n" +
                    "[Response Code] = $responseCode \n" +
                    "[Question] = $questionText \n" +
                    "[Answer] = $answerAsString")
        }
    }
}
