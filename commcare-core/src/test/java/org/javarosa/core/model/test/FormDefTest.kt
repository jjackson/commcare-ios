package org.javarosa.core.model.test

import org.javarosa.core.model.Constants
import org.javarosa.core.model.FormIndex
import org.javarosa.core.model.GroupDef
import org.javarosa.core.model.QuestionDef
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.IFunctionHandler
import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.model.data.SelectOneData
import org.javarosa.core.model.data.StringData
import org.javarosa.core.model.data.UncastData
import org.javarosa.core.model.data.DateData
import org.javarosa.core.model.data.helper.Selection
import org.javarosa.core.model.instance.test.DummyInstanceInitializationFactory
import org.javarosa.core.model.utils.test.PersistableSandbox
import org.javarosa.core.test.FormParseInit
import org.javarosa.form.api.FormEntryController
import org.javarosa.test_utils.ExprEvalUtils
import org.javarosa.xpath.parser.XPathSyntaxException
import org.junit.Assert
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.ArrayList
import java.util.Calendar
import java.util.Date

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
class FormDefTest {

    /**
     * Make sure that 'current()' expands correctly when used in conditionals
     * such as in 'relevant' tags.
     */
    @Test
    fun testCurrentFuncInTriggers() {
        val fpi = FormParseInit("/trigger_and_current_tests.xml")
        val fec = initFormEntry(fpi)

        do {
            val q = fpi.getCurrentQuestion() ?: continue
            val qRef = q.getBind()!!.reference

            if (qRef.toString() == "/data/show") {
                val response = fec.answerQuestion(StringData("no"))
                if (response != FormEntryController.ANSWER_OK) {
                    fail("Bad response from fec.answerQuestion()")
                }
            } else if (q.getID() == 2) {
                fail("shouldn't be relevant after answering no before")
            }
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)
    }

    /**
     * Make sure that relative references in bind elements are correctly
     * contextualized.
     */
    @Test
    fun testRelativeRefInTriggers() {
        val fpi = FormParseInit("/test_nested_preds_with_rel_refs.xml")
        val fec = fpi.getFormEntryController()
        fec.jumpToIndex(FormIndex.createBeginningOfFormIndex())

        val fd = fpi.getFormDef()!!
        fd.initialize(true, DummyInstanceInitializationFactory())

        val instance = fd.getMainInstance()

        val errorMsg = ExprEvalUtils.expectedEval("/data/query-one", instance, null, "0", null)
        assertTrue(errorMsg, "" == errorMsg)

        val shouldBePresent = booleanArrayOf(true, true)

        do {
            val q = fpi.getCurrentQuestion() ?: continue

            if (q.getID() <= shouldBePresent.size && !shouldBePresent[q.getID() - 1]) {
                fail("question with id " + q.getID() + " shouldn't be relevant")
            }
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)
    }

    @Test
    fun testAnswerConstraint() {
        val fpi = FormParseInit("/ImageSelectTester.xhtml")
        val fec = initFormEntry(fpi)

        do {
            val q = fpi.getCurrentQuestion()
            if (q == null || q.getTextID() == null || "" == q.getTextID()) {
                continue
            }
            if (q.getTextID() == "constraint-test") {
                val response = fec.answerQuestion(IntegerData(13))
                if (response == FormEntryController.ANSWER_CONSTRAINT_VIOLATED) {
                    fail("Answer Constraint test failed.")
                } else if (response == FormEntryController.ANSWER_OK) {
                    break
                } else {
                    fail("Bad response from fec.answerQuestion()")
                }
            }
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)
    }

