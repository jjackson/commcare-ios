package org.commcare.cases.test

import org.commcare.test.utilities.CaseTestUtils
import org.commcare.test.utilities.TestProfileConfiguration
import org.commcare.util.mocks.MockDataUtils
import org.commcare.util.mocks.MockUserDataSandbox
import org.javarosa.core.model.condition.EvaluationContext
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests verifying that the @external_id index performs correctly
 *
 * @author wpride
 */
@RunWith(value = Parameterized::class)
class CaseExternalIdTest(private val config: TestProfileConfiguration) {

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
    fun testReadExternalId() {
        config.parseIntoSandbox(javaClass.getResourceAsStream("/case_create_external_id.xml"), sandbox, false)
        val ec = MockDataUtils.buildContextWithInstance(sandbox, "casedb", CaseTestUtils.CASE_INSTANCE)
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(ec, "instance('casedb')/casedb/case[@external_id = '123']/case_name", "Two"))
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(ec, "instance('casedb')/casedb/case[@external_id = '123' and true()]/case_name", "Two"))
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(ec, "instance('casedb')/casedb/case[@case_id = 'case_two']/@external_id", "123"))
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(ec, "instance('casedb')/casedb/case[@case_id = 'case_two'][@external_id = '123']/case_name", "Two"))
    }

    @Test
    @Throws(Exception::class)
    fun testNoExternalIdFails() {
        config.parseIntoSandbox(javaClass.getResourceAsStream("/case_create_basic.xml"), sandbox, false)
        val ec = MockDataUtils.buildContextWithInstance(sandbox, "casedb", CaseTestUtils.CASE_INSTANCE)
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(ec, "instance('casedb')/casedb/case[@external_id = '123']/case_name", ""))
    }

    @Test
    @Throws(Exception::class)
    fun testEmptyDbWorks() {
        config.parseIntoSandbox(javaClass.getResourceAsStream("/empty_restore.xml"), sandbox, false)
        val ec = MockDataUtils.buildContextWithInstance(sandbox, "casedb", CaseTestUtils.CASE_INSTANCE)
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(ec,
                "instance('casedb')/casedb/case[@case_id = '123']/@external_id", ""))
    }
}
