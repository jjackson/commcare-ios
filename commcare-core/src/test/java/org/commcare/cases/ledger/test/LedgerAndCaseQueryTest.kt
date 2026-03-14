package org.commcare.cases.ledger.test

import org.commcare.test.utilities.CaseTestUtils
import org.commcare.test.utilities.TestProfileConfiguration
import org.commcare.util.mocks.MockDataUtils
import org.commcare.util.mocks.MockUserDataSandbox
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.xpath.XPathTypeMismatchException
import org.javarosa.xpath.parser.XPathSyntaxException
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test interplay between ledgers and cases.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
@RunWith(value = Parameterized::class)
class LedgerAndCaseQueryTest(private val config: TestProfileConfiguration) {

    private lateinit var evalContext: EvaluationContext

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<*> = TestProfileConfiguration.BulkOffOn()
    }

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val sandbox = MockDataUtils.getStaticStorage()

        // load cases that will be referenced by ledgers
        config.parseIntoSandbox(javaClass.getResourceAsStream("/create_case_for_ledger.xml"), sandbox, true)

        // load ledger data
        config.parseIntoSandbox(javaClass.getResourceAsStream("/ledger_create_basic.xml"), sandbox, true)

        // create an evaluation context that has ledger and case instances setup
        val instanceRefToId = HashMap<String, String>()
        instanceRefToId[CaseTestUtils.LEDGER_INSTANCE] = "ledger"
        instanceRefToId[CaseTestUtils.CASE_INSTANCE] = "casedb"
        evalContext = MockDataUtils.buildContextWithInstances(sandbox, instanceRefToId)
    }

    @Test
    @Throws(XPathSyntaxException::class)
    fun ledgerQueriesWithLedgerData() {
        // case id 'market_basket' exists, and ledger data has been attached it
        Assert.assertTrue(
                CaseTestUtils.xpathEvalAndCompare(evalContext,
                        "instance('ledger')/ledgerdb/ledger[@entity-id='market_basket']/section[@section-id='edible_stock']/entry[@id='rice']",
                        10.0))
        // Reference valid case but invalid section id
        Assert.assertTrue(
                CaseTestUtils.xpathEvalAndCompare(evalContext,
                        "instance('ledger')/ledgerdb/ledger[@entity-id='market_basket']/section[@section-id='non-existent-section']",
                        ""))
        // case id 'ocean_state_job_lot' doesn't exists, but the ledger data
        // corresponding to it does
        Assert.assertTrue(
                CaseTestUtils.xpathEvalAndCompare(evalContext,
                        "instance('ledger')/ledgerdb/ledger[@entity-id='ocean_state_job_lot']/section[@section-id='cleaning_stock']/entry[@id='soap']",
                        9.0))
    }

    @Test
    @Throws(XPathSyntaxException::class)
    fun ledgerQueriesWithoutReferencedLedgerData() {
        // case id 'star_market' exists but no ledger data has been attached to it
        Assert.assertTrue(
                CaseTestUtils.xpathEvalAndCompare(evalContext,
                        "instance('ledger')/ledgerdb/ledger[@entity-id='star_market']", ""))
        Assert.assertTrue(
                CaseTestUtils.xpathEvalAndCompare(evalContext,
                        "instance('ledger')/ledgerdb/ledger[@entity-id='star_market']/section[@section-id='non-existent-section']", ""))
    }

    @Test
    @Throws(XPathSyntaxException::class)
    fun fakeLedgerQueriesFailCorrectly() {
        // case id 'totally-fake' doesn't exist
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContext,
                "instance('ledger')/ledgerdb/ledger[@entity-id='totally-fake']", ""))
    }

    @Test
    @Throws(Exception::class)
    fun ledgerQueriesWithNoLedgerData() {
        // case id 'star_market' exists but no ledger data been loaded at all
        val evalContextWithoutLedgers = createContextWithNoLedgers()

        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContextWithoutLedgers,
                "instance('ledger')/ledgerdb/ledger[@entity-id='star_market']", ""))
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContextWithoutLedgers,
                "instance('ledger')/ledgerdb/ledger[@entity-id='']/section[@section-id='']/entry[@entry-id='']", ""))
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContextWithoutLedgers,
                "instance('ledger')/ledgerdb/ledger/section/entry", ""))
    }

    @Test(expected = XPathTypeMismatchException::class)
    @Throws(Exception::class)
    fun ledgerQueriesWithBadTemplate() {
        // case id 'star_market' exists but no ledger data been loaded at all
        val evalContextWithoutLedgers = createContextWithNoLedgers()
        CaseTestUtils.xpathEval(evalContextWithoutLedgers,
                "instance('ledger')/ledgerdb/ledger[@entity-id='star_market']/not-section[@section-id='']/entry[@entry-id='']")
    }

    @Throws(Exception::class)
    private fun createContextWithNoLedgers(): EvaluationContext {
        val sandbox = MockDataUtils.getStaticStorage()

        // load cases that will be referenced by ledgers
        config.parseIntoSandbox(javaClass.getResourceAsStream("/create_case_for_ledger.xml"), sandbox, true)

        // create an evaluation context that has ledger and case instances setup
        val instanceRefToId = HashMap<String, String>()
        instanceRefToId[CaseTestUtils.LEDGER_INSTANCE] = "ledger"
        instanceRefToId[CaseTestUtils.CASE_INSTANCE] = "casedb"
        return MockDataUtils.buildContextWithInstances(sandbox, instanceRefToId)
    }
}
