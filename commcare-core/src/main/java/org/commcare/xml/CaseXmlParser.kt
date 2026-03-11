package org.commcare.xml

import org.commcare.cases.model.Case
import org.commcare.cases.model.CaseIndex
import org.commcare.data.xml.TransactionParser
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_ATTACHMENT_NODE
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_CLOSE_NODE
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_CREATE_NODE
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_INDEX_NODE
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_NODE
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_ATTACHMENT_FROM
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_ATTACHMENT_NAME
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_ATTACHMENT_SRC
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_CASE_ID
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_CASE_NAME
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_CASE_TYPE
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_CATEGORY
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_DATE_MODIFIED
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_DATE_OPENED
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_EXTERNAL_ID
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_INDEX_CASE_TYPE
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_INDEX_RELATIONSHIP
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_OWNER_ID
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_STATE
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_USER_ID
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_UPDATE_NODE
import org.commcare.xml.CaseXmlParserUtil.Companion.checkForMaxLength
import org.commcare.xml.CaseXmlParserUtil.Companion.validateMandatoryProperty
import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.util.externalizable.SerializationLimitationException
import org.javarosa.xml.util.ActionableInvalidStructureException
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.PlatformXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.NoSuchElementException

/**
 * The CaseXML Parser is responsible for processing and performing
 * case transactions from an incoming XML stream. It will perform
 * all of the actions specified by the transaction (Create/modify/close)
 * against the application's current storage.
 *
 * NOTE: Future work on case XML Processing should shift to the BulkProcessingCaseXmlParser, since
 * there's no good way for us to maintain multiple different sources for all of the complex logic
 * inherent in this process. If anything would be added here, it should likely be replaced rather
 * than implemented in both places.
 *
 * @author ctsims
 */
open class CaseXmlParser : TransactionParser<Case>, CaseIndexChangeListener {

    private val storage: IStorageUtilityIndexed<*>
    private val acceptCreateOverwrites: Boolean

    constructor(parser: PlatformXmlParser, storage: IStorageUtilityIndexed<*>) : this(parser, true, storage)

