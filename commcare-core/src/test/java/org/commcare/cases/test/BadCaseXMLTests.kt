package org.commcare.cases.test

import org.commcare.test.utilities.TestProfileConfiguration
import org.commcare.util.mocks.MockDataUtils
import org.commcare.util.mocks.MockUserDataSandbox
import org.javarosa.xml.util.InvalidStructureException
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
class BadCaseXMLTests(private val config: TestProfileConfiguration) {

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

    @Test(expected = InvalidStructureException::class)
    @Throws(Exception::class)
    fun testNoCaseID() {
        try {
            config.parseIntoSandbox(javaClass.getResourceAsStream("/case_parse/case_create_broken_no_caseid.xml"), sandbox, true)
        } finally {
            // Make sure that we didn't make a case entry for the bad case though
            Assert.assertEquals("Case XML with no id should not have created a case record", sandbox.getCaseStorage().getNumRecords(), 0)
        }
    }

    @Test(expected = InvalidStructureException::class)
    @Throws(Exception::class)
    fun testMaxLength() {
        try {
            config.parseIntoSandbox(javaClass.getResourceAsStream("/case_parse/case_create_broken_length.xml"), sandbox, true)
        } finally {
            // Make sure that we didn't make a case entry for the bad case though
            Assert.assertEquals("Case XML with no id should not have created a case record", sandbox.getCaseStorage().getNumRecords(), 0)
        }
    }

    @Test(expected = InvalidStructureException::class)
    @Throws(Exception::class)
    fun testBadCaseIndexOp() {
        try {
            config.parseIntoSandbox(javaClass.getResourceAsStream("/case_parse/broken_self_index.xml"), sandbox, true)
        } finally {
            // Make sure that we didn't make a case entry for the bad case though
            Assert.assertEquals("Case XML with invalid index not have created a case record", sandbox.getCaseStorage().getNumRecords(), 0)
        }
    }

    @Test(expected = InvalidStructureException::class)
    @Throws(Exception::class)
    fun testEmptyRelationship() {
        try {
            config.parseIntoSandbox(javaClass.getResourceAsStream("/case_parse/empty_relationship.xml"), sandbox, true)
        } finally {
            // Make sure that we didn't make a case entry for the bad case though
            Assert.assertNotEquals("Case XML with invalid index not have created a case record", sandbox.getCaseStorage().getNumRecords(), 2)
        }
    }

    @Test(expected = InvalidStructureException::class)
    @Throws(Exception::class)
    fun testNoCaseName() {
        try {
            config.parseIntoSandbox(javaClass.getResourceAsStream("/case_parse/no_name.xml"), sandbox, true)
        } finally {
            // Make sure that we didn't make a case entry for the bad case though
            Assert.assertNotEquals("Case XML with no case_name element created a case record", sandbox.getCaseStorage().getNumRecords(), 2)
        }
    }
}
