package org.commcare.cases.util.test

import org.commcare.cases.model.Case
import org.commcare.core.parse.CommCareTransactionParserFactory
import org.commcare.core.parse.ParseUtils
import org.commcare.core.sandbox.SandboxUtils
import org.commcare.data.xml.DataModelPullParser
import org.commcare.data.xml.TransactionParser
import org.commcare.data.xml.TransactionParserFactory
import org.commcare.util.mocks.MockDataUtils
import org.commcare.util.mocks.MockUserDataSandbox
import org.commcare.xml.CaseXmlParser
import org.javarosa.xml.PlatformXmlParser
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Quick test to be able to restore a set of user data
 * and ensure users and groups are properly being included
 * in case purges.
 *
 * @author ctsims
 */
class CaseParseReindexTests {

    private lateinit var sandbox: MockUserDataSandbox

    @Before
    @Throws(Exception::class)
    fun setUp() {
        sandbox = MockDataUtils.getStaticStorage()
        ParseUtils.parseIntoSandbox(javaClass.classLoader.getResourceAsStream("index_disruption/base_transactions.xml"), sandbox)
        SandboxUtils.extractEntityOwners(sandbox)
    }

    @Test
    @Throws(Exception::class)
    fun testCloseDisruption() {
        parseAndTestForBreakage("index_disruption/case_close.xml", arrayOf("base_case"))
    }

    @Test
    @Throws(Exception::class)
    fun testNoDisruption() {
        parseAndTestForBreakage("index_disruption/case_update_clean.xml", arrayOf())
    }

    @Test
    @Throws(Exception::class)
    fun testChangeOwner() {
        parseAndTestForBreakage("index_disruption/case_change_owner.xml", arrayOf("base_case"))
    }

    @Test
    @Throws(Exception::class)
    fun testRemoveIndex() {
        parseAndTestForBreakage("index_disruption/case_remove_index.xml", arrayOf("base_case"))
    }

    @Test
    @Throws(Exception::class)
    fun testAddIndex() {
        parseAndTestForBreakage("index_disruption/case_add_index.xml", arrayOf("base_case"))
    }

    @Test
    @Throws(Exception::class)
    fun testChangeIndex() {
        parseAndTestForBreakage("index_disruption/case_change_index.xml", arrayOf("base_case"))
    }

    @Throws(Exception::class)
    private fun parseAndTestForBreakage(fileName: String, expectedDisruptedIds: Array<String>) {
        val disruptedIndexes = HashSet<String>()
        val factory = object : CommCareTransactionParserFactory(sandbox) {
            override fun initCaseParser() {
                caseParser = object : TransactionParserFactory {
                    var created: CaseXmlParser? = null

                    override fun getParser(parser: PlatformXmlParser): TransactionParser<Case> {
                        if (created == null) {
                            created = object : CaseXmlParser(parser, sandbox.getCaseStorage()) {
                                override fun onIndexDisrupted(caseId: String) {
                                    disruptedIndexes.add(caseId)
                                }
                            }
                        }
                        return created!!
                    }
                }
            }
        }
        val parser = DataModelPullParser(javaClass.classLoader.getResourceAsStream(fileName), factory, true, true)
        parser.parse()

        val expectedIds = HashSet<String>()
        expectedIds.addAll(expectedDisruptedIds)

        assertEquals("Incorrect Disrupted Indexes ($fileName)", expectedIds, disruptedIndexes)
    }
}
