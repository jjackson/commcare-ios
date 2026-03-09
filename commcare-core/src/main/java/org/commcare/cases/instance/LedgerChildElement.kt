package org.commcare.cases.instance

import org.commcare.cases.ledger.Ledger
import org.commcare.cases.query.QueryContext
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.instance.utils.TreeUtilities
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.expr.XPathPathExpr
import java.util.Hashtable
import java.util.Vector

/**
 * @author ctsims
 */
class LedgerChildElement : StorageBackedChildElement<Ledger> {

    private var childAttributeHintMap: Hashtable<XPathPathExpr, Hashtable<String, Array<TreeElement>>>? = null

    private var empty: TreeElement? = null

    constructor(
        parent: StorageInstanceTreeElement<Ledger, *>,
        recordId: Int,
        entityId: String?,
        mult: Int
    ) : super(parent, mult, recordId, entityId, NAME_ID)

    /*
     * Template constructor (For elements that need to create reference nodesets but never look up values)
     */
    private constructor(parent: StorageInstanceTreeElement<Ledger, *>) :
            super(parent, TreeReference.INDEX_TEMPLATE, TreeReference.INDEX_TEMPLATE, null, NAME_ID) {

        empty = TreeElement(NAME)
        empty!!.setMult(this._mult)

        empty!!.setAttribute(null, nameId, "")

        val blankLedger = TreeElement(SUBNAME)
        blankLedger.setAttribute(null, SUBNAME_ID, "")

        val scratch = TreeElement(FINALNAME)
        scratch.setAttribute(null, FINALNAME_ID, "")
        scratch.setAnswer(null)

        blankLedger.addChild(scratch)
        empty!!.addChild(blankLedger)
    }

    override fun getName(): String? {
        return NAME
    }

    //TODO: THIS IS NOT THREAD SAFE
    override fun cache(context: QueryContext?): TreeElement {
        if (recordId == TreeReference.INDEX_TEMPLATE) {
            return empty!!
        }
        synchronized(parent.treeCache) {
            val element = parent.treeCache.retrieve(recordId)
            if (element != null) {
                return element
            }

            val cacheBuilder = TreeElement(NAME)
            val ledger = parent.getElement(recordId, context)
            entityId = ledger.getEntiyId()
            cacheBuilder.setMult(this._mult)

            cacheBuilder.setAttribute(null, nameId, ledger.getEntiyId())

            val childAttrHintMap = Hashtable<XPathPathExpr, Hashtable<String, Array<TreeElement>>>()
            val sectionIdMap = Hashtable<String, Array<TreeElement>>()

            val sectionList = ledger.getSectionList()
            for (i in sectionList.indices) {
                val ledgerElement = TreeElement(SUBNAME, i)
                ledgerElement.setAttribute(null, SUBNAME_ID, sectionList[i])
                cacheBuilder.addChild(ledgerElement)
                sectionIdMap[sectionList[i]] = arrayOf(ledgerElement)

                val hintMap = Hashtable<XPathPathExpr, Hashtable<String, Array<TreeElement>>>()
                val idMap = Hashtable<String, Array<TreeElement>>()

                val entryList = ledger.getListOfEntries(sectionList[i])
                for (j in entryList.indices) {
                    val entry = TreeElement(FINALNAME, j)
                    entry.setAttribute(null, FINALNAME_ID, entryList[j])
                    entry.setValue(IntegerData(ledger.getEntry(sectionList[i], entryList[j])))
                    ledgerElement.addChild(entry)
                    idMap[entryList[j]] = arrayOf(entry)
                }

                hintMap[TreeUtilities.getXPathAttrExpression(FINALNAME_ID)] = idMap
                ledgerElement.addAttributeMap(hintMap)
            }
            childAttrHintMap[TreeUtilities.getXPathAttrExpression(SUBNAME_ID)] = sectionIdMap
            cacheBuilder.addAttributeMap(childAttrHintMap)
            childAttributeHintMap = childAttrHintMap

            cacheBuilder.setParent(this.parent)

            parent.treeCache.register(recordId, cacheBuilder)

            return cacheBuilder
        }
    }

    override fun tryBatchChildFetch(
        name: String,
        mult: Int,
        predicates: Vector<XPathExpression>,
        evalContext: EvaluationContext
    ): Collection<TreeReference>? {
        return TreeUtilities.tryBatchChildFetch(this, childAttributeHintMap, name, mult, predicates, evalContext)
    }

    companion object {
        const val NAME = "ledger"
        private const val NAME_ID = "entity-id"
        private const val SUBNAME = "section"
        private const val SUBNAME_ID = "section-id"
        private const val FINALNAME = "entry"
        private const val FINALNAME_ID = "id"

        @JvmStatic
        fun templateElement(parent: LedgerInstanceTreeElement): LedgerChildElement {
            return LedgerChildElement(parent)
        }
    }
}
