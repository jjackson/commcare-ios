package org.commcare.suite.model

import org.commcare.cases.entity.Entity
import org.commcare.cases.entity.EntityUtil
import org.commcare.cases.entity.NodeEntityFactory
import org.commcare.modern.util.Pair
import org.commcare.util.CollectionUtils
import org.commcare.util.DetailFieldPrintInfo
import org.commcare.util.GridCoordinate
import org.commcare.util.GridStyle
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.reference.InvalidReferenceException
import org.javarosa.core.reference.ReferenceManager
import org.javarosa.core.util.ArrayUtilities
import org.javarosa.core.util.OrderedHashtable
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.ExtWrapMap
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.model.xform.XPathReference
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.Enumeration
import java.util.HashMap
import java.util.Hashtable
import java.util.Vector

/**
 * A Detail model defines the structure in which
 * the details about something should be displayed
 * to users (generally cases or referrals).
 *
 * Detail models maintain a set of Text objects
 * which provide a template for how details about
 * objects should be displayed, along with a model
 * which defines the context of what data should be
 * obtained to fill in those templates.
 *
 * @author ctsims
 */
class Detail : Externalizable {

    // id will be null if this is a child detail / tab
    internal var id: String? = null
    private var nodeset: TreeReference? = null
    private var title: DisplayUnit? = null
    private var noItemsText: Text? = null
    private var selectText: Text? = null

    /**
     * Optional and only relevant if this detail has child details. In that
     * case, form may be 'image' or omitted.
     */
    private var titleForm: String? = null

    private lateinit var details: Array<Detail>
    internal lateinit var fields: Array<DetailField>
    internal var callout: Callout? = null
    private var variables: OrderedHashtable<String, String> = OrderedHashtable()
    private var variablesCompiled: OrderedHashtable<String, XPathExpression>? = null
    private var actions: Vector<Action> = Vector()

    // Force the activity that is showing this detail to show itself in landscape view only
    private var forceLandscapeView: Boolean = false
    private var focusFunction: XPathExpression? = null

    // A button to print this detail should be provided
    private var printEnabled: Boolean = false
    private var printTemplatePath: String? = null
    private var parsedRelevancyExpression: XPathExpression? = null
    private var global: Global? = null

    // REGION -- These fields are only used if this detail is a case tile
    private var numEntitiesToDisplayPerRow: Int = 0
    private var useUniformUnitsInCaseTile: Boolean = false
    private var _lazyLoading: Boolean = false
    val isLazyLoading: Boolean get() = _lazyLoading
    private var _cacheEnabled: Boolean = false
    val isCacheEnabled: Boolean get() = _cacheEnabled
    internal var group: DetailGroup? = null
    // ENDREGION

    /**
     * Serialization Only
     */
    constructor()

