package org.commcare.xml

import org.commcare.cases.model.Case
import org.commcare.cases.model.CaseIndex
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.xml.util.ActionableInvalidStructureException
import org.javarosa.xml.util.InvalidCasePropertyLengthException
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.PlatformXmlParser

class CaseXmlParserUtil {
    companion object {
        const val CASE_NODE: String = "case"
        const val CASE_CREATE_NODE: String = "create"
        const val CASE_UPDATE_NODE: String = "update"
        const val CASE_CLOSE_NODE: String = "close"
        const val CASE_INDEX_NODE: String = "index"
        const val CASE_ATTACHMENT_NODE: String = "attachment"
        const val CASE_PROPERTY_CASE_ID: String = "case_id"
        const val CASE_PROPERTY_CASE_TYPE: String = "case_type"
        const val CASE_PROPERTY_OWNER_ID: String = "owner_id"
        const val CASE_PROPERTY_USER_ID: String = "user_id"
        const val CASE_PROPERTY_STATUS: String = "status"
        const val CASE_PROPERTY_CASE_NAME: String = "case_name"
        const val CASE_PROPERTY_LAST_MODIFIED: String = "last_modified"
        const val CASE_PROPERTY_DATE_MODIFIED: String = "date_modified"
        const val CASE_PROPERTY_DATE_OPENED: String = "date_opened"
        const val CASE_PROPERTY_EXTERNAL_ID: String = "external_id"
        const val CASE_PROPERTY_CATEGORY: String = "category"
        const val CASE_PROPERTY_STATE: String = "state"
        const val CASE_PROPERTY_INDEX: String = "index"
        const val CASE_PROPERTY_INDEX_CASE_TYPE: String = "case_type"
        const val CASE_PROPERTY_INDEX_RELATIONSHIP: String = "relationship"
        const val CASE_PROPERTY_ATTACHMENT_SRC: String = "src"
        const val CASE_PROPERTY_ATTACHMENT_FROM: String = "from"
        const val CASE_PROPERTY_ATTACHMENT_NAME: String = "name"
        const val ATTACHMENT_FROM_LOCAL: String = "local"
        const val ATTACHMENT_FROM_REMOTE: String = "remote"
        const val CASE_XML_NAMESPACE: String = "http://commcarehq.org/case/transaction/v2"

        @JvmStatic
        @Throws(InvalidStructureException::class)
        fun validateMandatoryProperty(key: String, value: Any?, caseId: String, parser: PlatformXmlParser) {
            if (value == null || value == "") {
                val error = "The $key attribute of a <case> $caseId wasn't set"
                throw InvalidStructureException.readableInvalidStructureException(error, parser)
            }
        }

        @JvmStatic
        @Throws(InvalidStructureException::class)
        internal fun checkForMaxLength(caseForBlock: Case) {
            if (getStringLength(caseForBlock.getTypeId()) > 255) {
                throw InvalidCasePropertyLengthException(CASE_PROPERTY_CASE_TYPE)
            } else if (getStringLength(caseForBlock.getUserId()) > 255) {
                throw InvalidCasePropertyLengthException(CASE_PROPERTY_OWNER_ID)
            } else if (getStringLength(caseForBlock.getName()) > 255) {
                throw InvalidCasePropertyLengthException(CASE_PROPERTY_CASE_NAME)
            } else if (getStringLength(caseForBlock.getExternalId()) > 255) {
                throw InvalidCasePropertyLengthException(CASE_PROPERTY_EXTERNAL_ID)
            }
        }

        /**
         * Returns the length of string if it's not null, otherwise 0.
         */
        private fun getStringLength(input: String?): Int {
            return input?.length ?: 0
        }

        /**
         * Trims and returns elements value or empty string
         * @param element TreeElement we want the value for
         * @return empty string if element value is null, otherwise the trimmed value for element
         */
        @JvmStatic
        fun getTrimmedElementTextOrBlank(element: TreeElement): String {
            val v = element.getValue() ?: return ""
            return v.uncast().getString()?.trim() ?: ""
        }

        /**
         * Processes given treeElement to set case indexes
         * @param indexElement TreeElement containing the index nodes
         * @param caseForBlock Case to which indexes should be applied
         * @param caseId id of the given case
         * @param caseIndexChangeListener listener for the case index changes
         * @throws InvalidStructureException thrown when the given indexElement doesn't have valid index nodes
         */
        @JvmStatic
        @Throws(InvalidStructureException::class)
        fun indexCase(
            indexElement: TreeElement, caseForBlock: Case, caseId: String,
            caseIndexChangeListener: CaseIndexChangeListener
        ) {
            for (i in 0 until indexElement.getNumChildren()) {
                val subElement = indexElement.getChildAt(i)!!

                val indexName = subElement.getName()
                val caseType = subElement.getAttributeValue(null, CASE_PROPERTY_INDEX_CASE_TYPE)

                var value: String? = getTrimmedElementTextOrBlank(subElement)
                if (value == caseId) {
                    throw ActionableInvalidStructureException(
                        "case.error.self.index", arrayOf(caseId),
                        "Case $caseId cannot index itself"
                    )
                } else if (value == "") {
                    //Remove any ambiguity associated with empty values
                    value = null
                }

                var relationship = subElement.getAttributeValue(null, CASE_PROPERTY_INDEX_RELATIONSHIP)
                if (relationship == null) {
                    relationship = CaseIndex.RELATIONSHIP_CHILD
                } else if ("" == relationship) {
                    throw InvalidStructureException(
                        "Invalid Case Transaction for Case[$caseId]: Attempt to add a '' relationship type to " +
                                "entity[$value]"
                    )
                }

                //Process blank inputs in the same manner as data fields (IE: Remove the underlying model)
                if (value == null) {
                    if (caseForBlock.removeIndex(indexName)) {
                        caseIndexChangeListener.onIndexDisrupted(caseId)
                    }
                } else {
                    if (caseForBlock.setIndex(
                            CaseIndex(
                                indexName, caseType, value,
                                relationship
                            )
                        )
                    ) {
                        caseIndexChangeListener.onIndexDisrupted(caseId)
                    }
                }
            }
        }
    }
}
