package org.commcare.core.parse

import org.commcare.core.interfaces.UserSandbox
import org.commcare.data.xml.TransactionParser
import org.commcare.data.xml.TransactionParserFactory
import org.commcare.modern.engine.cases.CaseIndexTable
import org.commcare.xml.bulk.BulkCaseInstanceXmlParser
import org.kxml2.io.KXmlParser

/**
 * Transaction factory for parsing the case instance xml as defined in
 * https://github.com/dimagi/commcare-core/wiki/casedb
 *
 * It's primary usage is to parse case instance xml from case query API
 */
class CaseInstanceXmlTransactionParserFactory(
    private val sandbox: UserSandbox,
    private val caseIndexTable: CaseIndexTable?
) : TransactionParserFactory {

    private var caseParser: TransactionParserFactory

    init {
        caseParser = initCaseParser()
    }

    private fun initCaseParser(): TransactionParserFactory {
        return object : TransactionParserFactory {
            var created: BulkCaseInstanceXmlParser? = null

            override fun getParser(parser: KXmlParser): TransactionParser<*> {
                if (created == null) {
                    created = BulkCaseInstanceXmlParser(parser, sandbox.getCaseStorage(), caseIndexTable)
                }
                return created!!
            }
        }
    }

    override fun getParser(parser: KXmlParser): TransactionParser<*>? {
        val name = parser.name
        if ("case".equals(name, ignoreCase = true)) {
            return caseParser.getParser(parser)
        }
        return null
    }
}