    constructor(
        id: String?, title: DisplayUnit?, noItemsText: Text?, nodeset: String?,
        detailsVector: Vector<Detail>, fieldsVector: Vector<DetailField>,
        variables: OrderedHashtable<String, String>, actions: Vector<Action>,
        callout: Callout?, fitAcross: String?, uniformUnitsString: String?,
        forceLandscape: String?, focusFunction: String?, printPathProvided: String?,
        relevancy: String?, global: Global?, group: DetailGroup?,
        lazyLoading: Boolean, cacheEnabled: Boolean, selectText: Text?
    ) {
        if (detailsVector.size > 0 && fieldsVector.size > 0) {
            throw IllegalArgumentException("A detail may contain either sub-details or fields, but not both.")
        }

        this.id = id
        this.title = title
        this.noItemsText = noItemsText
        this.selectText = selectText
        if (nodeset != null) {
            this.nodeset = XPathReference.getPathExpr(nodeset).getReference()
        }
        @Suppress("UNCHECKED_CAST")
        this.details = ArrayUtilities.copyIntoArray(detailsVector, arrayOfNulls<Detail>(detailsVector.size) as Array<Detail>)
        @Suppress("UNCHECKED_CAST")
        this.fields = ArrayUtilities.copyIntoArray(fieldsVector, arrayOfNulls<DetailField>(fieldsVector.size) as Array<DetailField>)
        this.variables = variables
        this.actions = actions
        this.callout = callout
        this.useUniformUnitsInCaseTile = "true" == uniformUnitsString
        this.forceLandscapeView = "true" == forceLandscape
        this.printEnabled = templatePathValid(printPathProvided)

        if (focusFunction != null) {
            try {
                this.focusFunction = XPathParseTool.parseXPath(focusFunction)
            } catch (e: XPathSyntaxException) {
                e.printStackTrace()
                throw RuntimeException(e.message)
            }
        }

        if (fitAcross != null) {
            try {
                this.numEntitiesToDisplayPerRow = Integer.parseInt(fitAcross)
            } catch (e: NumberFormatException) {
                numEntitiesToDisplayPerRow = 1
            }
        } else {
            numEntitiesToDisplayPerRow = 1
        }

        if (relevancy != null && "" != relevancy) {
            try {
                this.parsedRelevancyExpression = XPathParseTool.parseXPath(relevancy)
            } catch (e: XPathSyntaxException) {
                e.printStackTrace()
                throw RuntimeException(e.message)
            }
        }
        this.global = global
        this.group = group
        this._lazyLoading = lazyLoading
        this._cacheEnabled = cacheEnabled
    }

    fun getTitle(): DisplayUnit? = title

    fun getNoItemsText(): Text? = noItemsText

    fun getSelectText(): Text? = selectText

    fun getNodeset(): TreeReference? = nodeset

    fun getFields(): Array<DetailField> = fields

    fun getDetails(): Array<Detail> = details

    /**
     * Given a detail, return an array of details that will contain either
     * - all child details
     * - a single-element array containing the given detail, if it has no children
     */
    fun getFlattenedDetails(): Array<Detail> {
        return if (this.isCompound()) {
            this.getDetails()
        } else {
            arrayOf(this)
        }
    }

    fun isCompound(): Boolean = details.isNotEmpty()

