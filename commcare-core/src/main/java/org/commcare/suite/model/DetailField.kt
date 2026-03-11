package org.commcare.suite.model
import org.javarosa.core.util.externalizable.JvmExtUtil

import org.javarosa.core.model.Constants
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Detail Fields represent the `<field>` elements of a suite's detail
 * definitions. The model contains the relevant text templates as well
 * as any layout or sorting options.
 *
 * @author ctsims
 */
class DetailField : Externalizable {

    internal var header: Text? = null
    internal var template: DetailTemplate? = null // Text or Graph
    internal var sort: Text? = null
    private var relevancy: String? = null
    private var parsedRelevancy: XPathExpression? = null
    private var headerWidthHint: String? = null  // Something like "500" or "10%"
    private var templateWidthHint: String? = null
    private var printIdentifier: String? = null
    internal var altText: Text? = null
    private var endpointAction: EndpointAction? = null

    private var showBorder: Boolean = false
    private var showShading: Boolean = false

    /**
     * Optional hint which provides a hint for whether rich media should be
     * displayed based on `<text>` returning a URI.  May be either 'image' or
     * 'audio'
     */
    private var headerForm: String? = null

    /**
     * Same as 'headerForm' except can also be set to 'graph'
     */
    private var templateForm: String? = null
    private var sortOrder: Int = -1
    internal var sortDirection: Int = DIRECTION_ASCENDING
    internal var sortType: Int = Constants.DATATYPE_TEXT
    private var showBlanksLastInSort: Boolean = false
    private var gridX: Int = -1
    private var gridY: Int = -1
    private var gridWidth: Int = -1
    private var gridHeight: Int = -1
    private var horizontalAlign: String? = null
    private var verticalAlign: String? = null
    private var fontSize: String? = null
    private var cssID: String? = null

    private var _lazyLoading: Boolean = false
    val isLazyLoading: Boolean get() = _lazyLoading
    private var _cacheEnabled: Boolean = false
    val isCacheEnabled: Boolean get() = _cacheEnabled

    constructor()

    fun getPrintIdentifierRobust(): String? {
        // TODO: change this implementation once HQ work is done
        return if (printIdentifier != null) {
            printIdentifier
        } else if (template is Text) {
            (template as Text).getArgument()
        } else {
            null
        }
    }

    fun getHeader(): Text? = header
    fun getTemplate(): DetailTemplate? = template
    fun getSort(): Text? = sort
    fun getAltText(): Text? = altText

    /**
     * Determine if field should be shown, based on any relevancy condition.
     *
     * @param context Context in which to evaluate the field.
     * @return true iff the field should be displayed
     * @throws XPathSyntaxException
     */
    @Throws(XPathSyntaxException::class)
    fun isRelevant(context: EvaluationContext): Boolean {
        if (relevancy == null) {
            return true
        }

        if (parsedRelevancy == null) {
            val rel = relevancy!!
            parsedRelevancy = XPathParseTool.parseXPath(rel)
        }

        return FunctionUtils.toBoolean(parsedRelevancy!!.eval(context))
    }

    fun getHeaderWidthHint(): String? = headerWidthHint

    fun getTemplateWidthHint(): String? = templateWidthHint

    fun getHeaderForm(): String? = headerForm

    fun getTemplateForm(): String? = templateForm

    fun getSortOrder(): Int = sortOrder

    fun showBlanksLastInSort(): Boolean = this.showBlanksLastInSort

    // sortDirection, sortType are internal properties

