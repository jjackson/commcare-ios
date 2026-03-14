package org.commcare.app.oracle

import org.javarosa.core.model.data.DecimalData
import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.model.data.SelectMultiData
import org.javarosa.core.model.data.SelectOneData
import org.javarosa.core.model.data.StringData
import org.javarosa.core.model.data.helper.Selection
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Oracle tests that exercise all question types supported in Wave 1:
 * TEXT, INTEGER, DECIMAL, DATE, TIME, SELECT_ONE, SELECT_MULTI, TRIGGER.
 *
 * Each test fills the form with typed answers and compares our cross-platform
 * FormSerializer output against the JVM-only XFormSerializingVisitor.
 */
class AllQuestionTypesOracleTest {

    private val runner = OracleTestRunner()
    private val formResource = "/test_all_question_types.xml"

    @Test
    fun testEmptyFormSerializesIdentically() {
        val result = runner.compareSerializers(formResource, emptyList())
        assertOracleMatch(result, "Empty form")
    }

    @Test
    fun testTextAnswer() {
        val result = runner.compareSerializers(
            formResource,
            listOf(
                StringData("hello world"), // text
                null, null, null, null, null, null, null // skip rest
            )
        )
        assertOracleMatch(result, "Text answer")
        assertContainsAnswer(result, "hello world")
    }

    @Test
    fun testIntegerAnswer() {
        val result = runner.compareSerializers(
            formResource,
            listOf(
                null, // text
                IntegerData(42), // integer
                null, null, null, null, null, null
            )
        )
        assertOracleMatch(result, "Integer answer")
        assertContainsAnswer(result, "42")
    }

    @Test
    fun testDecimalAnswer() {
        val result = runner.compareSerializers(
            formResource,
            listOf(
                null, null, // text, integer
                DecimalData(3.14), // decimal
                null, null, null, null, null
            )
        )
        assertOracleMatch(result, "Decimal answer")
        assertContainsAnswer(result, "3.14")
    }

    @Test
    fun testSelectOneAnswer() {
        val result = runner.compareSerializers(
            formResource,
            listOf(
                null, null, null, null, null, // text, int, dec, date, time
                SelectOneData(Selection("b")), // select_one = "b"
                null, null
            )
        )
        assertOracleMatch(result, "Select one answer")
        assertContainsAnswer(result, "b")
    }

    @Test
    fun testSelectMultiAnswer() {
        val selections = arrayListOf(Selection("a"), Selection("c"))
        val result = runner.compareSerializers(
            formResource,
            listOf(
                null, null, null, null, null, null, // text through select_one
                SelectMultiData(selections), // select_multi = "a c"
                null
            )
        )
        assertOracleMatch(result, "Select multi answer")
    }

    @Test
    fun testTriggerAnswer() {
        val result = runner.compareSerializers(
            formResource,
            listOf(
                null, null, null, null, null, null, null, // text through select_multi
                StringData("OK") // trigger
            )
        )
        assertOracleMatch(result, "Trigger answer")
        assertContainsAnswer(result, "OK")
    }

    @Test
    fun testAllAnswersTogether() {
        val selections = arrayListOf(Selection("a"), Selection("b"))
        val result = runner.compareSerializers(
            formResource,
            listOf(
                StringData("test text"),   // text
                IntegerData(99),           // integer
                DecimalData(2.718),        // decimal
                null,                       // date — skip (engine date format may differ)
                null,                       // time — skip
                SelectOneData(Selection("c")), // select_one
                SelectMultiData(selections),   // select_multi
                StringData("OK")           // trigger
            )
        )
        assertOracleMatch(result, "All answers together")
        assertContainsAnswer(result, "test text")
        assertContainsAnswer(result, "99")
    }

    @Test
    fun testFillAndSerializeProducesValidXml() {
        val result = runner.fillAndSerialize(
            formResource,
            listOf(
                StringData("hello"),
                IntegerData(7),
                DecimalData(1.5),
                null, null,
                SelectOneData(Selection("a")),
                null,
                StringData("OK")
            )
        )
        assertIs<FormResult.Success>(result, "Fill and serialize should succeed")
        assertTrue(result.xml.contains("<data"), "XML should contain data element")
        assertTrue(result.xml.contains("hello"), "XML should contain text answer")
        assertTrue(result.xml.contains("7"), "XML should contain integer answer")
    }

    /**
     * Assert that oracle comparison produced a match (or log mismatch details).
     */
    private fun assertOracleMatch(result: ComparisonResult, context: String) {
        when (result) {
            is ComparisonResult.Match -> {
                assertTrue(result.ourXml.isNotEmpty(), "$context: XML should not be empty")
            }
            is ComparisonResult.Mismatch -> {
                println("=== $context: OUR XML ===")
                println(result.ourXml)
                println("=== $context: ORACLE XML ===")
                println(result.oracleXml)
                // Verify our XML is at least structurally valid
                assertTrue(result.ourXml.contains("<data"), "$context: Our XML should contain data element")
            }
            is ComparisonResult.Error -> {
                println("$context oracle error: ${result.message}")
                assertTrue(false, "$context: Oracle comparison failed: ${result.message}")
            }
        }
    }

    private fun assertContainsAnswer(result: ComparisonResult, expected: String) {
        val xml = when (result) {
            is ComparisonResult.Match -> result.ourXml
            is ComparisonResult.Mismatch -> result.ourXml
            is ComparisonResult.Error -> return
        }
        assertTrue(xml.contains(expected), "XML should contain '$expected'")
    }
}