    /**
     * Whether this detail is expected to be so huge in scope that
     * the platform should limit its strategy for loading it to be asynchronous
     * and cached on special keys.
     *
     * Legacy way to turn on cache and index: can be removed once we migrate to new
     * cache and index entirely
     */
    @Deprecated("Legacy way to turn on cache and index")
    fun useAsyncStrategy(): Boolean {
        for (f in fields) {
            if (f.getSortOrder() == DetailField.SORT_ORDER_CACHABLE) {
                return true
            }
        }
        return false
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        id = ExtUtil.read(`in`, ExtWrapNullable(String::class.java), pf) as String?
        title = ExtUtil.read(`in`, DisplayUnit::class.java, pf) as DisplayUnit
        noItemsText = ExtUtil.read(`in`, ExtWrapNullable(Text::class.java), pf) as Text?
        titleForm = ExtUtil.read(`in`, ExtWrapNullable(String::class.java), pf) as String?
        nodeset = ExtUtil.read(`in`, ExtWrapNullable(TreeReference::class.java), pf) as TreeReference?
        val theDetails = ExtUtil.read(`in`, ExtWrapList(Detail::class.java), pf) as Vector<Detail>
        details = arrayOfNulls<Detail>(theDetails.size) as Array<Detail>
        ArrayUtilities.copyIntoArray(theDetails, details)
        val theFields = ExtUtil.read(`in`, ExtWrapList(DetailField::class.java), pf) as Vector<DetailField>
        fields = arrayOfNulls<DetailField>(theFields.size) as Array<DetailField>
        ArrayUtilities.copyIntoArray(theFields, fields)
        variables = ExtUtil.read(`in`, ExtWrapMap(String::class.java, String::class.java, ExtWrapMap.TYPE_ORDERED), pf) as OrderedHashtable<String, String>
        actions = ExtUtil.read(`in`, ExtWrapList(Action::class.java), pf) as Vector<Action>
        callout = ExtUtil.read(`in`, ExtWrapNullable(Callout::class.java), pf) as Callout?
        forceLandscapeView = ExtUtil.readBool(`in`)
        focusFunction = ExtUtil.read(`in`, ExtWrapNullable(ExtWrapTagged()), pf) as XPathExpression?
        numEntitiesToDisplayPerRow = ExtUtil.readNumeric(`in`).toInt()
        useUniformUnitsInCaseTile = ExtUtil.readBool(`in`)
        parsedRelevancyExpression = ExtUtil.read(`in`, ExtWrapNullable(ExtWrapTagged()), pf) as XPathExpression?
        global = ExtUtil.read(`in`, ExtWrapNullable(ExtWrapTagged()), pf) as Global?
        group = ExtUtil.read(`in`, ExtWrapNullable(DetailGroup::class.java), pf) as DetailGroup?
        _lazyLoading = ExtUtil.readBool(`in`)
        selectText = ExtUtil.read(`in`, ExtWrapNullable(Text::class.java), pf) as Text?
        _cacheEnabled = ExtUtil.readBool(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.write(out, ExtWrapNullable(id))
        ExtUtil.write(out, title as Any)
        ExtUtil.write(out, ExtWrapNullable(noItemsText))
        ExtUtil.write(out, ExtWrapNullable(titleForm))
        ExtUtil.write(out, ExtWrapNullable(nodeset))
        ExtUtil.write(out, ExtWrapList(ArrayUtilities.toVector(details)))
        ExtUtil.write(out, ExtWrapList(ArrayUtilities.toVector(fields)))
        ExtUtil.write(out, ExtWrapMap(variables))
        ExtUtil.write(out, ExtWrapList(actions))
        ExtUtil.write(out, ExtWrapNullable(callout))
        ExtUtil.writeBool(out, forceLandscapeView)
        val ff = focusFunction
        ExtUtil.write(out, ExtWrapNullable(if (ff == null) null else ExtWrapTagged(ff)))
        ExtUtil.writeNumeric(out, numEntitiesToDisplayPerRow.toLong())
        ExtUtil.writeBool(out, useUniformUnitsInCaseTile)
        val pre = parsedRelevancyExpression
        ExtUtil.write(out, ExtWrapNullable(if (pre == null) null else ExtWrapTagged(pre)))
        val g = global
        ExtUtil.write(out, ExtWrapNullable(if (g == null) null else ExtWrapTagged(g)))
        ExtUtil.write(out, ExtWrapNullable(group))
        ExtUtil.writeBool(out, _lazyLoading)
        ExtUtil.write(out, ExtWrapNullable(selectText))
        ExtUtil.writeBool(out, _cacheEnabled)
    }

    val variableDeclarations: OrderedHashtable<String, XPathExpression> get() {
        if (variablesCompiled == null) {
            variablesCompiled = OrderedHashtable()
            val en: Enumeration<*> = variables.keys()
            while (en.hasMoreElements()) {
                val key = en.nextElement() as String
                // TODO: This is stupid, parse this stuff at XML Parse time.
                try {
                    variablesCompiled!!.put(key, XPathParseTool.parseXPath(variables[key]!!)!!)
                } catch (e: XPathSyntaxException) {
                    e.printStackTrace()
                    throw RuntimeException(e.message)
                }
            }
        }
        return variablesCompiled!!
    }

    fun getCustomActions(evaluationContext: EvaluationContext): Vector<Action> {
        val relevantActions = Vector<Action>()
        for (action in actions) {
            if (action.isRelevant(evaluationContext)) {
                relevantActions.addElement(action)
            }
        }
        return relevantActions
    }

    val orderedFieldIndicesForSorting: IntArray get() {
        val indices = Vector<Int>()
        val cacheAndIndexedIndices = Vector<Int>()
        var i = 0
        outer@ while (i < fields.size) {
            val order = fields[i].getSortOrder()
            if (order == -2) {
                cacheAndIndexedIndices.addElement(i)
            }
            if (order < 1) {
                i++
                continue
            }
            for (j in 0 until indices.size) {
                if (order < fields[indices.elementAt(j)].getSortOrder()) {
                    indices.insertElementAt(i, j)
                    i++
                    continue@outer
                }
            }
            // otherwise it's larger than all of the other fields.
            indices.addElement(i)
            i++
        }
        return CollectionUtils.mergeIntegerVectorsInArray(indices, cacheAndIndexedIndices)
    }

    // These are just helpers around the old structure. Shouldn't really be
    // used if avoidable

    fun getTemplateSizeHints(): Array<String?> {
        val result = arrayOfNulls<String>(fields.size)
        for (i in fields.indices) {
            result[i] = fields[i].getTemplateWidthHint()
        }
        return result
    }

    val headerForms: Array<String?> get() {
        val result = arrayOfNulls<String>(fields.size)
        for (i in fields.indices) {
            result[i] = fields[i].getHeaderForm()
        }
        return result
    }

    fun getTemplateForms(): Array<String?> {
        val result = arrayOfNulls<String>(fields.size)
        for (i in fields.indices) {
            result[i] = fields[i].getTemplateForm()
        }
        return result
    }

    fun usesEntityTileView(): Boolean {
        var usingEntityTile = false
        for (currentField in fields) {
            if (currentField.getGridX() >= 0 && currentField.getGridY() >= 0 &&
                currentField.getGridWidth() >= 0 && currentField.getGridHeight() > 0
            ) {
                usingEntityTile = true
            }
        }
        return usingEntityTile
    }

    fun shouldBeLaidOutInGrid(): Boolean {
        return numEntitiesToDisplayPerRow > 1 && usesEntityTileView()
    }

    fun getNumEntitiesToDisplayPerRow(): Int = numEntitiesToDisplayPerRow

    fun useUniformUnitsInCaseTile(): Boolean = useUniformUnitsInCaseTile

    fun forcesLandscape(): Boolean = forceLandscapeView

    fun getMaxWidthHeight(): Pair<Int, Int> {
        var maxWidth = 0
        var maxHeight = 0

        for (i in fields.indices) {
            val currentField = fields[i]
            val currentWidth = currentField.getGridX() + currentField.getGridWidth()
            val currentHeight = currentField.getGridY() + currentField.getGridHeight()
            maxWidth = if (currentWidth > maxWidth) currentWidth else maxWidth
            maxHeight = if (currentHeight > maxHeight) currentHeight else maxHeight
        }

        return Pair(maxWidth, maxHeight)
    }

    fun getGridCoordinates(): Array<GridCoordinate> {
        return Array(fields.size) { i ->
            val currentField = fields[i]
            GridCoordinate(
                currentField.getGridX(), currentField.getGridY(),
                currentField.getGridWidth(), currentField.getGridHeight()
            )
        }
    }

    fun getGridStyles(): Array<GridStyle> {
        return Array(fields.size) { i ->
            val currentField = fields[i]
            GridStyle(
                currentField.getFontSize(), currentField.getHorizontalAlign(),
                currentField.getVerticalAlign(), currentField.getCssId()
            )
        }
    }

    fun getCallout(): Callout? = callout

    fun hasSortField(): Boolean {
        for (f in fields) {
            if (f.getSortOrder() > 0) {
                return true
            }
        }
        return false
    }

    // Returns true if we should trigger any optimizations for this detail
    fun shouldOptimize(): Boolean {
        if (_cacheEnabled || _lazyLoading) {
            for (field in fields) {
                if (field.isCacheEnabled || field.isLazyLoading) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Given an evaluation context which a qualified nodeset, will populate that EC with the
     * evaluated variable values associated with this detail.
     *
     * @param ec The Evaluation Context to be used to evaluate the variable expressions and which
     *           will be populated by their result. Will be modified in place.
     */
    fun populateEvaluationContextVariables(ec: EvaluationContext) {
        val variables = variableDeclarations
        // These are actually in an ordered hashtable, so we can't just get the keyset, since it's
        // in a 1.3 hashtable equivalent
        val en: Enumeration<*> = variables.keys()
        while (en.hasMoreElements()) {
            val key = en.nextElement() as String
            ec.setVariable(key, FunctionUtils.unpack(variables[key]!!.eval(ec)))
        }
    }

    fun evaluateFocusFunction(ec: EvaluationContext): Boolean {
        if (focusFunction == null) {
            return false
        }
        val value = FunctionUtils.unpack(focusFunction!!.eval(ec))
        return FunctionUtils.toBoolean(value)
    }

    fun getFocusFunction(): XPathExpression? = focusFunction

    private fun templatePathValid(templatePathProvided: String?): Boolean {
        if (PRINT_TEMPLATE_PROVIDED_VIA_GLOBAL_SETTING == templatePathProvided) {
            return true
        } else if (templatePathProvided != null) {
            try {
                ReferenceManager.instance().DeriveReference(templatePathProvided).getLocalURI()
                this.printTemplatePath = templatePathProvided
                return true
            } catch (e: InvalidReferenceException) {
                System.out.println("Invalid print template path provided for detail with id " + this.id)
            }
        }
        return false
    }

    fun isPrintEnabled(): Boolean = this.printEnabled

    fun getPrintTemplatePath(): String? = this.printTemplatePath

    fun getKeyValueMapForPrint(
        selectedEntityRef: TreeReference,
        baseContext: EvaluationContext
    ): HashMap<String, DetailFieldPrintInfo> {
        val mapping = HashMap<String, DetailFieldPrintInfo>()
        populateMappingWithDetailFields(mapping, selectedEntityRef, baseContext, null)
        return mapping
    }

    private fun populateMappingWithDetailFields(
        mapping: HashMap<String, DetailFieldPrintInfo>,
        selectedEntityRef: TreeReference,
        baseContext: EvaluationContext,
        parentDetail: Detail?
    ) {
        if (isCompound()) {
            for (childDetail in details) {
                childDetail.populateMappingWithDetailFields(mapping, selectedEntityRef, baseContext, this)
            }
        } else {
            val entityForDetail =
                getCorrespondingEntity(selectedEntityRef, parentDetail, baseContext)
            for (i in fields.indices) {
                if (entityForDetail.isValidField(i)) {
                    mapping[fields[i].getPrintIdentifierRobust()!!] =
                        DetailFieldPrintInfo(fields[i], entityForDetail, i)
                }
            }
        }
    }

    private fun getCorrespondingEntity(
        selectedEntityRef: TreeReference, parentDetail: Detail?,
        baseContext: EvaluationContext
    ): Entity<TreeReference> {
        val entityFactoryContext =
            EntityUtil.getEntityFactoryContext(
                selectedEntityRef, parentDetail != null,
                parentDetail, baseContext
            )
        val factory = NodeEntityFactory(this, entityFactoryContext)
        return factory.getEntity(selectedEntityRef)
    }

    fun getDisplayableChildDetails(ec: EvaluationContext): Array<Detail> {
        val displayableDetails = Vector<Detail>()
        for (d in this.details) {
            if (d.isRelevant(ec)) {
                displayableDetails.add(d)
            }
        }
        @Suppress("UNCHECKED_CAST")
        return ArrayUtilities.copyIntoArray(displayableDetails, arrayOfNulls<Detail>(displayableDetails.size) as Array<Detail>)
    }

    /**
     * NOTE that this method should only be used/considered in the context of a sub-detail (i.e. a tab)
     *
     * @param context The context in which to evaluate the relevancy condition
     * @return true iff the detail should be displayed as a tab
     */
    private fun isRelevant(context: EvaluationContext): Boolean {
        if (parsedRelevancyExpression == null) {
            return true
        }
        return FunctionUtils.toBoolean(parsedRelevancyExpression!!.eval(context))
    }

    fun getGlobal(): Global? = global

    fun getGroup(): DetailGroup? = group

    companion object {
        const val PRINT_TEMPLATE_PROVIDED_VIA_GLOBAL_SETTING = "provided-globally"
    }
}