    /**
     * Creates a Parser for case blocks in the XML stream provided.
     *
     * @param parser                 The parser for incoming XML.
     * @param acceptCreateOverwrites Whether an Exception should be thrown if the transaction
     *                               contains create actions for cases which already exist.
     */
    constructor(
        parser: PlatformXmlParser, acceptCreateOverwrites: Boolean,
        storage: IStorageUtilityIndexed<*>
    ) : super(parser) {
        this.acceptCreateOverwrites = acceptCreateOverwrites
        this.storage = storage
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    override fun parse(): Case? {
        checkNode(CASE_NODE)

        val caseId = parser.getAttributeValue(null, CASE_PROPERTY_CASE_ID)!!
        validateMandatoryProperty(CASE_PROPERTY_CASE_ID, caseId, "", parser)

        val dateModified = parser.getAttributeValue(null, CASE_PROPERTY_DATE_MODIFIED)!!
        validateMandatoryProperty(CASE_PROPERTY_DATE_MODIFIED, dateModified, caseId, parser)

        val modified = DateUtils.parseDateTime(dateModified)

        val userId = parser.getAttributeValue(null, CASE_PROPERTY_USER_ID)

        var caseForBlock: Case? = null
        var isCreateOrUpdate = false

        while (nextTagInBlock(CASE_NODE)) {
            val action = parser.name!!.lowercase()
            when (action) {
                CASE_CREATE_NODE -> {
                    caseForBlock = createCase(caseId, modified!!, userId)
                    isCreateOrUpdate = true
                }
                CASE_UPDATE_NODE -> {
                    caseForBlock = loadCase(caseForBlock, caseId, true)
                    updateCase(caseForBlock!!, caseId)
                    isCreateOrUpdate = true
                }
                CASE_CLOSE_NODE -> {
                    caseForBlock = loadCase(caseForBlock, caseId, true)
                    closeCase(caseForBlock!!, caseId)
                }
                CASE_INDEX_NODE -> {
                    caseForBlock = loadCase(caseForBlock, caseId, false)
                    indexCase(caseForBlock!!, caseId)
                }
                CASE_ATTACHMENT_NODE -> {
                    caseForBlock = loadCase(caseForBlock, caseId, false)
                    processCaseAttachment(caseForBlock!!)
                }
            }
        }

        if (caseForBlock != null) {
            caseForBlock.setLastModified(modified!!)

            try {
                commit(caseForBlock)
            } catch (e: SerializationLimitationException) {
                throw InvalidStructureException(
                    "One of the property values for the case named '" +
                            caseForBlock.getName() + "' is too large (by " + e.percentOversized +
                            "%). Please show your supervisor."
                )
            }

            if (isCreateOrUpdate) {
                onCaseCreateUpdate(caseId)
            }
        }

        return null
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    private fun createCase(caseId: String, modified: java.util.Date, userId: String?): Case {
        val data = arrayOfNulls<String>(3)
        var caseForBlock: Case? = null

        while (nextTagInBlock(CASE_CREATE_NODE)) {
            val tag = parser.name!!
            when (tag) {
                CASE_PROPERTY_CASE_TYPE -> data[0] = parser.nextText()!!.trim()
                CASE_PROPERTY_OWNER_ID -> data[1] = parser.nextText()!!.trim()
                CASE_PROPERTY_CASE_NAME -> data[2] = parser.nextText()!!.trim()
                else -> throw InvalidStructureException(
                    "Expected one of [case_type, owner_id, case_name], found ${parser.name!!}", parser
                )
            }
        }

        if (data[0] == null || data[2] == null) {
            throw InvalidStructureException(
                "One of [case_type, case_name] is missing for case <create> with ID: $caseId", parser
            )
        }

        if (acceptCreateOverwrites) {
            caseForBlock = retrieve(caseId)

            if (caseForBlock != null) {
                caseForBlock.setName(data[2])
                caseForBlock.setTypeId(data[0])
            }
        }

        if (caseForBlock == null) {
            // The case is either not present on the phone, or we're on strict tolerance
            caseForBlock = buildCase(data[2]!!, data[0]!!)
            caseForBlock.setCaseId(caseId)
            caseForBlock.setDateOpened(modified)
        }

        if (data[1] != null) {
            caseForBlock.setUserId(data[1])
        } else {
            caseForBlock.setUserId(userId)
        }

        if (caseForBlock.getUserId() == null || caseForBlock.getUserId()!!.contentEquals("")) {
            throw InvalidStructureException(
                "One of [user_id, owner_id] is missing for case <create> with ID: $caseId", parser
            )
        }

        checkForMaxLength(caseForBlock)

        return caseForBlock
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    private fun updateCase(caseForBlock: Case, caseId: String) {
        while (nextTagInBlock(CASE_UPDATE_NODE)) {
            val key = parser.name!!
            val value = parser.nextText()!!.trim()

            when (key) {
                CASE_PROPERTY_CASE_TYPE -> caseForBlock.setTypeId(value)
                CASE_PROPERTY_CASE_NAME -> caseForBlock.setName(value)
                CASE_PROPERTY_DATE_OPENED -> caseForBlock.setDateOpened(DateUtils.parseDate(value))
                CASE_PROPERTY_OWNER_ID -> {
                    val oldUserId = caseForBlock.getUserId()
                    if (oldUserId != value) {
                        onIndexDisrupted(caseId)
                    }
                    caseForBlock.setUserId(value)
                }
                CASE_PROPERTY_EXTERNAL_ID -> caseForBlock.setExternalId(value)
                CASE_PROPERTY_CATEGORY -> caseForBlock.setCategory(value)
                CASE_PROPERTY_STATE -> caseForBlock.setState(value)
                else -> caseForBlock.setProperty(key, value)
            }
        }
        checkForMaxLength(caseForBlock)
    }

    @Throws(InvalidStructureException::class)
    private fun loadCase(caseForBlock: Case?, caseId: String, errorIfMissing: Boolean): Case? {
        var result = caseForBlock
        if (result == null) {
            result = retrieve(caseId)
        }
        if (errorIfMissing && result == null) {
            throw InvalidStructureException.readableInvalidStructureException(
                "Unable to update or close case $caseId, it wasn't found", parser
            )
        }
        return result
    }

    @Throws(PlatformIOException::class)
    private fun closeCase(caseForBlock: Case, caseId: String) {
        caseForBlock.setClosed(true)
        commit(caseForBlock)
        onIndexDisrupted(caseId)
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    private fun indexCase(caseForBlock: Case, caseId: String) {
        while (nextTagInBlock(CASE_INDEX_NODE)) {
            val indexName = parser.name!!
            val caseType = parser.getAttributeValue(null, CASE_PROPERTY_INDEX_CASE_TYPE)

            var relationship = parser.getAttributeValue(null, CASE_PROPERTY_INDEX_RELATIONSHIP)
            if (relationship == null) {
                relationship = CaseIndex.RELATIONSHIP_CHILD
            } else if ("" == relationship) {
                throw InvalidStructureException(
                    "Invalid Case Transaction: Attempt to create '' relationship type", parser
                )
            }

            var value: String? = parser.nextText()!!.trim()

            if (value == caseId) {
                throw ActionableInvalidStructureException(
                    "case.error.self.index", arrayOf(caseId),
                    "Case $caseId cannot index itself"
                )
            }

            //Remove any ambiguity associated with empty values
            if (value == "") {
                value = null
            }
            //Process blank inputs in the same manner as data fields (IE: Remove the underlying model)
            if (value == null) {
                if (caseForBlock.removeIndex(indexName)) {
                    onIndexDisrupted(caseId)
                }
            } else {
                caseForBlock.setIndex(CaseIndex(indexName, caseType, value, relationship))
                onIndexDisrupted(caseId)
            }
        }
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    private fun processCaseAttachment(caseForBlock: Case) {
        while (nextTagInBlock(CASE_ATTACHMENT_NODE)) {
            val attachmentName = parser.name!!
            val src = parser.getAttributeValue(null, CASE_PROPERTY_ATTACHMENT_SRC)
            val from = parser.getAttributeValue(null, CASE_PROPERTY_ATTACHMENT_FROM)
            val fileName = parser.getAttributeValue(null, CASE_PROPERTY_ATTACHMENT_NAME)

            if ((src == null || "" == src) && (from == null || "" == from)) {
                //this is actually an attachment removal
                removeAttachment(caseForBlock, attachmentName)
                caseForBlock.removeAttachment(attachmentName)
                continue
            }

            val reference = processAttachment(src, from, fileName, parser)
            if (reference != null) {
                caseForBlock.updateAttachment(attachmentName, reference)
            }
        }
    }

    protected open fun removeAttachment(caseForBlock: Case, attachmentName: String) {
    }

    protected open fun processAttachment(src: String?, from: String?, name: String?, parser: PlatformXmlParser): String? {
        return null
    }

    protected open fun buildCase(name: String, typeId: String): Case {
        return Case(name, typeId)
    }

    @Throws(PlatformIOException::class)
    override fun commit(parsed: Case) {
        storage().write(parsed)
    }

    protected open fun retrieve(entityId: String): Case? {
        return try {
            storage().getRecordForValue(Case.INDEX_CASE_ID, entityId) as Case
        } catch (nsee: NoSuchElementException) {
            null
        }
    }

    open fun storage(): IStorageUtilityIndexed<*> {
        return storage
    }

    override fun onIndexDisrupted(caseId: String) {
    }

    protected open fun onCaseCreateUpdate(caseId: String) {
    }
}