    @Test
    fun testAnswerConstraintOldText() {
        val ans = IntegerData(7)
        val fpi = FormParseInit("/ImageSelectTester.xhtml")
        val fec = initFormEntry(fpi)
        fec.setLanguage("English")

        do {
            val q = fpi.getCurrentQuestion()
            if (q == null || q.getTextID() == null || "" == q.getTextID()) {
                continue
            }
            if (q.getTextID() == "constraint-test") {
                val response = fec.answerQuestion(ans)
                if (response == FormEntryController.ANSWER_CONSTRAINT_VIOLATED) {
                    if ("Old Constraint" != fec.getModel().getQuestionPrompt().getConstraintText()) {
                        fail("Old constraint message not found, instead got: "
                                + fec.getModel().getQuestionPrompt().getConstraintText())
                    }
                } else if (response == FormEntryController.ANSWER_OK) {
                    fail("Should have constrained")
                    break
                }
            }
            if (q.getTextID() == "constraint-test-2") {
                val response3 = fec.answerQuestion(IntegerData(13))
                if (response3 == FormEntryController.ANSWER_CONSTRAINT_VIOLATED) {
                    if ("New Alert" != fec.getModel().getQuestionPrompt().getConstraintText()) {
                        fail("New constraint message not found, instead got: "
                                + fec.getModel().getQuestionPrompt().getConstraintText())
                    }
                } else if (response3 == FormEntryController.ANSWER_OK) {
                    fail("Should have constrained (2)")
                    break
                }
            }
            if (q.getTextID() == "constraint-test-3") {
                val response4 = fec.answerQuestion(IntegerData(13))
                if (response4 == FormEntryController.ANSWER_CONSTRAINT_VIOLATED) {
                    if ("The best QB of all time: Tom Brady" != fec.getModel().getQuestionPrompt().getConstraintText()) {
                        fail("New constraint message not found, instead got: "
                                + fec.getModel().getQuestionPrompt().getConstraintText())
                    }
                } else if (response4 == FormEntryController.ANSWER_OK) {
                    fail("Should have constrained (2)")
                    break
                }
            }
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)
    }

    /**
     * Test setvalue expressions which have predicate references
     */
    @Test
    fun testSetValuePredicate() {
        val fpi = FormParseInit("/test_setvalue_predicate.xml")
        val fec = initFormEntry(fpi)

        var testPassed = false
        do {
            if (fec.getModel().getEvent() != FormEntryController.EVENT_QUESTION) {
                continue
            }
            val text = fec.getModel().getQuestionPrompt().getQuestionText()
            if (text != null && text.contains("Test") && text.contains("pass")) {
                testPassed = true
            }
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)
        if (!testPassed) {
            fail("Setvalue Predicate Target Test")
        }
    }

    /**
     * Test nested form repeat triggers and actions
     */
    @Test
    @Throws(Exception::class)
    fun testNestedRepeatActions() {
        val fpi = FormParseInit("/xform_tests/test_looped_model_iteration.xml")
        val fec = initFormEntry(fpi)
        stepThroughEntireForm(fec)

        ExprEvalUtils.assertEqualsXpathEval("Nested repeats did not evaluate to the proper outcome",
                60.0,
                "/data/sum",
                fpi.getFormDef()!!.getEvaluationContext()!!)
    }

    /**
     * Test triggers fired from inserting a new repeat entry.
     */
    @Test
    @Throws(Exception::class)
    fun testRepeatInsertTriggering() {
        val fpi = FormParseInit("/xform_tests/test_repeat_insert_duplicate_triggering.xml")
        val fec = initFormEntry(fpi)
        stepThroughEntireForm(fec)

        val evalCtx = fpi.getFormDef()!!.getEvaluationContext()!!
        ExprEvalUtils.assertEqualsXpathEval("Check language set correctly",
                "en", "/data/iter/country[1]/language", evalCtx)
        ExprEvalUtils.assertEqualsXpathEval("Check id attr set correctly",
                "1", "/data/iter/country[2]/@id", evalCtx)
        ExprEvalUtils.assertEqualsXpathEval("Check id node set correctly",
                "1", "/data/iter/country[2]/id", evalCtx)
    }

    @Test
    @Throws(Exception::class)
    fun testQuestionLevelActionsAndSerialization() {
        val fpi = FormParseInit("/xform_tests/test_question_level_actions.xml")

        val fd = fpi.getFormDef()!!
        val sandbox = PersistableSandbox()
        val serialized = sandbox.serialize(fd)
        val deserializedFormDef = sandbox.deserialize(serialized, org.javarosa.core.model.FormDef::class.java)
        val fpiFromDeserialization = FormParseInit(deserializedFormDef)

        testQuestionLevelActions(fpi)
        testQuestionLevelActions(fpiFromDeserialization)
    }

