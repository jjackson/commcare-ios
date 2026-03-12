package org.commcare.core.parse

import org.commcare.cases.instance.FixtureIndexSchema
import org.commcare.core.interfaces.UserSandbox
import org.commcare.data.xml.TransactionParser
import org.commcare.data.xml.TransactionParserFactory
import org.commcare.xml.CaseXmlParser
import org.commcare.xml.FixtureIndexSchemaParser
import org.commcare.xml.FixtureXmlParser
import org.commcare.xml.IndexedFixtureXmlParser
import org.commcare.xml.LedgerXmlParsers
import org.commcare.xml.bulk.LinearBulkProcessingCaseXmlParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.xml.PlatformXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * The CommCare Transaction Parser Factory (whew!) wraps all of the current
 * transactions that CommCare knows about, and provides the appropriate hooks for
 * parsing through XML and dispatching the right handler for each transaction.
 *
 * It should be the central point of processing for transactions (eliminating the
 * use of the old datamodel based processors) and should be used in any situation where
 * a transaction is expected to be present.
 *
 * It is expected to behave more or less as a black box, in that it directly creates/modifies
 * the data models on the system, rather than producing them for another layer or processing.
 *
 * V2: The CommCareTranactionParserFactory was refactored to be shared across Android/J2ME/Touchforms
 * as much possible. The parsing logic is largely the same across platforms. They mainly differ
 * in the UserSandbox sandbox implementation and in some nuances of the parsers, which is achieved
 * by overriding the init methods as needed. This is the "pure" Java implementation: J2METransactionParserFactory
 * and AndroidTransactionParserFactory override it for their respective platforms.
 *
 * @author ctsims
 * @author wspride
 */
open class CommCareTransactionParserFactory @JvmOverloads constructor(
    protected val sandbox: UserSandbox,
    useBulkProcessing: Boolean = false
) : TransactionParserFactory {

    @JvmField
    protected var userParser: TransactionParserFactory? = null
    @JvmField
    protected var caseParser: TransactionParserFactory? = null
    @JvmField
    protected var stockParser: TransactionParserFactory? = null
    @JvmField
    protected var fixtureParser: TransactionParserFactory? = null
    private val fixtureSchemas: MutableMap<String, FixtureIndexSchema> = HashMap()
    private val processedFixtures: MutableSet<String> = HashSet()

    protected var isBulkProcessingEnabled: Boolean = useBulkProcessing

    private var requests: Int = 0

    init {
        this.initFixtureParser()
        this.initUserParser()
        this.initCaseParser()
        this.initStockParser()
    }

    override fun getParser(parser: PlatformXmlParser): TransactionParser<*>? {
        val namespace = parser.getNamespace()
        val name = parser.getName()
        if (LedgerXmlParsers.STOCK_XML_NAMESPACE == namespace) {
            if (stockParser == null) {
                throw RuntimeException("Couldn't process Stock transaction without initialization!")
            }
            req()
            return stockParser!!.getParser(parser)
        } else if ("case".equals(name, ignoreCase = true)) {
            if (caseParser == null) {
                throw RuntimeException("Couldn't receive Case transaction without initialization!")
            }
            req()
            return caseParser!!.getParser(parser)
        } else if ("registration".equals(name, ignoreCase = true)) {
            if (userParser == null) {
                throw RuntimeException("Couldn't receive User transaction without initialization!")
            }
            req()
            return userParser!!.getParser(parser)
        } else if (FixtureIndexSchemaParser.INDICE_SCHEMA.equals(name, ignoreCase = true)) {
            return FixtureIndexSchemaParser(parser, fixtureSchemas, processedFixtures)
        } else if ("fixture".equals(name, ignoreCase = true)) {
            val id = parser.getAttributeValue(null, "id")
            val isIndexedAttr = parser.getAttributeValue(null, "indexed")
            val isIndexed = "true" == isIndexedAttr
            req()
            processedFixtures.add(id!!)
            if (isIndexed) {
                val schema = fixtureSchemas[id]
                return IndexedFixtureXmlParser(parser, id, schema, sandbox)
            } else {
                return fixtureParser!!.getParser(parser)
            }
        } else if ("sync".equals(name, ignoreCase = true) &&
            "http://commcarehq.org/sync" == namespace
        ) {
            return object : TransactionParser<String>(parser) {
                @Throws(PlatformIOException::class)
                override fun commit(parsed: String) {
                }

                @Throws(
                    InvalidStructureException::class,
                    PlatformIOException::class,
                    PlatformXmlParserException::class,
                    UnfullfilledRequirementsException::class
                )
                override fun parse(): String {
                    this.checkNode("sync")
                    this.nextTag("restore_id")
                    val syncToken = parser.nextText()
                        ?: throw InvalidStructureException(
                            "Sync block must contain restore_id with valid ID inside!", parser
                        )
                    sandbox.syncToken = syncToken
                    return syncToken
                }
            }
        }
        return null
    }

    protected open fun req() {
        requests++
        reportProgress(requests)
    }

    open fun reportProgress(total: Int) {
        // Overridden at the android level
    }

    internal open fun initUserParser() {
        userParser = object : TransactionParserFactory {
            var created: UserXmlParser? = null

            override fun getParser(parser: PlatformXmlParser): TransactionParser<*> {
                if (created == null) {
                    created = UserXmlParser(parser, sandbox.getUserStorage())
                }
                return created!!
            }
        }
    }

    open fun initFixtureParser() {
        fixtureParser = object : TransactionParserFactory {
            var created: FixtureXmlParser? = null

            override fun getParser(parser: PlatformXmlParser): TransactionParser<*> {
                if (created == null) {
                    created = FixtureXmlParser(parser, true, sandbox.getUserFixtureStorage())
                }
                return created!!
            }
        }
    }

    open fun initCaseParser() {
        caseParser = if (isBulkProcessingEnabled) {
            getBulkCaseParser()
        } else {
            getNormalCaseParser()
        }
    }

    open fun initStockParser() {
        stockParser = TransactionParserFactory { parser ->
            LedgerXmlParsers(parser, sandbox.getLedgerStorage())
        }
    }

    fun getSyncToken(): String? {
        return sandbox.syncToken
    }

    open fun getNormalCaseParser(): TransactionParserFactory {
        return object : TransactionParserFactory {
            var created: CaseXmlParser? = null

            override fun getParser(parser: PlatformXmlParser): TransactionParser<*> {
                if (created == null) {
                    created = CaseXmlParser(parser, sandbox.getCaseStorage())
                }
                return created!!
            }
        }
    }

    open fun getBulkCaseParser(): TransactionParserFactory {
        return object : TransactionParserFactory {
            var created: LinearBulkProcessingCaseXmlParser? = null

            override fun getParser(parser: PlatformXmlParser): TransactionParser<*> {
                if (created == null) {
                    created = LinearBulkProcessingCaseXmlParser(parser, sandbox.getCaseStorage())
                }
                return created!!
            }
        }
    }
}
