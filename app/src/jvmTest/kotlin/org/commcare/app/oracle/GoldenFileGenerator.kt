package org.commcare.app.oracle

import org.commcare.app.engine.FormSerializer
import org.javarosa.core.model.FormDef
import org.javarosa.core.model.data.DecimalData
import org.javarosa.core.model.data.GeoPointData
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.model.data.SelectMultiData
import org.javarosa.core.model.data.SelectOneData
import org.javarosa.core.model.data.StringData
import org.javarosa.core.model.data.helper.Selection
import org.javarosa.form.api.FormEntryController
import org.javarosa.form.api.FormEntryModel
import org.javarosa.model.xform.XFormSerializingVisitor
import org.javarosa.xform.util.XFormUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStream

/**
 * JVM-only golden file generator. Loads test forms, fills them with predetermined
 * answers, serializes via the oracle (XFormSerializingVisitor), and writes the
 * normalized output as golden files that cross-platform tests compare against.
 *
 * Run with: ./gradlew :app:jvmTest --tests "*.GoldenFileGenerator"
 *
 * Golden files go to: commcare-core/src/commonTest/resources/golden/
 */
class GoldenFileGenerator {

    private val goldenDir = File("../commcare-core/src/commonTest/resources/golden")

    /**
     * Each scenario defines a form, answers, and output filename.
     */
    data class GoldenScenario(
        val formResource: String,
        val outputName: String,
        val answers: List<IAnswerData?>,
        val useCustomNavigation: Boolean = false,
        val customNavigator: ((FormEntryModel, FormEntryController) -> Unit)? = null
    )

