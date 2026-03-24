package org.commcare.app.e2e

import org.commcare.app.engine.FormSerializer
import org.javarosa.core.model.data.DecimalData
import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.model.data.SelectMultiData
import org.javarosa.core.model.data.SelectOneData
import org.javarosa.core.model.data.StringData
import org.javarosa.core.model.data.helper.Selection
import org.javarosa.form.api.FormEntryController
import org.javarosa.form.api.FormEntryModel
import org.javarosa.xform.util.XFormUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Golden test that loads a real XForm (test_all_question_types.xml), walks
 * through questions, answers each one, serializes the form, and verifies the
 * output matches expected values.
 *
 * Follows the pattern from FormSerializationCrossPlatformTest but runs at the
 * app layer using XFormUtils (JVM) and FormSerializer (cross-platform).
 * This validates that the full form entry pipeline -- load, navigate, answer,
 * serialize -- produces correct XML for all supported question types.
 */
class FormEntryGoldenTest {

    private fun loadFormFromResource(path: String): org.javarosa.core.model.FormDef {
        val stream = this::class.java.getResourceAsStream(path)
            ?: throw AssertionError("Could not load form resource: $path")
        return XFormUtils.getFormFromInputStream(stream)
    }

    private fun fillAndSerialize(
        resourcePath: String,
        answers: List<org.javarosa.core.model.data.IAnswerData?>
    ): String {
        val formDef = loadFormFromResource(resourcePath)
        formDef.initialize(true, null)

        val model = FormEntryModel(formDef)
        val controller = FormEntryController(model)

        controller.stepToNextEvent()
        var answerIndex = 0

        while (model.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
            if (model.getEvent() == FormEntryController.EVENT_QUESTION) {
                if (answerIndex < answers.size && answers[answerIndex] != null) {
                    val result = controller.answerQuestion(answers[answerIndex])
                    assertEquals(
                        FormEntryController.ANSWER_OK, result,
                        "Answer rejected at question $answerIndex"
                    )
                }
                answerIndex++
            }
            controller.stepToNextEvent()
        }

        return FormSerializer.serializeForm(formDef)
    }

    // --- Empty form serialization ---

    @Test
    fun testEmptyFormProducesValidXml() {
        val xml = fillAndSerialize(
            "/test_all_question_types.xml",
            listOf(null, null, null, null, null, null, null, null)
        )
        assertTrue(xml.contains("<data"), "Empty form should have a data element")
        assertTrue(xml.contains("<text_answer"), "Should contain text_answer element")
        assertTrue(xml.contains("<integer_answer"), "Should contain integer_answer element")
        assertTrue(xml.contains("<select_one_answer"), "Should contain select_one_answer element")
    }

    // --- Text answer ---

    @Test
    fun testTextAnswerSerialized() {
        val xml = fillAndSerialize(
            "/test_all_question_types.xml",
            listOf(StringData("Hello World"), null, null, null, null, null, null, null)
        )
        assertTrue(
            xml.contains("<text_answer>Hello World</text_answer>"),
            "Text answer should serialize correctly"
        )
    }

    // --- Integer answer ---

    @Test
    fun testIntegerAnswerSerialized() {
        val xml = fillAndSerialize(
            "/test_all_question_types.xml",
            listOf(null, IntegerData(42), null, null, null, null, null, null)
        )
        assertTrue(
            xml.contains("<integer_answer>42</integer_answer>"),
            "Integer answer should serialize correctly"
        )
    }

    // --- Decimal answer ---

    @Test
    fun testDecimalAnswerSerialized() {
        val xml = fillAndSerialize(
            "/test_all_question_types.xml",
            listOf(null, null, DecimalData(3.14), null, null, null, null, null)
        )
        assertTrue(
            xml.contains("<decimal_answer>3.14</decimal_answer>"),
            "Decimal answer should serialize correctly"
        )
    }

    // --- Select one ---

    @Test
    fun testSelectOneAnswerSerialized() {
        val xml = fillAndSerialize(
            "/test_all_question_types.xml",
            listOf(null, null, null, null, null, SelectOneData(Selection("b")), null, null)
        )
        assertTrue(
            xml.contains("<select_one_answer>b</select_one_answer>"),
            "Select one answer should serialize correctly"
        )
    }

    // --- Select multi ---

    @Test
    fun testSelectMultiAnswerSerialized() {
        val selections = arrayListOf(Selection("a"), Selection("c"))
        val xml = fillAndSerialize(
            "/test_all_question_types.xml",
            listOf(null, null, null, null, null, null, SelectMultiData(selections), null)
        )
        assertTrue(
            xml.contains("<select_multi_answer>a c</select_multi_answer>"),
            "Select multi answer should serialize as space-separated values"
        )
    }

    // --- All answers together ---