    fun isCaseTileField(): Boolean {
        return gridX > -1 && gridY > -1 && gridWidth > -1 && gridHeight > -1
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        header = JvmExtUtil.read(`in`, Text::class.java, pf) as Text
        template = ExtUtil.read(`in`, ExtWrapTagged(DetailTemplate::class.java), pf) as DetailTemplate
        sort = ExtUtil.read(`in`, ExtWrapNullable(Text::class.java), pf) as Text?
        altText = ExtUtil.read(`in`, ExtWrapNullable(Text::class.java), pf) as Text?

        // Unfortunately I don't think there's a clean way to do this
        if (ExtUtil.readBool(`in`)) {
            relevancy = ExtUtil.readString(`in`)
        }
        headerWidthHint = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        templateWidthHint = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        headerForm = ExtUtil.readString(`in`)
        templateForm = ExtUtil.readString(`in`)
        sortOrder = ExtUtil.readInt(`in`)
        sortDirection = ExtUtil.readInt(`in`)
        sortType = ExtUtil.readInt(`in`)
        gridX = ExtUtil.readInt(`in`)
        gridY = ExtUtil.readInt(`in`)
        gridWidth = ExtUtil.readInt(`in`)
        gridHeight = ExtUtil.readInt(`in`)
        fontSize = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        showBlanksLastInSort = ExtUtil.readBool(`in`)
        horizontalAlign = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        verticalAlign = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        cssID = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        endpointAction = ExtUtil.read(`in`, ExtWrapNullable(EndpointAction::class.java), pf) as EndpointAction?
        showBorder = ExtUtil.readBool(`in`)
        showShading = ExtUtil.readBool(`in`)
        _cacheEnabled = ExtUtil.readBool(`in`)
        _lazyLoading = ExtUtil.readBool(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.write(out, header)
        ExtUtil.write(out, ExtWrapTagged(template!!))
        ExtUtil.write(out, ExtWrapNullable(sort))
        ExtUtil.write(out, ExtWrapNullable(altText))

        val relevantSet = relevancy != null
        ExtUtil.writeBool(out, relevantSet)
        if (relevantSet) {
            ExtUtil.writeString(out, relevancy)
        }
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(headerWidthHint))
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(templateWidthHint))
        ExtUtil.writeString(out, headerForm)
        ExtUtil.writeString(out, templateForm)
        ExtUtil.writeNumeric(out, sortOrder.toLong())
        ExtUtil.writeNumeric(out, sortDirection.toLong())
        ExtUtil.writeNumeric(out, sortType.toLong())
        ExtUtil.writeNumeric(out, gridX.toLong())
        ExtUtil.writeNumeric(out, gridY.toLong())
        ExtUtil.writeNumeric(out, gridWidth.toLong())
        ExtUtil.writeNumeric(out, gridHeight.toLong())
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(fontSize))
        ExtUtil.writeBool(out, showBlanksLastInSort)
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(horizontalAlign))
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(verticalAlign))
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(cssID))
        ExtUtil.write(out, ExtWrapNullable(endpointAction))
        ExtUtil.writeBool(out, showBorder)
        ExtUtil.writeBool(out, showShading)
        ExtUtil.writeBool(out, _cacheEnabled)
        ExtUtil.writeBool(out, _lazyLoading)
    }

    fun getGridX(): Int = gridX
    fun getGridY(): Int = gridY
    fun getGridWidth(): Int = gridWidth
    fun getGridHeight(): Int = gridHeight
    fun getHorizontalAlign(): String? = horizontalAlign
    fun getVerticalAlign(): String? = verticalAlign
    // altText is internal property
    fun getEndpointAction(): EndpointAction? = endpointAction
    fun getFontSize(): String? = fontSize
    fun getCssId(): String? = cssID
    fun getShowBorder(): Boolean = showBorder
    fun getShowShading(): Boolean = showShading
    // isLazyLoading and isCacheEnabled are public properties

    class Builder {
        @JvmField
        val field: DetailField = DetailField()

        fun build(): DetailField = field

        fun setPrintIdentifier(id: String?) { field.printIdentifier = id }
        fun setHeader(header: Text?) { field.header = header }
        fun setAltText(altText: Text?) { field.altText = altText }
        fun setTemplate(template: DetailTemplate?) { field.template = template }
        fun setSort(sort: Text?) { field.sort = sort }
        fun setRelevancy(relevancy: String?) { field.relevancy = relevancy }
        fun setHeaderWidthHint(hint: String?) { field.headerWidthHint = hint }
        fun setTemplateWidthHint(hint: String?) { field.templateWidthHint = hint }
        fun setHeaderForm(headerForm: String?) { field.headerForm = headerForm }
        fun setTemplateForm(templateForm: String?) { field.templateForm = templateForm }
        fun setSortOrder(sortOrder: Int) { field.sortOrder = sortOrder }
        fun setSortDirection(sortDirection: Int) { field.sortDirection = sortDirection }
        fun setShowBlanksLast(blanksLast: Boolean) { field.showBlanksLastInSort = blanksLast }
        fun setSortType(sortType: Int) { field.sortType = sortType }
        fun setGridX(gridX: Int) { field.gridX = gridX }
        fun setGridY(gridY: Int) { field.gridY = gridY }
        fun setGridWidth(gridWidth: Int) { field.gridWidth = gridWidth }
        fun setGridHeight(gridHeight: Int) { field.gridHeight = gridHeight }
        fun setHorizontalAlign(horizontalAlign: String?) { field.horizontalAlign = horizontalAlign }
        fun setVerticalAlign(verticalAlign: String?) { field.verticalAlign = verticalAlign }
        fun setFontSize(fontSize: String?) { field.fontSize = fontSize }
        fun setCssID(id: String?) { field.cssID = id }
        fun setEndpointAction(endpointAction: EndpointAction?) { field.endpointAction = endpointAction }
        fun setShowBorder(showBorder: Boolean) { field.showBorder = showBorder }
        fun setShowShading(showShading: Boolean) { field.showShading = showShading }
        fun setCacheEnabled(cacheEnabled: Boolean) { field._cacheEnabled = cacheEnabled }
        fun setLazyLoading(lazyLoading: Boolean) { field._lazyLoading = lazyLoading }
    }

    companion object {
        const val DIRECTION_ASCENDING = 1
        const val DIRECTION_DESCENDING = 2

        /**
         * A special flag that signals that this "Sort" should actually be
         * a cached, asynchronous key
         */
        const val SORT_ORDER_CACHABLE = -2
    }
}
