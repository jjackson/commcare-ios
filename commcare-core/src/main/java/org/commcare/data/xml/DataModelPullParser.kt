package org.commcare.data.xml

import org.commcare.resources.model.CommCareOTARestoreListener
import org.javarosa.core.log.WrappedException
import org.javarosa.xml.ElementParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.core.io.PlatformInputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import kotlin.jvm.Throws

/**
 * A DataModelPullParser pulls together the parsing of
 * different data models in order to be able to perform
 * a master update/restore of remote data.
 *
 * @author ctsims
 */
class DataModelPullParser : ElementParser<Boolean> {

    private val errors: ArrayList<String>
    private val factory: TransactionParserFactory
    private val failfast: Boolean
    private val deep: Boolean
    private val `is`: PlatformInputStream
    private val requiredRootEnvelope: String? = null
    private val rListener: CommCareOTARestoreListener?

    @Throws(InvalidStructureException::class, PlatformIOException::class)
    constructor(`is`: PlatformInputStream, factory: TransactionParserFactory) :
            this(`is`, factory, false)

    @Throws(InvalidStructureException::class, PlatformIOException::class)
    constructor(`is`: PlatformInputStream, factory: TransactionParserFactory, rl: CommCareOTARestoreListener?) :
            this(`is`, factory, false, false, rl)

    @Throws(InvalidStructureException::class, PlatformIOException::class)
    constructor(`is`: PlatformInputStream, factory: TransactionParserFactory, deep: Boolean) :
            this(`is`, factory, false, deep)

    @Throws(InvalidStructureException::class, PlatformIOException::class)
    constructor(`is`: PlatformInputStream, factory: TransactionParserFactory, failfast: Boolean, deep: Boolean) :
            this(`is`, factory, failfast, deep, null)

    @Throws(InvalidStructureException::class, PlatformIOException::class)
    constructor(
        `is`: PlatformInputStream,
        factory: TransactionParserFactory,
        failfast: Boolean,
        deep: Boolean,
        rListener: CommCareOTARestoreListener?
    ) : super(instantiateParser(`is`)) {
        this.`is` = `is`
        this.failfast = failfast
        this.factory = factory
        errors = ArrayList()
        this.deep = deep
        this.rListener = rListener
    }

    @Throws(
        InvalidStructureException::class,
        PlatformIOException::class,
        PlatformXmlParserException::class,
        UnfullfilledRequirementsException::class
    )
    override fun parse(): Boolean {
        try {
            val rootName = parser.getName()

            if (requiredRootEnvelope != null && requiredRootEnvelope != rootName) {
                throw InvalidStructureException(
                    "Invalid xml evelope: \"$rootName\" when looking for \"$requiredRootEnvelope\"",
                    parser
                )
            }

            val itemString = parser.getAttributeValue(null, "items")
            var itemNumber = -1

            if (itemString != null) {
                try {
                    itemNumber = itemString.toInt()
                } catch (e: NumberFormatException) {
                    itemNumber = 0
                }
                if (rListener != null) {
                    rListener.setTotalForms(itemNumber)
                }
            }
            // Here we'll go through in search of CommCare data models and parse
            // them using the appropriate CommCare Model data parser.

            val parsersUsed = LinkedHashSet<TransactionParser<*>>()
            // Go through each child of the root element
            parseBlock(rootName, parsersUsed)

            for (p in parsersUsed) {
                if (failfast) {
                    p.flush()
                } else {
                    try {
                        p.flush()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        deal(e, "Bulk Flush")
                    }
                }
            }
        } finally {
            // kxmlparser might close the stream, but we can't be sure, especially if
            // we bail early due to schema errors
            try {
                `is`.close()
            } catch (ioe: PlatformIOException) {
                // swallow
            }
        }

        return errors.size == 0
    }

    @Throws(
        InvalidStructureException::class,
        PlatformIOException::class,
        PlatformXmlParserException::class,
        UnfullfilledRequirementsException::class
    )
    private fun parseBlock(root: String?, parsers: LinkedHashSet<TransactionParser<*>>) {
        var parsedCounter = 0
        while (this.nextTagInBlock(root)) {
            if (listenerSet()) {
                rListener!!.onUpdate(parsedCounter)
                parsedCounter++
            }

            val name = parser.getName() ?: continue

            val transaction = factory.getParser(parser)
            if (transaction == null) {
                if (deep) {
                    // nothing to be done for this element, try recursing
                    parseBlock(name, parsers)
                } else {
                    this.skipBlock(name)
                }
            } else {
                parsers.add(transaction)
                if (failfast) {
                    transaction.parse()
                } else {
                    try {
                        transaction.parse()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        deal(e, name)
                    }
                }
            }
        }
    }

    @Throws(PlatformXmlParserException::class, PlatformIOException::class)
    private fun deal(e: Exception, parentTag: String) {
        errors.add(WrappedException.printException(e))
        this.skipBlock(parentTag)

        if (failfast) {
            throw WrappedException(e)
        }
    }

    fun getParseErrors(): Array<String> {
        return errors.toTypedArray()
    }

    fun listenerSet(): Boolean {
        return rListener != null
    }
}
