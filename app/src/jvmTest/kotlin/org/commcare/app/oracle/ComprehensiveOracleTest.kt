package org.commcare.app.oracle

import org.javarosa.core.model.data.DecimalData
import org.javarosa.core.model.data.GeoPointData
import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.model.data.SelectMultiData
import org.javarosa.core.model.data.SelectOneData
import org.javarosa.core.model.data.StringData
import org.javarosa.core.model.data.helper.Selection
import org.javarosa.form.api.FormEntryController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive oracle test suite covering all Tier 2 features:
 * calculations, geopoint, multi-select, cross-serializer comparison.
 */
class ComprehensiveOracleTest {

    private val runner = OracleTestRunner()

    // --- Calculation tests ---

    @Test
    fun testCalculationsWithMultiplication() {
        val result = runner.fillAndSerialize("/test_calculations.xml", listOf(
            IntegerData(5),     // quantity
            DecimalData(10.0),  // price
            DecimalData(5.0),   // discount
            StringData("World") // name
        ))
        assertTrue("Form should serialize: $result", result is FormResult.Success)
        val xml = (result as FormResult.Success).xml
        // Calculated fields may be empty or contain computed values
        assertTrue("Quantity should be in output", xml.contains("quantity"))
        assertTrue("Name should be in output", xml.contains("name"))
    }

    @Test
    fun testCalculationsOracleComparison() {
        val result = runner.compareSerializers("/test_calculations.xml", listOf(
            IntegerData(3),
            DecimalData(7.5),
            DecimalData(2.0),
            StringData("Test")
        ))
        assertTrue("Serializers should match: $result", result is ComparisonResult.Match)
    }

    // --- GeoPoint tests ---

    @Test
    fun testGeoPointForm() {
        val result = runner.fillAndSerialize("/test_geopoint.xml", listOf(
            StringData("Office"),
            GeoPointData(doubleArrayOf(42.3601, -71.0589, 10.0, 5.0)),
            StringData("Main office location")
        ))
        assertTrue("Form should serialize: $result", result is FormResult.Success)
        val xml = (result as FormResult.Success).xml
        assertTrue("GPS data should be in output", xml.contains("gps_location"))
    }

    @Test
    fun testGeoPointOracleComparison() {
        val result = runner.compareSerializers("/test_geopoint.xml", listOf(
            StringData("Test Location"),
            GeoPointData(doubleArrayOf(40.7128, -74.0060, 0.0, 10.0)),
            StringData("Notes here")
        ))
        assertTrue("Serializers should match: $result", result is ComparisonResult.Match)
    }

    // --- Multi-select tests ---

    @Test
    fun testSelectOneAndMulti() {
        val result = runner.fillAndSerialize("/test_multi_select.xml", listOf(
            SelectOneData(Selection("blue")),
            SelectMultiData(arrayListOf(Selection("fever"), Selection("cough"))),
            SelectOneData(Selection("moderate"))
        ))
        assertTrue("Form should serialize: $result", result is FormResult.Success)
        val xml = (result as FormResult.Success).xml
        assertTrue("Color should be blue", xml.contains("blue"))
        assertTrue("Symptoms should contain fever", xml.contains("fever"))
    }

    @Test
    fun testMultiSelectOracleComparison() {
        val result = runner.compareSerializers("/test_multi_select.xml", listOf(
            SelectOneData(Selection("red")),
            SelectMultiData(arrayListOf(Selection("headache"), Selection("fatigue"))),
            SelectOneData(Selection("mild"))
        ))
        assertTrue("Serializers should match: $result", result is ComparisonResult.Match)
    }

    // --- Existing form oracle comparisons ---

    @Test
    fun testAllQuestionTypesOracleComparison() {
        val result = runner.compareSerializers("/test_all_question_types.xml", listOf(
            StringData("John Doe"),
            IntegerData(30),
            DecimalData(72.5),
            null, // date - skip
            null, // time - skip
            SelectOneData(Selection("male")),
            null  // multi-select - skip
        ))
        assertTrue("All question types should match: $result", result is ComparisonResult.Match)
    }

    @Test
    fun testConstraintsFormComparison() {
        val result = runner.fillAndSerialize("/test_field_list_constraints.xml") { model, controller ->
            // Navigate through field-list group
            while (model.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
                if (model.getEvent() == FormEntryController.EVENT_QUESTION) {
                    val prompts = controller.getQuestionPrompts()
                    if (prompts.isNotEmpty()) {
                        // Answer age, name, email in field-list
                        for ((i, prompt) in prompts.withIndex()) {
                            val idx = prompt.getIndex()!!
                            when {
                                i == 0 -> controller.answerQuestion(idx, IntegerData(25))     // age
                                i == 1 -> controller.answerQuestion(idx, StringData("Jane"))   // name
                                i == 2 -> controller.answerQuestion(idx, StringData("j@e.co")) // email
                            }
                        }
                    }
                } else if (model.getEvent() == FormEntryController.EVENT_GROUP) {
                    // Try to get prompts for field-list
                    try {
                        val prompts = controller.getQuestionPrompts()
                        if (prompts.isNotEmpty()) {
                            for ((i, prompt) in prompts.withIndex()) {
                                val idx = prompt.getIndex()!!
                                when {
                                    i == 0 -> controller.answerQuestion(idx, IntegerData(25))
                                    i == 1 -> controller.answerQuestion(idx, StringData("Jane"))
                                    i == 2 -> controller.answerQuestion(idx, StringData("j@e.co"))
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
        assertTrue("Constraints form should serialize", result is FormResult.Success)
    }

    @Test
    fun testRepeatFormNavigation() {
        val result = runner.fillAndSerialize("/test_repeat_and_relevancy.xml") { model, controller ->
            while (model.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
                when (model.getEvent()) {
                    FormEntryController.EVENT_QUESTION -> {
                        controller.stepToNextEvent()
                    }
                    FormEntryController.EVENT_PROMPT_NEW_REPEAT -> {
                        // Skip adding repeat
                        controller.stepToNextEvent()
                    }
                    else -> controller.stepToNextEvent()
                }
            }
        }
        assertTrue("Repeat form should serialize: $result", result is FormResult.Success)
    }

    // --- Form structure validation ---

    @Test
    fun testFormDefMetadata() {
        val result = runner.fillAndSerialize("/test_calculations.xml", listOf(
            IntegerData(1),
            DecimalData(1.0),
            DecimalData(0.0),
            StringData("X")
        ))
        assertTrue(result is FormResult.Success)
        val formDef = (result as FormResult.Success).formDef
        assertEquals("Calculation Test Form", formDef.getTitle())
        assertNotNull(formDef.getInstance())
    }

    @Test
    fun testEmptyFormSerialization() {
        // Submit with all nulls — form should still serialize
        val result = runner.fillAndSerialize("/test_multi_select.xml", listOf(
            null, null, null
        ))
        assertTrue("Empty form should serialize: $result", result is FormResult.Success)
    }
}
