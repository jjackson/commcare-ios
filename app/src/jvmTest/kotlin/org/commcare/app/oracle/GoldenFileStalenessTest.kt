package org.commcare.app.oracle

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
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.io.InputStream

/**
 * Detects golden file staleness by regenerating oracle output in-memory
 * and comparing against checked-in golden files. Fails if any golden file
 * is out of date, with a diff showing what changed.
 *
 * This catches cases where engine changes alter serialization output.
 * To fix: re-run GoldenFileGenerator, verify output, commit updated golden files.
 */
class GoldenFileStalenessTest {

    data class Scenario(
        val formResource: String,
        val goldenName: String,
        val answers: List<IAnswerData?>,
        val useCustomNavigation: Boolean = false,
        val customNavigator: ((FormEntryModel, FormEntryController) -> Unit)? = null
    )

    private val scenarios = listOf(
        Scenario("/test_all_question_types.xml", "all_question_types_empty",
            listOf(null, null, null, null, null, null, null)),

        Scenario("/test_all_question_types.xml", "all_question_types_text_only",
            listOf(
                StringData("John Doe"), IntegerData(30), DecimalData(72.5),
                null, null, SelectOneData(Selection("male")), null
            )),

        Scenario("/test_calculations.xml", "calculations_basic",
            listOf(IntegerData(5), DecimalData(10.0), DecimalData(5.0), StringData("World"))),

        Scenario("/test_geopoint.xml", "geopoint_basic",
            listOf(StringData("Office"), GeoPointData(doubleArrayOf(42.3601, -71.0589, 10.0, 5.0)),
                StringData("Main office location"))),

        Scenario("/test_multi_select.xml", "multi_select_basic",
            listOf(
                SelectOneData(Selection("blue")),
                SelectMultiData(arrayListOf(Selection("fever"), Selection("cough"))),
                SelectOneData(Selection("moderate"))
            )),

        Scenario("/test_multi_select.xml", "multi_select_empty",
            listOf(null, null, null)),

        Scenario("/test_repeat_and_relevancy.xml", "repeat_skip",
            emptyList(), useCustomNavigation = true,
            customNavigator = { model, controller ->
                while (model.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
                    controller.stepToNextEvent()
                }
            }),

        Scenario("/test_field_list_constraints.xml", "constraints_valid",
            emptyList(), useCustomNavigation = true,
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
                        } catch (_: Exception) {}
                    }
                    controller.stepToNextEvent()
                }
            })
    )

    private fun loadForm(resource: String): FormDef? {
        val stream: InputStream = this::class.java.getResourceAsStream(resource) ?: return null
        return XFormUtils.getFormFromInputStream(stream)
    }

    private fun generateOracleOutput(scenario: Scenario): String? {
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
            var i = 0
            while (model.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
                if (model.getEvent() == FormEntryController.EVENT_QUESTION) {
                    if (i < scenario.answers.size && scenario.answers[i] != null) {
                        controller.answerQuestion(scenario.answers[i])
                    }
                    i++
                }
                controller.stepToNextEvent()
            }
        }

        val visitor = XFormSerializingVisitor()
        val bytes = visitor.serializeInstance(formDef.getInstance()!!)
        return normalizeForGolden(String(bytes, Charsets.UTF_8))
    }

    private fun normalizeForGolden(xml: String): String {
        return xml.trim()
            .replace(Regex("""<\?xml[^?]*\?>"""), "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .trim()
    }

    private val goldenDir = File("../commcare-core/src/commonTest/resources/golden")

    @Test
    fun testGoldenFilesAreUpToDate() {
        val failures = mutableListOf<String>()

        for (scenario in scenarios) {
            val oracleXml = generateOracleOutput(scenario)
            assertNotNull("Failed to generate oracle output for ${scenario.goldenName}", oracleXml)

            val goldenFile = File(goldenDir, "${scenario.goldenName}.expected.xml")

            if (!goldenFile.exists()) {
                failures.add("MISSING: ${goldenFile.name} — run GoldenFileGenerator to create it")
                continue
            }

            val checkedIn = goldenFile.readText().trim()

            if (oracleXml != checkedIn) {
                failures.add(buildString {
                    appendLine("STALE: ${scenario.goldenName}.expected.xml")
                    appendLine("  Expected (oracle): ${oracleXml!!.take(120)}...")
                    appendLine("  Checked-in:        ${checkedIn.take(120)}...")
                })
            }
        }

        if (failures.isNotEmpty()) {
            val msg = buildString {
                appendLine("${failures.size} golden file(s) are stale or missing!")
                appendLine("Run: ./gradlew :app:jvmTest --tests \"*.GoldenFileGenerator.generateGoldenFiles\"")
                appendLine()
                for (f in failures) appendLine(f)
            }
            org.junit.Assert.fail(msg)
        }
    }
}
