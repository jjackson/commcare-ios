package org.commcare.cases.util.test

import org.commcare.core.parse.CaseInstanceXmlTransactionParserFactory
import org.commcare.core.parse.ParseUtils
import org.commcare.data.xml.TransactionParserFactory
import org.commcare.test.utilities.CaseTestUtils
import org.commcare.util.mocks.MockDataUtils
import org.commcare.util.mocks.MockUserDataSandbox
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.xpath.parser.XPathSyntaxException
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream

class BulkCaseInstanceXmlParserTests {

    private lateinit var sandbox: MockUserDataSandbox

    @Before
    fun setUp() {
        sandbox = MockDataUtils.getStaticStorage()
    }

    @Test
    @Throws(UnfullfilledRequirementsException::class, InvalidStructureException::class,
            XmlPullParserException::class, IOException::class, XPathSyntaxException::class)
    fun testValidCaseInstanceXml() {
        parseXml("case_instance_parse/case_instance_valid.xml")
        CaseTestUtils.compareCaseDbState(sandbox,
                javaClass.classLoader.getResourceAsStream("case_instance_parse/case_instance_output.xml"))
        val ec = MockDataUtils.buildContextWithInstance(sandbox, "casedb",
                CaseTestUtils.CASE_INSTANCE)
        Assert.assertTrue(CaseTestUtils.xpathEvalAndCompare(ec,
                "instance('casedb')/casedb/case[@case_id = 'f6dff792-2599-4fd3-9e86-c11ef61f0302']/case_name",
                "tapid papid"))
    }

    @Throws(UnfullfilledRequirementsException::class, InvalidStructureException::class,
            XmlPullParserException::class, IOException::class)
    private fun parseXml(resourceFilePath: String) {
        val dataStream = javaClass.classLoader.getResourceAsStream(resourceFilePath)
        val factory: TransactionParserFactory = CaseInstanceXmlTransactionParserFactory(sandbox, null)
        ParseUtils.parseIntoSandbox(dataStream, factory, false, false)
        dataStream.close()
    }
}