    @Throws(Exception::class)
    fun testQuestionLevelActions(fpi: FormParseInit) {
        val fec = initFormEntry(fpi)
        val evalCtx = fpi.getFormDef()!!.getEvaluationContext()!!

        ExprEvalUtils.assertEqualsXpathEval(
                "Test that xforms-ready event triggered the form-level setvalue action",
                "default value", "/data/selection", evalCtx)

        val birthday = Calendar.getInstance()
        birthday.set(1993, Calendar.MARCH, 26)

        var questionIndex = 0
        do {
            val q = fpi.getCurrentQuestion() ?: continue

            when (questionIndex) {
                0 -> fec.answerQuestion(StringData("Answer to text question"))
                1 -> fec.answerQuestion(SelectOneData(Selection("one")))
                2 -> fec.answerQuestion(DateData(birthday.time))
            }

            questionIndex++
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)

        var evalResult = ExprEvalUtils.xpathEval(evalCtx, "/data/text/@time")
        assertTrue("Check that a timestamp was set for the text question",
                evalResult is Date)

        evalResult = ExprEvalUtils.xpathEval(evalCtx, "/data/selection/@time")
        assertTrue("Check that a timestamp was set for the selection question",
                evalResult is Date)

        evalResult = ExprEvalUtils.xpathEval(evalCtx, "/data/birthday/@time")
        assertTrue("Check that a timestamp was set for the date question",
                evalResult is Date)

        val currentInMillis = Calendar.getInstance().timeInMillis
        val birthdayInMillis = birthday.timeInMillis
        val diff = currentInMillis - birthdayInMillis
        val MILLISECONDS_IN_A_YEAR = (365.25 * 24 * 60 * 60 * 1000).toLong()
        val expectedAge = (diff / MILLISECONDS_IN_A_YEAR).toDouble()

        ExprEvalUtils.assertEqualsXpathEval("Check that a default value for the age question was " +
                "set correctly based upon provided answer to birthday question",
                expectedAge, "/data/age", evalCtx)
    }

    /**
     * Tests trigger caching related to cascading relevancy calculations to children.
     */
    @Test
    @Throws(Exception::class)
    fun testTriggerCaching() {
        val fpi = FormParseInit("/xform_tests/test_trigger_caching.xml")
        val fec = initFormEntry(fpi)
        stepThroughEntireForm(fec)

        val evalCtx = fpi.getFormDef()!!.getEvaluationContext()!!
        ExprEvalUtils.assertEqualsXpathEval("Check max animal weight",
                400.0, "/data/heaviest_animal_weight", evalCtx)
        ExprEvalUtils.assertEqualsXpathEval("Check min animal",
                100.0, "/data/lightest_animal_weight", evalCtx)
        ExprEvalUtils.assertEqualsXpathEval("Ensure we skip over setting attr of irrelevant entry",
                "", "/data/animals[/data/skip_weighing_nth_animal]/weight/@time", evalCtx)

        val weighTimeResult = ExprEvalUtils.xpathEval(evalCtx,
                "/data/animals[/data/skip_weighing_nth_animal - 1]/weight/@time")
        if ("" == weighTimeResult || "-1" == weighTimeResult) {
            fail("@time should be set for relevant animal weight.")
        }
        ExprEvalUtils.assertEqualsXpathEval("Assert genus skip value",
                1.0, "/data/skip_genus_nth_animal", evalCtx)
        ExprEvalUtils.assertEqualsXpathEval("Ensure genus at skip entry is irrelevant",
                "", "/data/animals[1]/genus/species", evalCtx)
        ExprEvalUtils.assertEqualsXpathEval("Ensure genuse at non-skip entry has default value",
                "default", "/data/animals[2]/genus/species", evalCtx)

        ExprEvalUtils.assertEqualsXpathEval(
                "Relevancy of skipped genus entry should be irrelevant to, due to the way it is calculated",
                "", "/data/disabled_species", evalCtx)
    }

    /**
     * Regressions around complex repeat behaviors
     */
    @Test
    @Throws(Exception::class)
    fun testLoopedRepeatIndexFetches() {
        val fpi = FormParseInit("/xform_tests/test_looped_form_index_fetch.xml")
        val fec = initFormEntry(fpi)

        fec.stepToNextEvent()
        fec.stepToNextEvent()

        fec.answerQuestion(IntegerData(2))
        while (fec.stepToNextEvent() != FormEntryController.EVENT_QUESTION);

        fec.answerQuestion(UncastData("yes"))
        while (fec.stepToNextEvent() != FormEntryController.EVENT_QUESTION);

        fec.getNextIndex(fec.getModel().getFormIndex(), true)
        fec.answerQuestion(IntegerData(2))
        fec.getNextIndex(fec.getModel().getFormIndex(), true)
    }

    @Test
    @Throws(XPathSyntaxException::class)
    fun testModelIterationLookahead() {
        val fpi = FormParseInit("/xform_tests/model_iteration_lookahead.xml")
        val fec = initFormEntry(fpi)
        stepThroughEntireForm(fec)

        val evalCtx = fpi.getFormDef()!!.getEvaluationContext()!!
        ExprEvalUtils.assertEqualsXpathEval("",
                "20", "/data/myiterator/iterator[1]/target_value", evalCtx)
        ExprEvalUtils.assertEqualsXpathEval("",
                100.0, "/data/myiterator/iterator[1]/relevancy_depending_on_future", evalCtx)
    }

