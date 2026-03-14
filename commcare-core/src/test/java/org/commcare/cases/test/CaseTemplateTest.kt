package org.commcare.cases.test

import org.commcare.test.utilities.CaseTestUtils
import org.commcare.test.utilities.TestProfileConfiguration
import org.commcare.util.mocks.MockDataUtils
import org.commcare.util.mocks.MockUserDataSandbox
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.xpath.XPathTypeMismatchException
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test CaseDB template checking code.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
@RunWith(value = Parameterized::class)
class CaseTemplateTest(private val config: TestProfileConfiguration) {

    private lateinit var evalCtx: EvaluationContext

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<*> = TestProfileConfiguration.BulkOffOn()
    }

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val sandbox = MockDataUtils.getStaticStorage()
        val inputTransactions = "/create_cases_with_parents.xml"
        config.parseIntoSandbox(javaClass.getResourceAsStream(inputTransactions), sandbox)
        evalCtx = MockDataUtils.buildContextWithInstance(sandbox, "casedb", CaseTestUtils.CASE_INSTANCE)
    }

    @Test
    @Throws(Exception::class)
    fun testRefToData() {
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(evalCtx, "instance('casedb')/casedb/case[@case_id = 'child_node']/index/parent", "parent_node"))
    }

    /**
     * Ensure silent failure of reference that follows casedb instance template spec but doesn't point to existing data
     */
    @Test
    @Throws(Exception::class)
    fun testWellTemplatedRefToMissingData() {
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(evalCtx, "instance('casedb')/casedb/case[@case_id = 'parent_node']/index/parent", ""))
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(evalCtx, "instance('casedb')/casedb/case[@case_id = 'parent_node']/anything_can_go_here", ""))
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(evalCtx, "instance('casedb')/casedb/case[@case_id = 'parent_node']/index/anything_can_go_here", ""))
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(evalCtx, "instance('casedb')/casedb/case[@case_id = 'parent_node']/index/anything_can_go_here/@case_type", ""))
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(evalCtx, "instance('casedb')/casedb/case[@case_id = 'parent_node']/index/anything_can_go_here/@relationship", ""))
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(evalCtx, "instance('casedb')/casedb/case[@case_id = 'parent_node']/attachment/anything_can_go_here", ""))
    }

    /**
     * Ensure reference that doesn't follows casedb instance template spec fails
     */
    @Test(expected = XPathTypeMismatchException::class)
    @Throws(Exception::class)
    fun testNonSpecRefFails() {
        CaseTestUtils.xpathEvalAndCompare(evalCtx, "instance('casedb')/casedb/case[@case_id = 'parent_node']/anything_can_go_here/this_should_crash", "")
    }

    /**
     * Ensure attribute reference that doesn't follows casedb instance template spec fails
     */
    @Test(expected = XPathTypeMismatchException::class)
    @Throws(Exception::class)
    fun testNonSpecAttrRefFails() {
        CaseTestUtils.xpathEvalAndCompare(evalCtx, "instance('casedb')/casedb/case[@case_id = 'parent_node']/index/anything_can_go_here/@this_should_crash", "")
    }
}
