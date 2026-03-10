package org.commcare.xml

import org.commcare.suite.model.DisplayUnit
import org.commcare.suite.model.QueryPrompt
import org.commcare.suite.model.QueryPromptCondition
import org.commcare.suite.model.Text
import org.javarosa.core.model.ItemsetBinding
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.xform.parse.ItemSetParsingUtils
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException
import org.kxml2.io.KXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException

class QueryPromptParser(parser: KXmlParser) : CommCareElementParser<QueryPrompt>(parser) {

    companion object {
        private const val NAME_PROMPT = "prompt"
        private const val NAME_DISPLAY = "display"
        private const val NAME_ITEMSET = "itemset"
        private const val NAME_VALIDATION = "validation"
        private const val NAME_REQUIRED = "required"
        private const val ATTR_APPEARANCE = "appearance"
        private const val ATTR_KEY = "key"
        private const val ATTR_INPUT = "input"
        private const val ATTR_RECEIVE = "receive"
        private const val ATTR_HIDDEN = "hidden"
        private const val ATTR_NODESET = "nodeset"
        private const val ATTR_DEFAULT = "default"
        private const val NAME_LABEL = "label"
        private const val NAME_VALUE = "value"
        private const val NAME_SORT = "sort"
        private const val ATTR_REF = "ref"
        private const val ATTR_ALLOW_BLANK_VALUE = "allow_blank_value"
        private const val ATTR_EXCLUDE = "exclude"
        private const val ATTR_REQUIRED = "required"
        private const val ATTR_VALIDATION_TEST = "test"
        private const val NAME_TEXT = "text"
        private const val ATTR_GROUP_KEY = "group_key"
    }

    @Throws(
        InvalidStructureException::class, PlatformIOException::class,
        PlatformXmlParserException::class, UnfullfilledRequirementsException::class
    )
    override fun parse(): QueryPrompt {
        val appearance = parser.getAttributeValue(null, ATTR_APPEARANCE)
        val key = parser.getAttributeValue(null, ATTR_KEY)
        val input = parser.getAttributeValue(null, ATTR_INPUT)
        val receive = parser.getAttributeValue(null, ATTR_RECEIVE)
        val hidden = parser.getAttributeValue(null, ATTR_HIDDEN)
        val allowBlankValue = "true" == parser.getAttributeValue(null, ATTR_ALLOW_BLANK_VALUE)
        var display: DisplayUnit? = null
        var itemsetBinding: ItemsetBinding? = null
        val defaultValueString = parser.getAttributeValue(null, ATTR_DEFAULT)
        val defaultValue = xpathPropertyValue(defaultValueString)
        val excludeValueString = parser.getAttributeValue(null, ATTR_EXCLUDE)
        val exclude = xpathPropertyValue(excludeValueString)
        val groupKey = parser.getAttributeValue(null, ATTR_GROUP_KEY)
        val oldRequired = xpathPropertyValue(parser.getAttributeValue(null, ATTR_REQUIRED))

        var validation: QueryPromptCondition? = null
        var required: QueryPromptCondition? = null
        while (nextTagInBlock(NAME_PROMPT)) {
            if (NAME_DISPLAY.equals(parser.name, ignoreCase = true)) {
                display = parseDisplayBlock()
            } else if (NAME_ITEMSET.equals(parser.name, ignoreCase = true)) {
                itemsetBinding = parseItemset()
            } else if (NAME_VALIDATION.equals(parser.name, ignoreCase = true)) {
                validation = parseValidationBlock(key)
            } else if (NAME_REQUIRED.equals(parser.name, ignoreCase = true)) {
                if (oldRequired != null) {
                    throw InvalidStructureException(
                        "Both required attribute and <required> node present for prompt $key"
                    )
                }
                required = parseRequiredBlock(key)
            }
        }

        if (oldRequired != null) {
            required = QueryPromptCondition(oldRequired, null)
        }

        return QueryPrompt(
            key, appearance, input, receive, hidden, display,
            itemsetBinding, defaultValue, allowBlankValue, exclude,
            required, validation, groupKey
        )
    }

    @Throws(InvalidStructureException::class, PlatformXmlParserException::class, PlatformIOException::class)
    private fun parseRequiredBlock(key: String?): QueryPromptCondition {
        val testStr = parser.getAttributeValue(null, ATTR_VALIDATION_TEST)
            ?: throw InvalidStructureException("No test condition defined in <required> for prompt $key")
        val test = xpathPropertyValue(testStr)
        var message: Text? = null
        while (nextTagInBlock(NAME_REQUIRED)) {
            if (parser.name == NAME_TEXT) {
                message = TextParser(parser).parse()
            } else {
                throw InvalidStructureException(
                    "Unrecognised node ${parser.name} in <required> for prompt $key"
                )
            }
        }
        return QueryPromptCondition(test, message)
    }

    @Throws(InvalidStructureException::class, PlatformXmlParserException::class, PlatformIOException::class)
    private fun parseValidationBlock(key: String?): QueryPromptCondition {
        val testStr = parser.getAttributeValue(null, ATTR_VALIDATION_TEST)
            ?: throw InvalidStructureException("No test condition defined in validation for prompt $key")
        val test = xpathPropertyValue(testStr)
        var message: Text? = null
        while (nextTagInBlock(NAME_VALIDATION)) {
            if (parser.name == NAME_TEXT) {
                message = TextParser(parser).parse()
            } else {
                throw InvalidStructureException(
                    "Unrecognised node ${parser.name}in validation for prompt $key"
                )
            }
        }
        return QueryPromptCondition(test, message)
    }

    @Throws(PlatformIOException::class, PlatformXmlParserException::class, InvalidStructureException::class)
    private fun parseItemset(): ItemsetBinding {
        val itemset = ItemsetBinding()
        itemset.contextRef = TreeReference.rootRef()
        val nodesetStr = parser.getAttributeValue(null, ATTR_NODESET)
        ItemSetParsingUtils.setNodeset(itemset, nodesetStr, NAME_ITEMSET)
        while (nextTagInBlock(NAME_ITEMSET)) {
            if (NAME_LABEL == parser.name) {
                ItemSetParsingUtils.setLabel(itemset, parser.getAttributeValue(null, ATTR_REF))
            } else if (NAME_VALUE == parser.name) {
                ItemSetParsingUtils.setValue(itemset, parser.getAttributeValue(null, ATTR_REF))
            } else if (NAME_SORT == parser.name) {
                ItemSetParsingUtils.setSort(itemset, parser.getAttributeValue(null, ATTR_REF))
            }
        }
        return itemset
    }

    @Throws(InvalidStructureException::class)
    fun xpathPropertyValue(xpath: String?): XPathExpression? {
        var propertyValue: XPathExpression? = null
        if (xpath != null) {
            try {
                propertyValue = XPathParseTool.parseXPath(xpath)
            } catch (e: XPathSyntaxException) {
                val toThrow = InvalidStructureException(
                    String.format(
                        "Invalid XPath Expression in QueryPrompt %s",
                        e.message
                    ), parser
                )
                toThrow.initCause(e)
                throw toThrow
            }
        }
        return propertyValue
    }
}
