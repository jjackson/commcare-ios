package org.commcare.xml

import org.commcare.cases.ledger.Ledger
import org.commcare.data.xml.TransactionParser
import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.kxml2.io.KXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.NoSuchElementException

/**
 * Contains all of the logic for parsing transactions in xml that pertain to
 * ledgers (balance/transfer actions)
 *
 * @author ctsims
 */
class LedgerXmlParsers(
    parser: KXmlParser,
    val storage: IStorageUtilityIndexed<Ledger>
) : TransactionParser<Array<Ledger>>(parser) {

    companion object {
        private const val TAG_QUANTITY = "quantity"
        private const val TAG_VALUE = "value"
        private const val ENTRY_ID = "id"
        private const val TRANSFER = "transfer"
        private const val TAG_BALANCE = "balance"

        const val STOCK_XML_NAMESPACE: String = "http://commcarehq.org/ledger/v1"

        private const val MODEL_ID = "entity-id"
        private const val SUBMODEL_ID = "section-id"
        private const val FINAL_NAME = "entry"
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    override fun parse(): Array<Ledger> {
        this.checkNode(arrayOf(TAG_BALANCE, TRANSFER))

        val name = parser.name.lowercase()

        val toWrite = ArrayList<Ledger>()

        val dateModified = parser.getAttributeValue(null, "date")
            ?: throw InvalidStructureException(
                "<$name> block with no date_modified attribute.", this.parser
            )
        val modified = DateUtils.parseDateTime(dateModified)

        if (name == TAG_BALANCE) {
            val entityId = parser.getAttributeValue(null, MODEL_ID)
                ?: throw InvalidStructureException(
                    "<balance> block with no $MODEL_ID attribute.", this.parser
                )

            val ledger = retrieveOrCreate(entityId)

            val sectionId = parser.getAttributeValue(null, SUBMODEL_ID)

            if (sectionId == null) {
                //Complex case: we need to update multiple sections on a per-entry basis
                while (this.nextTagInBlock(TAG_BALANCE)) {
                    object : ElementParser<Array<Ledger>?>(this.parser) {
                        @Throws(
                            InvalidStructureException::class, PlatformIOException::class,
                            PlatformXmlParserException::class
                        )
                        override fun parse(): Array<Ledger>? {
                            val productId = parser.getAttributeValue(null, ENTRY_ID)

                            while (this.nextTagInBlock(FINAL_NAME)) {
                                this.checkNode(TAG_VALUE)

                                val quantityString = parser.getAttributeValue(null, TAG_QUANTITY)
                                val sectionId = parser.getAttributeValue(null, SUBMODEL_ID)
                                if (sectionId == null || "" == sectionId) {
                                    throw InvalidStructureException(
                                        "<value> update requires a valid @$SUBMODEL_ID attribute",
                                        this.parser
                                    )
                                }
                                val quantity = this.parseInt(quantityString)

                                ledger.setEntry(sectionId, productId, quantity)
                            }
                            return null
                        }
                    }.parse()
                }
            } else {
                //Simple case - Updating one section
                while (this.nextTagInBlock(TAG_BALANCE)) {
                    this.checkNode(FINAL_NAME)
                    val id = parser.getAttributeValue(null, ENTRY_ID)
                    val quantityString = parser.getAttributeValue(null, TAG_QUANTITY)
                    if (id == null || id == "") {
                        throw InvalidStructureException(
                            "<$FINAL_NAME> update requires a valid @id attribute", this.parser
                        )
                    }
                    val quantity = this.parseInt(quantityString)
                    ledger.setEntry(sectionId, id, quantity)
                }
            }

            toWrite.add(ledger)
        } else if (name == TRANSFER) {
            val source = parser.getAttributeValue(null, "src")
            val destination = parser.getAttributeValue(null, "dest")

            if (source == null && destination == null) {
                throw InvalidStructureException(
                    "<transfer> block no source or destination id.", this.parser
                )
            }

            val sourceLeger = if (source == null) null else retrieveOrCreate(source)
            val destinationLedger = if (destination == null) null else retrieveOrCreate(destination)

            val sectionId = parser.getAttributeValue(null, SUBMODEL_ID)

            if (sectionId == null) {
                while (this.nextTagInBlock(TRANSFER)) {
                    object : ElementParser<Array<Ledger>?>(this.parser) {
                        @Throws(
                            InvalidStructureException::class, PlatformIOException::class,
                            PlatformXmlParserException::class
                        )
                        override fun parse(): Array<Ledger>? {
                            val productId = parser.getAttributeValue(null, ENTRY_ID)

                            while (this.nextTagInBlock(FINAL_NAME)) {
                                this.checkNode(TAG_VALUE)

                                val quantityString = parser.getAttributeValue(null, TAG_QUANTITY)
                                val sectionId = parser.getAttributeValue(null, SUBMODEL_ID)
                                if (sectionId == null || sectionId == "") {
                                    throw InvalidStructureException(
                                        "<value> update requires a valid @$SUBMODEL_ID attribute",
                                        this.parser
                                    )
                                }
                                val quantity = this.parseInt(quantityString)

                                sourceLeger?.setEntry(
                                    sectionId, productId,
                                    sourceLeger.getEntry(sectionId, productId) - quantity
                                )
                                destinationLedger?.setEntry(
                                    sectionId, productId,
                                    destinationLedger.getEntry(sectionId, productId) + quantity
                                )
                            }
                            return null
                        }
                    }.parse()
                }
            } else {
                while (this.nextTagInBlock(TRANSFER)) {
                    this.checkNode(FINAL_NAME)
                    val entryId = parser.getAttributeValue(null, ENTRY_ID)
                    val quantityString = parser.getAttributeValue(null, TAG_QUANTITY)
                    if (entryId == null || entryId == "") {
                        throw InvalidStructureException(
                            "<$FINAL_NAME> update requires a valid @$ENTRY_ID attribute",
                            this.parser
                        )
                    }
                    val quantity = this.parseInt(quantityString)

                    sourceLeger?.setEntry(
                        sectionId, entryId,
                        sourceLeger.getEntry(sectionId, entryId) - quantity
                    )
                    destinationLedger?.setEntry(
                        sectionId, entryId,
                        destinationLedger.getEntry(sectionId, entryId) + quantity
                    )
                }
            }

            if (sourceLeger != null) {
                toWrite.add(sourceLeger)
            }
            if (destinationLedger != null) {
                toWrite.add(destinationLedger)
            }
        }

        val tw = Array(toWrite.size) { toWrite[it] }
        //this should really be decided on _not_ in the parser...
        commit(tw)

        return tw
    }

    @Throws(InvalidStructureException::class)
    override fun parseInt(value: String?): Int {
        if (value == null) {
            throw InvalidStructureException.readableInvalidStructureException(
                "Ledger Quantities must be integers, found null text instead", parser
            )
        }
        try {
            return Integer.parseInt(value)
        } catch (nfe: NumberFormatException) {
            throw InvalidStructureException.readableInvalidStructureException(
                "Ledger Quantities must be integers, found \"$value\" instead", parser
            )
        }
    }

    @Throws(PlatformIOException::class)
    override fun commit(parsed: Array<Ledger>) {
        for (s in parsed) {
            storage().write(s)
        }
    }

    fun retrieveOrCreate(entityId: String): Ledger {
        return try {
            storage().getRecordForValue(Ledger.INDEX_ENTITY_ID, entityId)
        } catch (nsee: NoSuchElementException) {
            Ledger(entityId)
        }
    }

    fun storage(): IStorageUtilityIndexed<Ledger> {
        return storage
    }
}