    private val scenarios = listOf(
        // Empty forms (no answers)
        GoldenScenario(
            formResource = "/test_all_question_types.xml",
            outputName = "all_question_types_empty",
            answers = listOf(null, null, null, null, null, null, null)
        ),

        // All question types with values
        GoldenScenario(
            formResource = "/test_all_question_types.xml",
            outputName = "all_question_types_text_only",
            answers = listOf(
                StringData("John Doe"),   // name
                IntegerData(30),          // age
                DecimalData(72.5),        // height
                null,                     // date - skip
                null,                     // time - skip
                SelectOneData(Selection("male")), // gender
                null                      // hobbies - skip
            )
        ),

        // Calculations
        GoldenScenario(
            formResource = "/test_calculations.xml",
            outputName = "calculations_basic",
            answers = listOf(
                IntegerData(5),      // quantity
                DecimalData(10.0),   // price
                DecimalData(5.0),    // discount
                StringData("World")  // name
            )
        ),

        // GeoPoint
        GoldenScenario(
            formResource = "/test_geopoint.xml",
            outputName = "geopoint_basic",
            answers = listOf(
                StringData("Office"),
                GeoPointData(doubleArrayOf(42.3601, -71.0589, 10.0, 5.0)),
                StringData("Main office location")
            )
        ),

        // Multi-select
        GoldenScenario(
            formResource = "/test_multi_select.xml",
            outputName = "multi_select_basic",
            answers = listOf(
                SelectOneData(Selection("blue")),
                SelectMultiData(arrayListOf(Selection("fever"), Selection("cough"))),
                SelectOneData(Selection("moderate"))
            )
        ),

        // Multi-select empty
        GoldenScenario(
            formResource = "/test_multi_select.xml",
            outputName = "multi_select_empty",
            answers = listOf(null, null, null)
        ),

        // Repeat with skip (no repeats added)
        GoldenScenario(
            formResource = "/test_repeat_and_relevancy.xml",
            outputName = "repeat_skip",
            answers = emptyList(),
            useCustomNavigation = true,
            customNavigator = { model, controller ->
                while (model.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
                    when (model.getEvent()) {
                        FormEntryController.EVENT_PROMPT_NEW_REPEAT -> {
                            // Skip adding repeat
                            controller.stepToNextEvent()
                        }
                        else -> controller.stepToNextEvent()
                    }
                }
            }
        ),

        // Constraints form with valid values
        GoldenScenario(
            formResource = "/test_field_list_constraints.xml",
            outputName = "constraints_valid",
            answers = emptyList(),
            useCustomNavigation = true,
            customNavigator = { model, controller ->
                while (model.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
                    if (model.getEvent() == FormEntryController.EVENT_GROUP) {
                        try {
                            val prompts = controller.getQuestionPrompts()
                            if (prompts.isNotEmpty()) {
                                for ((i, prompt) in prompts.withIndex()) {
                                    val idx = prompt.getIndex()!!
                                    when (i) {
                                        0 -> controller.answerQuestion(idx, IntegerData(25))
                                        1 -> controller.answerQuestion(idx, StringData("Jane"))
                                        2 -> controller.answerQuestion(idx, StringData("j@e.co"))
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            // Not a field-list group
                        }
                    }
                    controller.stepToNextEvent()
                }
            }
        )
    )

    private fun loadForm(formResource: String): FormDef? {
        val stream: InputStream = this::class.java.getResourceAsStream(formResource)
            ?: return null
        return XFormUtils.getFormFromInputStream(stream)
    }

    private fun fillForm(
        scenario: GoldenScenario
    ): Pair<FormDef, FormEntryController>? {
        val formDef = loadForm(scenario.formResource) ?: return null
        formDef.initialize(true, null)

        val model = if (scenario.useCustomNavigation) {
            FormEntryModel(formDef, FormEntryModel.REPEAT_STRUCTURE_LINEAR)
        } else {
            FormEntryModel(formDef)
        }
        val controller = FormEntryController(model)

        controller.stepToNextEvent()

        if (scenario.useCustomNavigation && scenario.customNavigator != null) {
            scenario.customNavigator.invoke(model, controller)
        } else {
            var answerIndex = 0
            while (model.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
                if (model.getEvent() == FormEntryController.EVENT_QUESTION) {
                    if (answerIndex < scenario.answers.size && scenario.answers[answerIndex] != null) {
                        controller.answerQuestion(scenario.answers[answerIndex])
                    }
                    answerIndex++
                }
                controller.stepToNextEvent()
            }
        }

        return formDef to controller
    }

    /**
     * Normalize XML for deterministic golden file output.
     * Strips XML declaration and normalizes whitespace, but preserves
     * namespace attributes since they're part of the form's identity.
     */
    private fun normalizeForGolden(xml: String): String {
        return xml.trim()
            .replace(Regex("""<\?xml[^?]*\?>"""), "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .trim()
    }

    @Test
    fun generateGoldenFiles() {
        goldenDir.mkdirs()

        var generated = 0
        for (scenario in scenarios) {
            val result = fillForm(scenario)
            assertNotNull("Failed to load form: ${scenario.formResource}", result)

            val (formDef, _) = result!!

            // Generate oracle output
            val visitor = XFormSerializingVisitor()
            val oracleBytes = visitor.serializeInstance(formDef.getInstance()!!)
            val oracleXml = normalizeForGolden(String(oracleBytes, Charsets.UTF_8))

            // Also generate our cross-platform output for verification
            val ourXml = normalizeForGolden(FormSerializer.serializeForm(formDef))

            // Write golden file (oracle output is the reference)
            val goldenFile = File(goldenDir, "${scenario.outputName}.expected.xml")
            goldenFile.writeText(oracleXml + "\n")

            println("Generated: ${goldenFile.name} (${oracleXml.length} chars)")
            generated++
        }

        assertTrue("Should generate at least 7 golden files", generated >= 7)
        println("\nGenerated $generated golden files in ${goldenDir.absolutePath}")
    }

    @Test
    fun verifyGoldenFilesDeterministic() {
        // Run generation twice and verify identical output
        val firstRun = mutableMapOf<String, String>()
        val secondRun = mutableMapOf<String, String>()

        for (scenario in scenarios) {
            val result1 = fillForm(scenario) ?: continue
            val result2 = fillForm(scenario) ?: continue

            val visitor1 = XFormSerializingVisitor()
            val xml1 = normalizeForGolden(
                String(visitor1.serializeInstance(result1.first.getInstance()!!), Charsets.UTF_8)
            )
            firstRun[scenario.outputName] = xml1

            val visitor2 = XFormSerializingVisitor()
            val xml2 = normalizeForGolden(
                String(visitor2.serializeInstance(result2.first.getInstance()!!), Charsets.UTF_8)
            )
            secondRun[scenario.outputName] = xml2
        }

        for ((name, xml1) in firstRun) {
            val xml2 = secondRun[name]!!
            assertEquals("Golden file '$name' is not deterministic", xml1, xml2)
        }

        println("All ${firstRun.size} golden files are deterministic")
    }
}
