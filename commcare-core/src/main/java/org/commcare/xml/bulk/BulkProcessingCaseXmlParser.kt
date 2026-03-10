package org.commcare.xml.bulk

import org.commcare.cases.model.Case
import org.commcare.xml.CaseIndexChangeListener
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
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_OWNER_ID
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_STATE
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_USER_ID
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_UPDATE_NODE
import org.commcare.xml.CaseXmlParserUtil.Companion.getTrimmedElementTextOrBlank
import org.commcare.xml.CaseXmlParserUtil.Companion.indexCase
import org.commcare.xml.CaseXmlParserUtil.Companion.validateMandatoryProperty
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.util.externalizable.SerializationLimitationException
import org.javarosa.xml.util.InvalidStructureException
import org.kxml2.io.KXmlParser

import java.util.Date
import java.util.LinkedHashMap

/**
 * A parser which is capable of processing CaseXML transactions in a bulk format.
 *
 * This parser needs an implementation which can perform the "bulk" steps efficiently on the current
 * platform.
 *
 * It should be a drop-in replacement for the CaseXmlParser in the ways that it is used
 *
 * Created by ctsims on 3/14/2017.
 */
abstract class BulkProcessingCaseXmlParser(parser: KXmlParser) :
    BulkElementParser<Case>(parser), CaseIndexChangeListener {

    override fun requestModelReadsForElement(
        bufferedTreeElement: TreeElement,
        currentBulkReadSet: MutableSet<String>
    ) {
        val caseId = bufferedTreeElement.getAttributeValue(null, "case_id")
        currentBulkReadSet.add(caseId!!)
    }

    @Throws(InvalidStructureException::class)
    override fun preParseValidate() {
        checkNode(CASE_NODE)
    }

    @Throws(InvalidStructureException::class)
    override fun processBufferedElement(
        bufferedTreeElement: TreeElement,
        currentOperatingSet: MutableMap<String, Case>,
        writeLog: LinkedHashMap<String, Case>
    ) {
        val caseId = bufferedTreeElement.getAttributeValue(null, CASE_PROPERTY_CASE_ID)
        validateMandatoryProperty(CASE_PROPERTY_CASE_ID, caseId, "", parser)

        val dateModified = bufferedTreeElement.getAttributeValue(null, CASE_PROPERTY_DATE_MODIFIED)
        validateMandatoryProperty(CASE_PROPERTY_DATE_MODIFIED, dateModified, caseId!!, parser)
        val modified = DateUtils.parseDateTime(dateModified!!)

        val userId = parser.getAttributeValue(null, CASE_PROPERTY_USER_ID)

        var caseForBlock: Case? = null
        var isCreateOrUpdate = false

        for (i in 0 until bufferedTreeElement.getNumChildren()) {
            val subElement = bufferedTreeElement.getChildAt(i)!!
            val action = subElement.getName()!!.lowercase()
            when (action) {
                CASE_CREATE_NODE -> {
                    caseForBlock = createCase(subElement, currentOperatingSet, caseId, modified, userId)
                    isCreateOrUpdate = true
                }
                CASE_UPDATE_NODE -> {
                    caseForBlock = loadCase(caseForBlock, caseId, currentOperatingSet, true)
                    updateCase(subElement, caseForBlock!!, caseId)
                    isCreateOrUpdate = true
                }
                CASE_CLOSE_NODE -> {
                    caseForBlock = loadCase(caseForBlock, caseId, currentOperatingSet, true)
                    closeCase(caseForBlock!!, caseId)
                }
                CASE_INDEX_NODE -> {
                    caseForBlock = loadCase(caseForBlock, caseId, currentOperatingSet, false)
                    indexCase(subElement, caseForBlock!!, caseId, this)
                }
                CASE_ATTACHMENT_NODE -> {
                    caseForBlock = loadCase(caseForBlock, caseId, currentOperatingSet, false)
                    processCaseAttachment(subElement, caseForBlock!!)
                }
            }
        }

        if (caseForBlock != null) {
            caseForBlock.setLastModified(modified!!)

            try {
                writeLog[caseForBlock.getCaseId()!!] = caseForBlock
                currentOperatingSet[caseForBlock.getCaseId()!!] = caseForBlock
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
    }

    @Throws(InvalidStructureException::class)
    private fun createCase(
        createElement: TreeElement,
        currentOperatingSet: MutableMap<String, Case>,
        caseId: String,
        modified: Date?,
        userId: String?
    ): Case {
        val data = arrayOfNulls<String>(3)

        for (i in 0 until createElement.getNumChildren()) {
            val subElement = createElement.getChildAt(i)!!
            val tag = subElement.getName()
            when (tag) {
                CASE_PROPERTY_CASE_TYPE -> data[0] = getTrimmedElementTextOrBlank(subElement)
                CASE_PROPERTY_OWNER_ID -> data[1] = getTrimmedElementTextOrBlank(subElement)
                CASE_PROPERTY_CASE_NAME -> data[2] = getTrimmedElementTextOrBlank(subElement)
                else -> throw InvalidStructureException(
                    "Expected one of [case_type, owner_id, case_name], found $tag"
                )
            }
        }

        if (data[0] == null || data[2] == null) {
            throw InvalidStructureException(
                "One of [case_type, case_name] is missing for case <create> with ID: $caseId"
            )
        }

        var caseForBlock = currentOperatingSet[caseId]

        if (caseForBlock != null) {
            caseForBlock.setName(data[2])
            caseForBlock.setTypeId(data[0])
        }

        if (caseForBlock == null) {
            // The case is either not present on the phone, or we're on strict tolerance
            caseForBlock = buildCase(data[2], data[0])
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
                "One of [user_id, owner_id] is missing for case <create> with ID: $caseId",
                parser
            )
        }
        return caseForBlock
    }

    protected open fun buildCase(name: String?, typeId: String?): Case {
        return Case(name, typeId)
    }

    @Throws(InvalidStructureException::class)
    private fun loadCase(
        caseForBlock: Case?,
        caseId: String,
        currentOperatingSet: Map<String, Case>,
        errorIfMissing: Boolean
    ): Case? {
        var result = caseForBlock
        if (result == null) {
            result = currentOperatingSet[caseId]
        }
        if (errorIfMissing && result == null) {
            throw InvalidStructureException(
                "Unable to update or close case $caseId, it wasn't found"
            )
        }
        return result
    }

    private fun updateCase(
        updateElement: TreeElement,
        caseForBlock: Case,
        caseId: String
    ) {
        for (i in 0 until updateElement.getNumChildren()) {
            val subElement = updateElement.getChildAt(i)!!

            val key = subElement.getName()!!
            val value = getTrimmedElementTextOrBlank(subElement)

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
    }

    private fun closeCase(caseForBlock: Case, caseId: String) {
        caseForBlock.setClosed(true)
        //this used to insist on a write happening _right here_. Not sure exactly why. Maybe related
        //to other writes happening in the same restore?
        onIndexDisrupted(caseId)
    }

    override fun onIndexDisrupted(caseId: String) {
    }

    protected open fun onCaseCreateUpdate(caseId: String) {
    }

    //These are unlikely to be used, and likely need to be refactored still a bit

    private fun processCaseAttachment(attachmentElement: TreeElement, caseForBlock: Case) {
        for (i in 0 until attachmentElement.getNumChildren()) {
            val subElement = attachmentElement.getChildAt(i)!!

            val attachmentName = subElement.getName()!!
            val src = subElement.getAttributeValue(null, CASE_PROPERTY_ATTACHMENT_SRC)
            val from = subElement.getAttributeValue(null, CASE_PROPERTY_ATTACHMENT_FROM)
            val fileName = subElement.getAttributeValue(null, CASE_PROPERTY_ATTACHMENT_NAME)

            if ((src == null || "" == src) && (from == null || "" == from)) {
                //this is actually an attachment removal
                removeAttachment(caseForBlock, attachmentName)
                caseForBlock.removeAttachment(attachmentName)
                continue
            }

            val reference = processAttachment(src, from, fileName)
            if (reference != null) {
                caseForBlock.updateAttachment(attachmentName, reference!!)
            }
        }
    }

    protected open fun removeAttachment(caseForBlock: Case, attachmentName: String) {
        throw RuntimeException("Attachment processing not available for bulk reads")
    }

    protected open fun processAttachment(src: String?, from: String?, name: String?): String? {
        throw RuntimeException("Attachment processing not available for bulk reads")
    }
}
