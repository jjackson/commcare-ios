package org.commcare.cases.instance

import org.commcare.cases.ledger.Ledger
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.model.xform.XPathReference
import org.javarosa.xpath.expr.XPathPathExpr

/**
 * @author ctsims
 */
class LedgerInstanceTreeElement(
    instanceRoot: AbstractTreeElement?,
    storage: IStorageUtilityIndexed<Ledger>
) : StorageInstanceTreeElement<Ledger, LedgerChildElement>(instanceRoot, storage, MODEL_NAME, "ledger") {

    override fun buildElement(
        storageInstance: StorageInstanceTreeElement<Ledger, LedgerChildElement>,
        recordId: Int,
        id: String?,
        mult: Int
    ): LedgerChildElement {
        return LedgerChildElement(storageInstance, recordId, null, mult)
    }

    override fun getChildTemplate(): LedgerChildElement {
        return LedgerChildElement.templateElement(this)
    }

    override fun getStorageIndexMap(): HashMap<XPathPathExpr, String> {
        val indices = HashMap<XPathPathExpr, String>()

        //TODO: Much better matching
        indices[ENTITY_ID_EXPR] = Ledger.INDEX_ENTITY_ID
        indices[ENTITY_ID_EXPR_TWO] = Ledger.INDEX_ENTITY_ID

        return indices
    }

    override val storageCacheName: String?
        get() = MODEL_NAME

    companion object {
        const val MODEL_NAME = "ledgerdb"

        private val ENTITY_ID_EXPR: XPathPathExpr = XPathReference.getPathExpr("@entity-id")
        private val ENTITY_ID_EXPR_TWO: XPathPathExpr = XPathReference.getPathExpr("./@entity-id")
    }
}
