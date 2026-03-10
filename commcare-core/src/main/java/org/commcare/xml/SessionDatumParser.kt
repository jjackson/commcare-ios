package org.commcare.xml

import org.commcare.cases.util.StringUtils
import org.commcare.suite.model.ComputedDatum
import org.commcare.suite.model.EntityDatum
import org.commcare.suite.model.FormIdDatum
import org.commcare.suite.model.MultiSelectEntityDatum
import org.commcare.suite.model.QueryData
import org.commcare.suite.model.QueryGroup
import org.commcare.suite.model.QueryPrompt
import org.commcare.suite.model.RemoteQueryDatum
import org.commcare.suite.model.SessionDatum
import org.commcare.suite.model.Text
import org.javarosa.core.util.OrderedHashtable
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import java.net.MalformedURLException
import java.net.URL
import java.util.Hashtable

/**
 * @author ctsims
 */
class SessionDatumParser(parser: KXmlParser) : CommCareElementParser<SessionDatum>(parser) {

    companion object {
        const val DEFAULT_MAX_SELECT_VAL: Int = 100
    }

    @Throws(
        InvalidStructureException::class, PlatformIOException::class,
        XmlPullParserException::class, UnfullfilledRequirementsException::class
    )
    override fun parse(): SessionDatum {
        val name = parser.name
        if ("query" == name) {
            return parseRemoteQueryDatum()
        }

        if ("datum" != name && "form" != name && "instance-datum" != name) {
            throw InvalidStructureException(
                "Expected one of <instance-datum>, <datum> or <form> data in <session> block,"
                        + " instead found "
                        + this.parser.name + ">", this.parser
            )
        }

        val id = parser.getAttributeValue(null, "id")

        val calculate = parser.getAttributeValue(null, "function")

        val datum: SessionDatum
        if (calculate == null) {
            val nodeset = parser.getAttributeValue(null, "nodeset")
            val shortDetail = parser.getAttributeValue(null, "detail-select")
            val longDetail = parser.getAttributeValue(null, "detail-confirm")
            val inlineDetail = parser.getAttributeValue(null, "detail-inline")
            val persistentDetail = parser.getAttributeValue(null, "detail-persistent")
            val value = parser.getAttributeValue(null, "value")
            val autoselect = parser.getAttributeValue(null, "autoselect")
            val maxSelectValueStr = parser.getAttributeValue(null, "max-select-value")
            var maxSelectValue = DEFAULT_MAX_SELECT_VAL
            if (!StringUtils.isEmpty(maxSelectValueStr)) {
                try {
                    maxSelectValue = Integer.parseInt(maxSelectValueStr)
                } catch (e: NumberFormatException) {
                    throw InvalidStructureException(
                        "Invalid value $maxSelectValueStr"
                                + "for max-select-value. Must be an Integer", this.parser
                    )
                }
            }

            if (nodeset == null) {
                throw InvalidStructureException(
                    "Expected @nodeset in $id <datum> definition", this.parser
                )
            }

            datum = if ("instance-datum" == name) {
                MultiSelectEntityDatum(
                    id, nodeset, shortDetail, longDetail, inlineDetail,
                    persistentDetail, value, autoselect, maxSelectValue
                )
            } else {
                EntityDatum(
                    id, nodeset, shortDetail, longDetail, inlineDetail,
                    persistentDetail, value, autoselect
                )
            }
        } else {
            datum = if ("form" == this.parser.name) {
                FormIdDatum(calculate)
            } else {
                ComputedDatum(id, calculate)
            }
        }

        while (parser.next() == KXmlParser.TEXT) {
            // consume text nodes
        }

        return datum
    }

    @Throws(
        InvalidStructureException::class, PlatformIOException::class,
        XmlPullParserException::class, UnfullfilledRequirementsException::class
    )
    private fun parseRemoteQueryDatum(): RemoteQueryDatum {
        val userQueryPrompts = OrderedHashtable<String, QueryPrompt>()
        this.checkNode("query")

        val xpathTemplateType = parser.getAttributeValue(null, "template")
        val useCaseTemplate = "case" == xpathTemplateType

        val queryUrlString = parser.getAttributeValue(null, "url")
        val queryResultStorageInstance = parser.getAttributeValue(null, "storage-instance")
        if (queryUrlString == null || queryResultStorageInstance == null) {
            val errorMsg = "<query> element missing 'url' or 'storage-instance' attribute"
            throw InvalidStructureException(errorMsg, parser)
        }
        val queryUrl: URL
        try {
            queryUrl = URL(queryUrlString)
        } catch (e: MalformedURLException) {
            val errorMsg = "<query> element has invalid 'url' attribute ($queryUrlString)."
            throw InvalidStructureException(errorMsg, parser)
        }

        val defaultSearch = "true" == parser.getAttributeValue(null, "default_search")
        val dynamicSearch = "true" == parser.getAttributeValue(null, "dynamic_search")
        val searchOnClear = "true" == parser.getAttributeValue(null, "search_on_clear")
        var title: Text? = null
        var description: Text? = null
        val groupPrompts = Hashtable<String, QueryGroup>()
        val hiddenQueryValues = ArrayList<QueryData>()
        while (nextTagInBlock("query")) {
            val tagName = parser.name
            if ("data" == tagName) {
                hiddenQueryValues.add(QueryDataParser(parser).parse())
            } else if ("prompt" == tagName) {
                val key = parser.getAttributeValue(null, "key")
                userQueryPrompts[key] = QueryPromptParser(parser).parse()
            } else if ("title" == tagName) {
                nextTagInBlock("title")
                title = TextParser(parser).parse()
            } else if ("description" == tagName) {
                nextTagInBlock("description")
                description = TextParser(parser).parse()
            } else if (QueryGroupParser.NAME_GROUP == tagName) {
                val queryGroup = QueryGroupParser(parser).parse()
                groupPrompts[queryGroup.getKey()] = queryGroup
            }
        }
        return RemoteQueryDatum(
            queryUrl, queryResultStorageInstance, hiddenQueryValues,
            userQueryPrompts, useCaseTemplate, defaultSearch, dynamicSearch, title, description,
            groupPrompts, searchOnClear
        )
    }
}
