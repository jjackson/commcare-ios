package org.commcare.suite.model

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.ExtWrapMap
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.parser.XPathSyntaxException

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.Hashtable
import java.util.Vector

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
    private var extras: Hashtable<String, String>? = null
    private var responses: Vector<String>? = null
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
        extras: Hashtable<String, String>?, responses: Vector<String>?,
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
        val evaluatedExtras = Hashtable<String, String>()
        val forceXpathParsing = forceXpathParsing()
        val keys = extras!!.keys()
        while (keys.hasMoreElements()) {
            val key = keys.nextElement() as String
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
        val keys = extras!!.keys()
        while (keys.hasMoreElements()) {
            val key = keys.nextElement() as String
            if (key.contentEquals(KEY_FORCE_XPATH_PARSING)) {
                val forceXpathVal = extras!![key]
                return forceXpathVal!!.contentEquals(KEY_FORCE_XPATH_PARSING_VALUE_TRUE)
            }
        }
        return false
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        displayName = ExtUtil.readString(`in`)
        actionName = ExtUtil.read(`in`, ExtWrapNullable(String::class.java), pf) as String?
        image = ExtUtil.read(`in`, ExtWrapNullable(String::class.java), pf) as String?
        extras = ExtUtil.read(`in`, ExtWrapMap(String::class.java, String::class.java), pf) as Hashtable<String, String>
        responses = ExtUtil.read(`in`, ExtWrapList(String::class.java), pf) as Vector<String>
        responseDetailField = ExtUtil.read(`in`, ExtWrapNullable(DetailField::class.java), pf) as DetailField?
        type = ExtUtil.read(`in`, ExtWrapNullable(String::class.java), pf) as String?
        isAutoLaunching = ExtUtil.readBool(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeString(out, displayName!!)
        ExtUtil.write(out, ExtWrapNullable(actionName))
        ExtUtil.write(out, ExtWrapNullable(image))
        ExtUtil.write(out, ExtWrapMap(extras!!))
        ExtUtil.write(out, ExtWrapList(responses!!))
        ExtUtil.write(out, ExtWrapNullable(responseDetailField))
        ExtUtil.write(out, ExtWrapNullable(type))
        ExtUtil.writeBool(out, isAutoLaunching)
    }

    fun getImage(): String? = image

    fun getActionName(): String? = actionName

    fun getDisplayName(): String? = displayName

    fun getExtras(): Hashtable<String, String>? = extras

    fun getResponses(): Vector<String>? = responses

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
