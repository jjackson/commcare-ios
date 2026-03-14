package org.commcare.test.utilities

import org.commcare.cases.instance.CaseInstanceTreeElement
import org.commcare.util.mocks.MockUserDataSandbox
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.ExternalDataInstance
import org.javarosa.model.xform.DataModelSerializer
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.parser.XPathSyntaxException
import org.junit.Assert
import org.kxml2.kdom.Document
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class CaseTestUtils {
    companion object {
        @JvmField
        val CASE_INSTANCE = "jr://instance/casedb"
        @JvmField
        val LEDGER_INSTANCE = "jr://instance/ledgerdb"
        @JvmField
        val FIXTURE_INSTANCE_PRODUCT = "jr://fixture/commtrack:products"

        @JvmStatic
        @Throws(XPathSyntaxException::class)
        fun xpathEvalAndCompare(
            evalContext: EvaluationContext,
            input: String,
            expectedOutput: Any
        ): Boolean {
            val expr = XPathParseTool.parseXPath(input)!!
            val output = FunctionUtils.unpack(expr.eval(evalContext))
            return expectedOutput == output
        }

        @JvmStatic
        @Throws(XPathSyntaxException::class)
        fun xpathEvalAndAssert(
            evalContext: EvaluationContext,
            input: String,
            expectedOutput: Any
        ) {
            val expr = XPathParseTool.parseXPath(input)!!
            val output = FunctionUtils.unpack(expr.eval(evalContext))
            Assert.assertEquals("XPath: $input", expectedOutput, output)
        }

        @JvmStatic
        @Throws(XPathSyntaxException::class)
        fun xpathEval(
            evalContext: EvaluationContext,
            input: String
        ): Any? {
            val expr = XPathParseTool.parseXPath(input)!!
            return FunctionUtils.unpack(expr.eval(evalContext))
        }

        /**
         * Compares the case db state in a sandbox with the given data
         *
         * @param sandbox     Sandbox with case data
         * @param caseDbState expected state for casedb instance
         */
        @JvmStatic
        @Throws(IOException::class)
        fun compareCaseDbState(sandbox: MockUserDataSandbox, caseDbState: InputStream) {
            val parsedDb = serializeCaseInstanceFromSandbox(sandbox)
            val parsed = XmlComparator.getDocumentFromStream(ByteArrayInputStream(parsedDb))
            val loaded = XmlComparator.getDocumentFromStream(caseDbState)

            try {
                XmlComparator.isDOMEqual(parsed, loaded)
            } catch (e: Exception) {
                print(String(parsedDb))

                // NOTE: The DOM's definitely don't match here, so the strings cannot be the same.
                // The reason we are asserting equality is because the delta between the strings is
                // likely to do a good job of contextualizing where the DOM's don't match.
                Assert.assertEquals(
                    "CaseDB output did not match expected structure(${e.message})",
                    String(dumpStream(caseDbState)), String(parsedDb)
                )
            }
        }

        private fun serializeCaseInstanceFromSandbox(sandbox: MockUserDataSandbox): ByteArray {
            try {
                val bos = ByteArrayOutputStream()
                val s = DataModelSerializer(bos, TestInstanceInitializer(sandbox))

                s.serialize(
                    ExternalDataInstance(CASE_INSTANCE, CaseInstanceTreeElement.MODEL_NAME),
                    null
                )

                return bos.toByteArray()
            } catch (e: IOException) {
                throw RuntimeException(e.message)
            }
        }

        @Throws(IOException::class)
        private fun dumpStream(inputStream: InputStream): ByteArray {
            val bos = ByteArrayOutputStream()
            StreamsUtil.writeFromInputToOutput(inputStream, bos)
            return bos.toByteArray()
        }
    }
}
