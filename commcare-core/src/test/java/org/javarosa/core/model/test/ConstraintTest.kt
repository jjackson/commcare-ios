package org.javarosa.core.model.test

import org.javarosa.core.model.FormIndex
import org.javarosa.core.model.condition.pivot.IntegerRangeHint
import org.javarosa.core.model.condition.pivot.UnpivotableExpressionException
import org.javarosa.core.test.FormParseInit
import org.javarosa.form.api.FormEntryController
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * @author William Pride (wpride@dimagi.com)
 */
class ConstraintTest {
    private lateinit var fpi: FormParseInit

    /**
     * load and parse form
     */
    @Before
    fun initForm() {
        println("init Constraint Test")
        fpi = FormParseInit("/test_constraints.xml")
    }

    /**
     * Tests constraint passing and failing when using FormEntryController to
     * answer form questions.
     */
    @Test
    fun testAnswerQuestion() {
        val fec = fpi.getFormEntryController()
        fec.jumpToIndex(FormIndex.createBeginningOfFormIndex())

        do {
            val q = fpi.getCurrentQuestion()

            if (q == null || q.getTextID() == null || "" == q.getTextID()) {
                continue
            }

            when (q.getTextID()) {
                "constraint-max-label" -> assertConstraintMaxMin(30, null)
                "constraint-min-label" -> assertConstraintMaxMin(null, 10)
                "constraint-max-or-min-label" -> assertUnpivotable()
                "constraint-max-and-min-label" -> assertUnpivotable()
            }
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)
    }

    private fun assertConstraintMaxMin(max: Int?, min: Int?) {
        val hint = IntegerRangeHint()
        val prompt = fpi.getFormEntryModel().getQuestionPrompt()
        try {
            prompt.requestConstraintHint(hint)

            if (max != null) {
                assert(max == hint.getMax()!!.getValue() as Int)
            } else {
                assert(hint.getMax() == null)
            }

            if (min != null) {
                assert(min == hint.getMin()!!.getValue() as Int)
            } else {
                assert(hint.getMin() == null)
            }
        } catch (e: UnpivotableExpressionException) {
            e.printStackTrace()
            fail(e.message)
        }
    }

    private fun assertUnpivotable() {
        val hint = IntegerRangeHint()
        val prompt = fpi.getFormEntryModel().getQuestionPrompt()
        try {
            prompt.requestConstraintHint(hint)
            fail("Should have not been able to pivot with prompt: $prompt")
        } catch (e: UnpivotableExpressionException) {
            // good
        }
    }
}