    @Test
    @Throws(Exception::class)
    fun testSimilarBindConditionsAreDistinguished() {
        val fpi = FormParseInit("/xform_tests/test_display_conditions_regression.xml")
        val fec = initFormEntry(fpi)

        var visibleLabelWasPresent = false
        do {
            val q = fpi.getCurrentQuestion()
            if (q == null || q.getTextID() == null || "" == q.getTextID()) {
                continue
            }
            if (q.getTextID() == "visible-label") {
                visibleLabelWasPresent = true
            }
            if (q.getTextID() == "invisible-label") {
                fail("Label whose display condition should be false was showing")
            }
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)

        if (!visibleLabelWasPresent) {
            fail("Label whose display condition should be true was not showing")
        }
    }

    @Test
    @Throws(IOException::class, XPathSyntaxException::class)
    fun testDeleteRepeatMultiplicities() {
        val fpi = FormParseInit("/multiple_repeats.xml")
        val fec = initFormEntry(fpi, "en")
        fec.stepToNextEvent()
        fec.newRepeat()
        fec.stepToNextEvent()
        fec.answerQuestion(StringData("First repeat, first iteration: question2"))
        fec.stepToNextEvent()
        fec.answerQuestion(StringData("First repeat, first iteration: question3"))
        fec.stepToNextEvent()
        fec.newRepeat()
        fec.stepToNextEvent()
        fec.answerQuestion(StringData("First repeat, second iteration: question2"))
        fec.stepToNextEvent()
        fec.answerQuestion(StringData("First repeat, second iteration: question3"))
        fec.stepToNextEvent()
        fec.stepToNextEvent()
        fec.newRepeat()
        fec.stepToNextEvent()
        fec.answerQuestion(StringData("Second repeat, first iteration: question5"))
        fec.stepToNextEvent()
        fec.answerQuestion(StringData("Second repeat, first iteration: question6"))
        fec.stepToNextEvent()
        fec.newRepeat()
        fec.stepToNextEvent()
        fec.answerQuestion(StringData("Second repeat, second iteration: question5"))
        fec.stepToNextEvent()
        fec.answerQuestion(StringData("Second repeat, second iteration: question6"))
        fec.stepToPreviousEvent()
        fec.stepToPreviousEvent()

        val root = fpi.getFormDef()!!.getInstance()!!.getRoot()

        assertEquals(root.getChildMultiplicity("question4"), 2)
        assertNotEquals(root.getChild("question4", 1), null)
        assertEquals(root.getChildMultiplicity("question1"), 2)
        assertNotEquals(root.getChild("question1", 1), null)

        val evalCtx = fpi.getFormDef()!!.getEvaluationContext()!!
        ExprEvalUtils.assertEqualsXpathEval("check repeat node set correctly",
                "Second repeat, first iteration: question5", "/data/question4[1]/question5", evalCtx)
        ExprEvalUtils.assertEqualsXpathEval("check repeat node set correctly",
                "Second repeat, first iteration: question6", "/data/question4[1]/question6", evalCtx)
        ExprEvalUtils.assertEqualsXpathEval("check repeat node set correctly",
                "Second repeat, second iteration: question5", "/data/question4[2]/question5", evalCtx)
        ExprEvalUtils.assertEqualsXpathEval("check repeat node set correctly",
                "Second repeat, second iteration: question6", "/data/question4[2]/question6", evalCtx)

        fec.deleteRepeat(0)

        assertEquals(root.getChildMultiplicity("question4"), 1)
        assertEquals(root.getChild("question4", 1), null)
        assertEquals(root.getChildMultiplicity("question1"), 2)
        assertNotEquals(root.getChild("question1", 1), null)

        ExprEvalUtils.assertEqualsXpathEval("check repeat node set correctly",
                "Second repeat, second iteration: question5", "/data/question4[1]/question5", evalCtx)
        ExprEvalUtils.assertEqualsXpathEval("check repeat node set correctly",
                "Second repeat, second iteration: question6", "/data/question4[1]/question6", evalCtx)
    }

