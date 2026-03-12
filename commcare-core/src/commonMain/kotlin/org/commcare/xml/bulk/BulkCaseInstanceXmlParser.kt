package org.commcare.xml.bulk

import org.commcare.cases.model.Case
import org.commcare.modern.engine.cases.CaseIndexTable
import org.commcare.xml.CaseIndexChangeListener
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_NODE
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_CASE_ID
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_CASE_NAME
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_CASE_TYPE
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_CATEGORY
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_DATE_OPENED
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_EXTERNAL_ID
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_INDEX
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_LAST_MODIFIED
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_OWNER_ID
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_STATE
import org.commcare.xml.CaseXmlParserUtil.Companion.CASE_PROPERTY_STATUS
import org.commcare.xml.CaseXmlParserUtil.Companion.getTrimmedElementTextOrBlank
import org.commcare.xml.CaseXmlParserUtil.Companion.indexCase
import org.commcare.xml.CaseXmlParserUtil.Companion.validateMandatoryProperty
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.util.externalizable.SerializationLimitationException
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.PlatformXmlParser

import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * The BulkCaseInstanceXmlParser Parser is responsible for parsing case instance xml structure
 * as specified in https://github.com/dimagi/commcare-core/wiki/casedb#casedb-instance-structure
 */
// todo this and other case parsers duplicates a bunch of logic today that can be unified
open class BulkCaseInstanceXmlParser(
    parser: PlatformXmlParser,
    private val storage: IStorageUtilityIndexed<Case>,
    private val mCaseIndexTable: CaseIndexTable?
) : BulkElementParser<Case>(parser), CaseIndexChangeListener {

    override fun requestModelReadsForElement(
        bufferedTreeElement: TreeElement,
        currentBulkReadSet: MutableSet<String>
    ) {
        val caseId = bufferedTreeElement.getAttributeValue(null, CASE_PROPERTY_CASE_ID)
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

        val caseType = bufferedTreeElement.getAttributeValue(null, CASE_PROPERTY_CASE_TYPE)
        validateMandatoryProperty(CASE_PROPERTY_CASE_TYPE, caseType, caseId!!, parser)

        val ownerId = bufferedTreeElement.getAttributeValue(null, CASE_PROPERTY_OWNER_ID)
        validateMandatoryProperty(CASE_PROPERTY_OWNER_ID, ownerId, caseId, parser)

        val status = bufferedTreeElement.getAttributeValue(null, CASE_PROPERTY_STATUS)
        validateMandatoryProperty(CASE_PROPERTY_STATUS, status, caseId, parser)

        var caseForBlock = currentOperatingSet[caseId]
        if (caseForBlock == null) {
            caseForBlock = buildCase(null, caseType)
            caseForBlock.setCaseId(caseId)
        } else {
            caseForBlock.setTypeId(caseType)
        }
        caseForBlock.setUserId(ownerId)
        caseForBlock.setClosed(status!!.contentEquals("closed"))

        updateCase(bufferedTreeElement, caseForBlock, caseId)
        validateCase(caseForBlock)

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
    }

    @Throws(InvalidStructureException::class)
    private fun validateCase(caseForBlock: Case) {
        validateMandatoryProperty(
            CASE_PROPERTY_LAST_MODIFIED, caseForBlock.getLastModified(), caseForBlock.getCaseId()!!,
            parser
        )
        validateMandatoryProperty(
            CASE_PROPERTY_CASE_NAME, caseForBlock.getName(), caseForBlock.getCaseId()!!,
            parser
        )
    }

    protected open fun buildCase(name: String?, typeId: String?): Case {
        return Case(name, typeId)
    }

    @Throws(InvalidStructureException::class)
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
                CASE_PROPERTY_CASE_NAME -> caseForBlock.setName(value)
                CASE_PROPERTY_DATE_OPENED -> caseForBlock.setDateOpened(DateUtils.parseDate(value))
                CASE_PROPERTY_LAST_MODIFIED -> caseForBlock.setLastModified(DateUtils.parseDateTime(value)!!)
                CASE_PROPERTY_EXTERNAL_ID -> caseForBlock.setExternalId(value)
                CASE_PROPERTY_CATEGORY -> caseForBlock.setCategory(value)
                CASE_PROPERTY_STATE -> caseForBlock.setState(value)
                CASE_PROPERTY_INDEX -> indexCase(subElement, caseForBlock, caseId, this)
                else -> caseForBlock.setProperty(key, value)
            }
        }
    }

    override fun performBulkRead(
        currentBulkReadSet: Set<String>,
        currentOperatingSet: MutableMap<String, Case>
    ) {
        for (c in storage.getBulkRecordsForIndex(Case.INDEX_CASE_ID, currentBulkReadSet)) {
            currentOperatingSet[c.getCaseId()!!] = c
        }
    }

    @Throws(PlatformIOException::class)
    override fun performBulkWrite(writeLog: LinkedHashMap<String, Case>) {
        val recordIdsToWipe = ArrayList<Int>()
        for (caseId in writeLog.keys) {
            val c = writeLog[caseId]!!
            storage.write(c)
            // Add the case's SQL record ID
            recordIdsToWipe.add(c.getID())
        }
        if (mCaseIndexTable != null) {
            mCaseIndexTable.clearCaseIndices(recordIdsToWipe)
            for (cid in writeLog.keys) {
                val c = writeLog[cid]!!
                mCaseIndexTable.indexCase(c)
            }
        }
    }

    override fun onIndexDisrupted(caseId: String) {
    }
}