    @Test
    fun testAllAnswersTogetherProducesCompleteXml() {
        val selections = arrayListOf(Selection("a"), Selection("b"))
        val xml = fillAndSerialize(
            "/test_all_question_types.xml",
            listOf(
                StringData("Jane Doe"),
                IntegerData(28),
                DecimalData(65.5),
                null, // date - skip
                null, // time - skip
                SelectOneData(Selection("c")),
                SelectMultiData(selections),
                StringData("OK") // trigger
            )
        )

        assertTrue(xml.contains("<text_answer>Jane Doe</text_answer>"), "Text answer present")
        assertTrue(xml.contains("<integer_answer>28</integer_answer>"), "Integer answer present")
        assertTrue(xml.contains("<decimal_answer>65.5</decimal_answer>"), "Decimal answer present")
        assertTrue(xml.contains("<select_one_answer>c</select_one_answer>"), "Select one present")
        assertTrue(xml.contains("<select_multi_answer>a b</select_multi_answer>"), "Multi present")
        assertTrue(xml.contains("<trigger_answer>OK</trigger_answer>"), "Trigger present")
    }

    // --- Serialization idempotency ---

    @Test
    fun testSerializationIsIdempotent() {
        val formDef = loadFormFromResource("/test_all_question_types.xml")
        formDef.initialize(true, null)

        val model = FormEntryModel(formDef)
        val controller = FormEntryController(model)

        controller.stepToNextEvent()
        while (model.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
            if (model.getEvent() == FormEntryController.EVENT_QUESTION) {
                controller.answerQuestion(StringData("test"))
            }
            controller.stepToNextEvent()
        }

        val xml1 = FormSerializer.serializeForm(formDef)
        val xml2 = FormSerializer.serializeForm(formDef)
        assertEquals(xml1, xml2, "Serialization should be idempotent")
    }

    // --- Calculations form ---

    @Test
    fun testCalculationsFormProducesCorrectResults() {
        val xml = fillAndSerialize(
            "/test_calculations.xml",
            listOf(
                IntegerData(5),
                DecimalData(10.0),
                DecimalData(5.0),
                StringData("World")
            )
        )

        assertTrue(xml.contains("<total>50</total>"), "Calculated total should be 5*10=50")
        assertTrue(
            xml.contains("<final_price>45</final_price>"),
            "Calculated final_price should be 50-5=45"
        )
        assertTrue(
            xml.contains("<greeting>Hello World</greeting>"),
            "Calculated greeting should be 'Hello World'"
        )
    }

    // --- Cross-platform serializer comparison ---

    @Test
    fun testCrossPlatformSerializerMatchesJvmOracle() {
        val formDef = loadFormFromResource("/test_all_question_types.xml")
        formDef.initialize(true, null)

        val model = FormEntryModel(formDef)
        val controller = FormEntryController(model)

        // Fill with answers
        val answers = listOf(
            StringData("test text"),
            IntegerData(99),
            DecimalData(2.718),
            null, null, // skip date and time
            SelectOneData(Selection("a")),
            null, null
        )

        controller.stepToNextEvent()
        var answerIndex = 0
        while (model.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
            if (model.getEvent() == FormEntryController.EVENT_QUESTION) {
                if (answerIndex < answers.size && answers[answerIndex] != null) {
                    controller.answerQuestion(answers[answerIndex])
                }
                answerIndex++
            }
            controller.stepToNextEvent()
        }

        // Our cross-platform serializer
        val ourXml = FormSerializer.serializeForm(formDef)

        // JVM-only oracle serializer
        val visitor = org.javarosa.model.xform.XFormSerializingVisitor()
        val oracleBytes = visitor.serializeInstance(formDef.getInstance()!!)
        val oracleXml = String(oracleBytes, Charsets.UTF_8)

        // Both should contain the same answer data
        assertTrue(ourXml.contains("test text"), "Our serializer should contain text answer")
        assertTrue(oracleXml.contains("test text"), "Oracle should contain text answer")
        assertTrue(ourXml.contains("99"), "Our serializer should contain integer answer")
        assertTrue(oracleXml.contains("99"), "Oracle should contain integer answer")

        // Normalize and compare structural content
        val ourNorm = normalizeXml(ourXml)
        val oracleNorm = normalizeXml(oracleXml)
        assertEquals(oracleNorm, ourNorm, "Cross-platform and JVM serializers should match")
    }

    private fun normalizeXml(xml: String): String {
        return xml.trim()
            .replace(Regex("""<\?xml[^?]*\?>"""), "")
            .replace(Regex("""\s+xmlns(:\w+)?="[^"]*""""), "")
            .replace(Regex("""\s+(uiVersion|version|name)="[^"]*""""), "")
            .replace(Regex("""&#(\d+);""")) { Char(it.groupValues[1].toInt()).toString() }
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .trim()
    }
}