    @Test
    @Throws(XPathSyntaxException::class)
    fun testITextXPathFunction() {
        val fpi = FormParseInit("/xform_tests/itext_function.xml")
        val fec = initFormEntry(fpi, "new")

        var inlinePassed = false
        var nestedPassed = false

        do {
            val currentRef = fec.getModel().getFormIndex().getReference() ?: continue

            if (currentRef.genericize().toString() == "/data/inline") {
                assertEquals("Inline IText Method Callout", "right",
                        fec.getModel().getCaptionPrompt().getQuestionText())
                inlinePassed = true
            }

            if (currentRef.genericize().toString() == "/data/nested") {
                assertEquals("Nexted IText Method Callout", "right",
                        fec.getModel().getCaptionPrompt().getQuestionText())
                nestedPassed = true
            }
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)

        if (!inlinePassed) {
            Assert.fail("Inline itext callout did not occur")
        }
        if (!nestedPassed) {
            Assert.fail("Nested itext callout did not occur")
        }

        val evalCtx = fpi.getFormDef()!!.getEvaluationContext()!!
        ExprEvalUtils.assertEqualsXpathEval("IText calculation contained the wrong value",
                "right", "/data/calculation", evalCtx)
    }

    @Test
    @Throws(XPathSyntaxException::class)
    fun testGroupRelevancyInsideRepeat() {
        val fpi = FormParseInit("/xform_tests/group_relevancy_in_repeat.xml")
        val fec = initFormEntry(fpi)

        do {
            val q = fpi.getCurrentQuestion()

            if (q != null && q.getControlType() == Constants.CONTROL_SELECT_ONE) {
                val ans = SelectOneData(Selection("yes"))
                fec.answerQuestion(ans)
                ExprEvalUtils.testEval("/data/some_group/repeat_sum",
                        fpi.getFormDef()!!.getInstance(), null, 25.0)
            }
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)
    }

    /**
     * Test that the untrue portion of an if() statement won't evaluate
     */
    @Test
    fun testFormShortCircuit() {
        val fpi = FormParseInit("/IfShortCircuitTest.xhtml")

        val functionFailer = object : IFunctionHandler {
            override fun getName(): String = "fail_function"

            override fun eval(args: Array<Any?>?, ec: EvaluationContext?): Any {
                throw RuntimeException("False portion of if() statement called")
            }

            override fun getPrototypes(): ArrayList<*> {
                val p = ArrayList<Array<Class<*>>>()
                p.add(arrayOf())
                return p
            }

            override fun rawArgs(): Boolean = false
        }

        fpi.getFormDef()!!.exprEvalContext!!.addFunctionHandler(functionFailer)

        val fec = fpi.getFormEntryController()

        do {
            val q = fpi.getCurrentQuestion() ?: continue
            fec.answerQuestion(SelectOneData(Selection("yes")))
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)
    }

    @Test
    fun testItemsetPopulationAndFilter() {
        val fpi = FormParseInit("/xform_tests/itemset_population_test.xhtml")

        val fec = fpi.getFormEntryController()

        do {
            val q = fpi.getCurrentQuestion() ?: continue
            val currentRef = fec.getModel().getFormIndex().getReference() ?: continue

            if (currentRef.genericize().toString() == "/data/filter") {
                fec.answerQuestion(SelectOneData(Selection("a")))
            }

            if (currentRef.genericize().toString() == "/data/question") {
                assertEquals("Itemset Filter returned the wrong size",
                        fec.getModel().getQuestionPrompt().getSelectChoices()!!.size,
                        3)
            }
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)
    }

    @Test
    @Throws(Exception::class)
    fun testRepeatCaptions() {
        val fpi = FormParseInit("/xform_tests/sweet_repeat_demo.xml")
        val fec = initFormEntry(fpi)
        val formDef = fec.getModel().getForm()
        val repeat = formDef.getChild(2) as GroupDef
        assertEquals("main-header-label", repeat.mainHeader)
        assertEquals("add-caption-label", repeat.addCaption)
        assertEquals("add-empty-caption-label", repeat.addEmptyCaption)
        assertEquals("del-caption-label", repeat.delCaption)
        assertEquals("done-caption-label", repeat.doneCaption)
        assertEquals("done-empty-caption-label", repeat.doneEmptyCaption)
        assertEquals("choose-caption-label", repeat.chooseCaption)
        assertEquals("entry-header-label", repeat.entryHeader)
    }

    companion object {
        private fun stepThroughEntireForm(fec: FormEntryController) {
            do {
            } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)
        }

        @JvmStatic
        fun initFormEntry(fpi: FormParseInit): FormEntryController {
            return initFormEntry(fpi, null)
        }

        private fun initFormEntry(fpi: FormParseInit, locale: String?): FormEntryController {
            val fec = fpi.getFormEntryController()
            fpi.getFormDef()!!.initialize(true, null, locale, false)
            fec.jumpToIndex(FormIndex.createBeginningOfFormIndex())
            return fec
        }
    }
}
