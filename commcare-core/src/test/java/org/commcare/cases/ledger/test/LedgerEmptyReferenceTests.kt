package org.commcare.cases.ledger.test

import org.commcare.test.utilities.CaseTestUtils
import org.commcare.test.utilities.TestProfileConfiguration
import org.commcare.util.mocks.MockDataUtils
import org.commcare.util.mocks.MockUserDataSandbox
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.xpath.XPathMissingInstanceException
import org.javarosa.xpath.parser.XPathSyntaxException
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test handling of empty ledger queries
 *
 * @author Clayton Sims
 */
@RunWith(value = Parameterized::class)
class LedgerEmptyReferenceTests(private val config: TestProfileConfiguration) {

    private lateinit var evalContextWithLedger: EvaluationContext

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<*> = TestProfileConfiguration.BulkOffOn()
    }

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val sandbox = MockDataUtils.getStaticStorage()
        config.parseIntoSandbox(javaClass.getResourceAsStream("/ledger_tests/no_data_restore.xml"), sandbox, true)
        evalContextWithLedger = MockDataUtils.buildContextWithInstance(sandbox, "ledger", CaseTestUtils.LEDGER_INSTANCE)
    }

    @Test
    @Throws(XPathSyntaxException::class)
    fun queryMissingLedgerPath() {
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContextWithLedger,
                "instance('ledger')/ledgerdb/ledger[@entity-id='H_mart']",
                ""))
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContextWithLedger,
                "instance('ledger')/ledgerdb/ledger[@entity-id='market_basket']/section[@section-id='amphibious_stock']",
                ""))
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContextWithLedger,
                "instance('ledger')/ledgerdb/ledger[@entity-id='market_basket']/section[@section-id='cleaning_stock']/entry[@id='bleach']",
                ""))

        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContextWithLedger,
                "instance('ledger')/ledgerdb/ledger[@entity-id='H_mart']/section[@section-id='edible_stock']/entry[@id='beans']",
                ""))
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContextWithLedger,
                "instance('ledger')/ledgerdb/ledger[@entity-id='market_basket']/section[@section-id='amphibious_stock']/entry[@id='beans']",
                ""))
    }

    @Test(expected = XPathMissingInstanceException::class)
    @Throws(XPathSyntaxException::class)
    fun ledgerQueriesWithNoLedgerInstance() {
        val emptyEvalContext = EvaluationContext(null)
        CaseTestUtils.xpathEval(emptyEvalContext, "instance('ledger')/ledgerdb/ledger[@entity-id='H_mart']")
    }
}
