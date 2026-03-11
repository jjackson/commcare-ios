package org.commcare.xml

import org.commcare.suite.model.AssertionSet
import org.commcare.suite.model.DisplayUnit
import org.commcare.suite.model.Entry
import org.commcare.suite.model.FormEntry
import org.commcare.suite.model.PostRequest
import org.commcare.suite.model.QueryData
import org.commcare.suite.model.RemoteRequestEntry
import org.commcare.suite.model.SessionDatum
import org.commcare.suite.model.StackOperation
import org.commcare.suite.model.ValueQueryData
import org.commcare.suite.model.ViewEntry
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.ExternalDataInstance
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException
import org.javarosa.xml.PlatformXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import java.net.MalformedURLException
import java.net.URL
import kotlin.jvm.JvmStatic

/**
 * @author ctsims
 */
class EntryParser private constructor(
    parser: PlatformXmlParser,
    private val parserBlockTag: String
) : CommCareElementParser<Entry>(parser) {

    companion object {
        private const val FORM_ENTRY_TAG = "entry"
        private const val VIEW_ENTRY_TAG = "view"
        const val REMOTE_REQUEST_TAG: String = "remote-request"

        @JvmStatic
        fun buildViewParser(parser: PlatformXmlParser): EntryParser {
            return EntryParser(parser, VIEW_ENTRY_TAG)
        }

        @JvmStatic
        fun buildEntryParser(parser: PlatformXmlParser): EntryParser {
            return EntryParser(parser, FORM_ENTRY_TAG)
        }

        @JvmStatic
        fun buildRemoteSyncParser(parser: PlatformXmlParser): EntryParser {
            return EntryParser(parser, REMOTE_REQUEST_TAG)
        }
    }

    @Throws(
        InvalidStructureException::class, PlatformIOException::class,
        PlatformXmlParserException::class, UnfullfilledRequirementsException::class
    )
    override fun parse(): Entry {
        this.checkNode(parserBlockTag)

        var xFormNamespace: String? = null
        val data = ArrayList<SessionDatum>()
        val instances = HashMap<String, DataInstance<*>>()

        var commandId = ""
        var display: DisplayUnit? = null
        val stackOps = ArrayList<StackOperation>()
        var assertions: AssertionSet? = null
        var post: PostRequest? = null

        while (nextTagInBlock(parserBlockTag)) {
            val tagName = parser.getName()
            if ("form" == tagName) {
                if (parserBlockTag == VIEW_ENTRY_TAG) {
                    throw InvalidStructureException("<$parserBlockTag>'s cannot specify XForms!!", parser)
                }
                xFormNamespace = parser.nextText()
            } else if ("command" == tagName) {
                commandId = parser.getAttributeValue(null, "id")!!
                display = parseCommandDisplay()
            } else if ("instance" == tagName!!.lowercase()) {
                ParseInstance.parseInstance(instances, parser)
            } else if ("session" == tagName) {
                parseSessionData(data)
            } else if ("entity" == tagName || "details" == tagName) {
                throw InvalidStructureException(
                    "Incompatible CaseXML 1.0 elements detected in <$parserBlockTag>. " +
                            "$tagName is not a valid construct in 2.0 CaseXML", parser
                )
            } else if ("stack" == tagName) {
                parseStack(stackOps)
            } else if ("assertions" == tagName) {
                assertions = AssertionSetParser(parser).parse()
            } else if ("post" == tagName) {
                post = parsePost()
            }
        }

        if (display == null) {
            throw InvalidStructureException("<entry> block must define display text details", parser)
        }

        //The server side wasn't generating <view> blocks correctly for a long time, so if we have
        //an entry with no xmlns and no operations, we'll consider that a view.
        val isViewEntry = VIEW_ENTRY_TAG == parserBlockTag ||
                (FORM_ENTRY_TAG == parserBlockTag &&
                        xFormNamespace == null &&
                        stackOps.size == 0)

        if (isViewEntry) {
            return ViewEntry(commandId, display, data, instances, stackOps, assertions)
        } else if (FORM_ENTRY_TAG == parserBlockTag) {
            return FormEntry(commandId, display, data, xFormNamespace, instances, stackOps, assertions, post)
        } else if (REMOTE_REQUEST_TAG == parserBlockTag) {
            if (post == null) {
                throw RuntimeException("$REMOTE_REQUEST_TAG must contain a <post> element")
            } else {
                return RemoteRequestEntry(commandId, display, data, instances, stackOps, assertions, post)
            }
        }

        throw RuntimeException("Misconfigured entry parser with unsupported '$parserBlockTag' tag.")
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    private fun parseCommandDisplay(): DisplayUnit? {
        parser.nextTag()
        var display: DisplayUnit? = null
        val tagName = parser.getName()
        if ("text" == tagName) {
            display = DisplayUnit(TextParser(parser).parse())
        } else if ("display" == tagName) {
            display = parseDisplayBlock()
            //check that we have text to display;
            if (display.getText() == null) {
                throw InvalidStructureException("Expected CommandText in Display block", parser)
            }
        }
        return display
    }

    @Throws(
        InvalidStructureException::class, PlatformIOException::class,
        PlatformXmlParserException::class, UnfullfilledRequirementsException::class
    )
    private fun parseSessionData(data: ArrayList<SessionDatum>) {
        while (nextTagInBlock("session")) {
            val datumParser = SessionDatumParser(this.parser)
            data.add(datumParser.parse())
        }
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    private fun parseStack(stackOps: ArrayList<StackOperation>) {
        val sop = StackOpParser(parser)
        while (this.nextTagInBlock(StackOpParser.NAME_STACK)) {
            stackOps.add(sop.parse())
        }
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    private fun parsePost(): PostRequest {
        val urlString = parser.getAttributeValue(null, "url")
            ?: throw InvalidStructureException(
                "Expected 'url' attribute in a <post> structure.",
                parser
            )
        val url: URL
        try {
            url = URL(urlString)
        } catch (e: MalformedURLException) {
            throw InvalidStructureException(
                "The <post> block's 'url' attribute ($urlString) isn't a valid url.",
                parser
            )
        }

        var relevantExpr: XPathExpression? = null
        val relevantExprString = parser.getAttributeValue(null, "relevant")
        if (relevantExprString != null) {
            try {
                relevantExpr = XPathParseTool.parseXPath(relevantExprString)
            } catch (e: XPathSyntaxException) {
                val messageBase = "'relevant' doesn't contain a valid xpath expression: "
                throw InvalidStructureException(messageBase + relevantExprString, parser)
            }
        }

        val postData = ArrayList<QueryData>()
        while (nextTagInBlock("post")) {
            postData.add(QueryDataParser(parser).parse())
        }
        return PostRequest(url, relevantExpr, postData)
    }
}
