package org.commcare.cases.test

import org.commcare.test.utilities.CaseTestUtils
import org.commcare.test.utilities.TestProfileConfiguration
import org.commcare.util.mocks.MockDataUtils
import org.commcare.util.mocks.MockUserDataSandbox
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test suite to verify end-to-end parsing of inbound case XML
 * and reading values back from the casedb model
 *
 * @author ctsims
 */
@RunWith(value = Parameterized::class)
class CaseParseAndReadTest(private val config: TestProfileConfiguration) {

    private lateinit var sandbox: MockUserDataSandbox

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<*> = TestProfileConfiguration.BulkOffOn()
    }

    @Before
    fun setUp() {
        sandbox = MockDataUtils.getStaticStorage()
    }

    @Test
    @Throws(Exception::class)
    fun testReadCaseDB() {
        parseAndCompareCaseDbState("/case_create_basic.xml", "/case_create_basic_output.xml")

        val ec = MockDataUtils.buildContextWithInstance(sandbox, "casedb", CaseTestUtils.CASE_INSTANCE)
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(ec, "instance('casedb')/casedb/case[@case_id = 'case_one']/case_name", "case"))
    }

    @Test
    @Throws(Exception::class)
    fun testDoubleCreateCaseWithUpdate() {
        parseAndCompareCaseDbState("/case_create_overwrite.xml", "/case_create_overwrite_output.xml")
        val ec = MockDataUtils.buildContextWithInstance(sandbox, "casedb", CaseTestUtils.CASE_INSTANCE)
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(ec, "instance('casedb')/casedb/case[@case_id = 'case_one']/case_name", "case_overwrite"))
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(ec, "instance('casedb')/casedb/case[@case_id = 'case_one']/case_property1", "one"))
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(ec, "instance('casedb')/casedb/case[@case_id = 'case_one']/case_property2", "property_two"))
    }

    @Throws(Exception::class)
    private fun parseAndCompareCaseDbState(inputTransactions: String, caseDbState: String) {
        config.parseIntoSandbox(javaClass.getResourceAsStream(inputTransactions), sandbox, false)
        CaseTestUtils.compareCaseDbState(sandbox, javaClass.getResourceAsStream(caseDbState))
    }
}
