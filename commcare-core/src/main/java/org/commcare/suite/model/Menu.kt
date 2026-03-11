package org.commcare.suite.model

import io.reactivex.Single
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.utils.InstanceUtils
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.ExtWrapMap
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * A Menu definition describes the structure of how
 * actions should be provided to the user in a CommCare
 * application.
 *
 * @author ctsims
 */
class Menu : Externalizable, MenuDisplayable {

    private var display: DisplayUnit? = null
    private var commandIds: ArrayList<String>? = null
    private var commandExprs: Array<String?>? = null
    private var _id: String? = null
    private var root: String? = null
    private var rawRelevance: String? = null
    private var style: String? = null
    private var relevance: XPathExpression? = null
    @JvmField
    internal var assertions: AssertionSet? = null
    @JvmField
    internal var instances: HashMap<String, DataInstance<*>>? = null

    /**
     * Serialization only!!!
     */
    constructor()

    constructor(
        id: String?, root: String?, rawRelevance: String?,
        relevance: XPathExpression?, display: DisplayUnit?,
        commandIds: ArrayList<String>?, commandExprs: Array<String?>?,
        style: String?, assertions: AssertionSet?,
        instances: HashMap<String, DataInstance<*>>?
    ) {
        this._id = id
        this.root = root
        this.rawRelevance = rawRelevance
        this.relevance = relevance
        this.display = display
        this.commandIds = commandIds
        this.commandExprs = commandExprs
        this.style = style
        this.assertions = assertions
        this.instances = instances
    }

    /**
     * @return The ID of what menu an option to navigate to
     * this menu should be displayed in.
     */
    fun getRoot(): String? = root

    /**
     * @return A Text which should be displayed to the user as
     * the action which will display this menu.
     */
    fun getName(): Text? = display?.getText()

    /**
     * @return The ID of this menu. If this value is "root"
     * many CommCare applications will support displaying this
     * menu's options at the app home screen
     */
    fun getId(): String? = _id

    /**
     * @return A parsed XPath expression that determines
     * whether or not to display this menu.
     */
    @Throws(XPathSyntaxException::class)
    fun getMenuRelevance(): XPathExpression? {
        if (relevance == null && rawRelevance != null) {
            val rr = rawRelevance!!
            relevance = XPathParseTool.parseXPath(rr)
        }
        return relevance
    }

    /**
     * @return A string representing an XPath expression to determine
     * whether or not to display this menu.
     */
    fun getMenuRelevanceRaw(): String? = rawRelevance

    /**
     * @return The ID of what command actions should be available
     * when viewing this menu.
     */
    fun getCommandIds(): ArrayList<String> = commandIds!!

    @Throws(XPathSyntaxException::class)
    fun getCommandRelevance(index: Int): XPathExpression? {
        // Don't cache this for now at all
        return if (commandExprs!![index] == null) null else XPathParseTool.parseXPath(commandExprs!![index]!!)
    }

    fun getInstances(instancesToInclude: Set<String>?): HashMap<String, DataInstance<*>> {
        return InstanceUtils.getLimitedInstances(instancesToInclude, instances)
    }

    fun getAssertions(): AssertionSet {
        return if (assertions == null) AssertionSet(ArrayList<String>(), ArrayList<Text>()) else assertions!!
    }

    /**
     * @return an optional string indicating how this menu wants to display its items
     */
    fun getStyle(): String? = style

    /**
     * @return the raw xpath string for a relevant condition (if available). Largely for
     * displaying to the user in the event of a failure
     */
    fun getCommandRelevanceRaw(index: Int): String? = commandExprs!![index]

    @Suppress("UNCHECKED_CAST")
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        _id = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        root = ExtUtil.readString(`in`)
        rawRelevance = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        display = ExtUtil.read(`in`, DisplayUnit::class.java, pf) as DisplayUnit
        commandIds = ExtUtil.read(`in`, ExtWrapList(String::class.java), pf) as ArrayList<String>
        instances = ExtUtil.read(`in`, ExtWrapMap(String::class.java, ExtWrapTagged()), pf) as HashMap<String, DataInstance<*>>
        commandExprs = arrayOfNulls(ExtUtil.readInt(`in`))
        for (i in commandExprs!!.indices) {
            if (ExtUtil.readBool(`in`)) {
                commandExprs!![i] = ExtUtil.readString(`in`)
            }
        }
        style = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        assertions = ExtUtil.read(`in`, ExtWrapNullable(AssertionSet::class.java), pf) as AssertionSet?
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(_id))
        ExtUtil.writeString(out, root)
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(rawRelevance))
        ExtUtil.write(out, display)
        ExtUtil.write(out, ExtWrapList(commandIds!!))
        ExtUtil.write(out, ExtWrapMap(instances!!, ExtWrapTagged()))
        ExtUtil.writeNumeric(out, commandExprs!!.size.toLong())
        for (commandExpr in commandExprs!!) {
            if (commandExpr == null) {
                ExtUtil.writeBool(out, false)
            } else {
                ExtUtil.writeBool(out, true)
                ExtUtil.writeString(out, commandExpr)
            }
        }
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(style))
        ExtUtil.write(out, ExtWrapNullable(assertions))
    }

    override fun getImageURI(): String? {
        val imageUri = display?.getImageURI() ?: return null
        return imageUri.evaluate()
    }

    override fun getAudioURI(): String? {
        val audioUri = display?.getAudioURI() ?: return null
        return audioUri.evaluate()
    }

    override fun getDisplayText(ec: EvaluationContext?): String? {
        val text = display?.getText() ?: return null
        return text.evaluate(ec)
    }

    override fun getTextForBadge(ec: EvaluationContext?): Single<String> {
        val badgeText = display?.getBadgeText() ?: return Single.just("")
        return badgeText.getDisposableSingleForEvaluation(ec)
    }

    override fun getRawBadgeTextObject(): Text? = display?.getBadgeText()

    override fun getRawText(): Text? = display?.getText()

    override fun getCommandID(): String? = _id

    // unsafe! assumes that xpath expressions evaluate properly...
    fun indexOfCommand(cmd: String?): Int = commandIds!!.indexOf(cmd)

    override fun toString(): String {
        return "Menu with id " + this.getId()
    }

    companion object {
        const val ROOT_MENU_ID = "root"
        const val TRAINING_MENU_ROOT = "training-root"
    }
}
