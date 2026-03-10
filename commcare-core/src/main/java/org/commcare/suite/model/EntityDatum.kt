package org.commcare.suite.model

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.model.xform.XPathReference
import org.javarosa.xpath.expr.XPathEqExpr
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.expr.XPathStringLiteral

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Represents entity selection requirement in the current session. The nodeset
 * defines entities up for selection and detail fields are keys to display
 * formatting definitions
 */
open class EntityDatum : SessionDatum {
    private var nodeset: TreeReference? = null
    private var shortDetail: String? = null
    private var longDetail: String? = null
    private var inlineDetail: String? = null
    private var persistentDetail: String? = null
    private var autoSelectEnabled: Boolean = false

    constructor()

    constructor(
        id: String?, nodeset: String?, shortDetail: String?, longDetail: String?,
        inlineDetail: String?, persistentDetail: String?, value: String?, autoselect: String?
    ) : super(id, value) {
        this.nodeset = XPathReference.getPathExpr(nodeset!!).getReference()
        this.shortDetail = shortDetail
        this.longDetail = longDetail
        this.inlineDetail = inlineDetail
        this.persistentDetail = persistentDetail
        this.autoSelectEnabled = "true" == autoselect
    }

    fun getNodeset(): TreeReference? = nodeset

    /**
     * the ID of a detail that structures the screen for selecting an item from the nodeset
     */
    fun getShortDetail(): String? = shortDetail

    /**
     * the ID of a detail that will show a selected item for confirmation. If not present,
     * no confirmation screen is shown after item selection
     */
    fun getLongDetail(): String? = longDetail

    fun getInlineDetail(): String? = inlineDetail

    fun getPersistentDetail(): String? = persistentDetail

    fun isAutoSelectEnabled(): Boolean = autoSelectEnabled

    /**
     * @return The case that would be auto-selected for this EntityDatum in the given eval context
     * if there is one, or null if there is not
     */
    fun getCurrentAutoselectableCase(ec: EvaluationContext): TreeReference? {
        if (isAutoSelectEnabled()) {
            val entityListElements = ec.expandReference(this.getNodeset()!!)
            if (entityListElements != null && entityListElements.size == 1) {
                return entityListElements[0]
            }
        }
        return null
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        super.readExternal(`in`, pf)

        if (ExtUtil.readBool(`in`)) {
            nodeset = ExtUtil.read(`in`, TreeReference::class.java, pf) as TreeReference
        } else {
            nodeset = null
        }
        shortDetail = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        longDetail = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        inlineDetail = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        persistentDetail = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        autoSelectEnabled = ExtUtil.readBool(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        super.writeExternal(out)

        ExtUtil.writeBool(out, nodeset != null)
        val ns = nodeset
        if (ns != null) {
            ExtUtil.write(out, ns)
        }
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(shortDetail))
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(longDetail))
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(inlineDetail))
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(persistentDetail))
        ExtUtil.writeBool(out, autoSelectEnabled)
    }

    /**
     * Takes an ID and identifies a reference in the provided context which corresponds
     * to that element if one can be found.
     *
     * NOT GUARANTEED TO WORK! May return an entity if one exists
     */
    fun getEntityFromID(ec: EvaluationContext, elementId: String): TreeReference? {
        // The uniqueid here is the value selected, so we can in theory track down the value we're looking for.

        // Get root nodeset
        val nodesetRef = this.getNodeset()!!.clone()
        var predicates = nodesetRef.getPredicate(nodesetRef.size() - 1)
        if (predicates == null) {
            predicates = ArrayList()
        }
        // For speed reasons, add a case id selection as the first predicate
        // This has potential to change outcomes if other predicates utilize 'position'
        val caseIdSelection = XPathEqExpr(
            XPathEqExpr.EQ,
            XPathReference.getPathExpr(this.getValue()!!),
            XPathStringLiteral(elementId)
        )
        predicates.add(0, caseIdSelection)
        nodesetRef.addPredicate(nodesetRef.size() - 1, predicates)

        val elements = ec.expandReference(nodesetRef)
        return if (elements?.size == 1) {
            elements?.first()
        } else {
            null
        }
    }
}
