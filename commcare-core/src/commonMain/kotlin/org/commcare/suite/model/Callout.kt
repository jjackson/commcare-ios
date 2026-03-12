package org.commcare.suite.model

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.parser.XPathSyntaxException

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Application callout described in suite.xml
 * Used in callouts from EntitySelectActivity and EntityDetailActivity
 *
 * @author wpride1 on 4/14/15.
 */
class Callout : Externalizable, DetailTemplate {
    private var actionName: String? = null
    private var image: String? = null
    private var displayName: String? = null
    private var type: String? = null
    private var extras: MutableMap<String, String>? = null
    private var responses: ArrayList<String>? = null
    private var isAutoLaunching: Boolean = false
    private var assumePlainTextValues: Boolean = false

    /**
     * Allows case list intent callouts to map result data to cases. 'header'
     * is the column header text and 'template' is the key used for mapping a
     * callout result data point to a case, should usually be the case id.
     */
    internal var responseDetailField: DetailField? = null

    /**
     * For externalization
     */
    constructor()

    constructor(
        actionName: String?, image: String?, displayName: String?,
        extras: HashMap<String, String>?, responses: ArrayList<String>?,
        responseDetail: DetailField?, type: String?, isAutoLaunching: Boolean
    ) {
        this.actionName = actionName
        this.image = image
        this.displayName = displayName
        this.extras = extras
        this.responses = responses
        this.responseDetailField = responseDetail
        this.type = type
        this.isAutoLaunching = isAutoLaunching
        this.assumePlainTextValues = false
    }

    override fun evaluate(context: EvaluationContext?): CalloutData {
        val evaluatedExtras = HashMap<String, String>()
        val forceXpathParsing = forceXpathParsing()
        val keys = extras!!.keys.iterator()
        while (keys.hasNext()) {
            val key = keys.next() as String
            if (!key.contentEquals(KEY_FORCE_XPATH_PARSING)) {
                val rawValue = extras!![key]
                if (assumePlainTextValues && !forceXpathParsing) {
                    if (rawValue != null) evaluatedExtras[key] = rawValue
                } else {
                    try {
                        val evaluatedValue =
                            FunctionUtils.toString(XPathParseTool.parseXPath(rawValue!!)!!.eval(context!!))
                        evaluatedExtras[key] = evaluatedValue
                    } catch (e: XPathSyntaxException) {
                        // do nothing
                    }
                }
            }
        }

        // emit a CalloutData with the extras evaluated. used for the detail screen.
        return CalloutData(actionName, image, displayName, evaluatedExtras, responses!!, type)
    }

    // Returns true if force_xpath_parsing is yes
    private fun forceXpathParsing(): Boolean {
        val keys = extras!!.keys.iterator()
        while (keys.hasNext()) {
            val key = keys.next() as String
            if (key.contentEquals(KEY_FORCE_XPATH_PARSING)) {
                val forceXpathVal = extras!![key]
                return forceXpathVal!!.contentEquals(KEY_FORCE_XPATH_PARSING_VALUE_TRUE)
            }
        }
        return false
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        displayName = SerializationHelpers.readString(`in`)
        actionName = SerializationHelpers.readNullableString(`in`, pf)
        image = SerializationHelpers.readNullableString(`in`, pf)
        extras = SerializationHelpers.readStringStringMap(`in`)
        responses = SerializationHelpers.readStringList(`in`)
        responseDetailField = SerializationHelpers.readNullableExternalizable(`in`, pf) { DetailField() }
        type = SerializationHelpers.readNullableString(`in`, pf)
        isAutoLaunching = SerializationHelpers.readBool(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeString(out, displayName!!)
        SerializationHelpers.writeNullable(out, actionName)
        SerializationHelpers.writeNullable(out, image)
        SerializationHelpers.writeMap(out, extras!!)
        SerializationHelpers.writeList(out, responses!!)
        SerializationHelpers.writeNullable(out, responseDetailField)
        SerializationHelpers.writeNullable(out, type)
        SerializationHelpers.writeBool(out, isAutoLaunching)
    }

    fun getImage(): String? = image

    fun getActionName(): String? = actionName

    fun getDisplayName(): String? = displayName

    fun getExtras(): MutableMap<String, String>? = extras

    fun getResponses(): ArrayList<String>? = responses

    fun getResponseDetailField(): DetailField? = responseDetailField

    fun isAutoLaunching(): Boolean = isAutoLaunching

    fun isSimprintsCallout(): Boolean = "com.simprints.id.IDENTIFY" == actionName

    fun setAssumePlainTextValues() {
        this.assumePlainTextValues = true
    }

    companion object {
        private const val KEY_FORCE_XPATH_PARSING = "force_xpath_parsing"
        private const val KEY_FORCE_XPATH_PARSING_VALUE_TRUE = "yes"
    }
}
