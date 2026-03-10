package org.commcare.cases.ledger

import org.commcare.cases.model.Case
import org.javarosa.core.services.storage.EntityFilter
import org.javarosa.core.services.storage.IStorageIterator
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.util.DataUtil


/**
 * @author ctsims
 */
class LedgerPurgeFilter(
    ledgerStorage: IStorageUtilityIndexed<Ledger>,
    caseStorage: IStorageUtilityIndexed<Case>
) : EntityFilter<Ledger>() {

    val idsToRemove: ArrayList<Int> = ArrayList()

    init {
        val i: IStorageIterator<Ledger> = ledgerStorage.iterate()
        while (i.hasMore()) {
            val s = i.nextRecord()
            try {
                caseStorage.getRecordForValue(Case.INDEX_CASE_ID, s.getEntiyId() as Any)
            } catch (nsee: NoSuchElementException) {
                idsToRemove.add(Integer.valueOf(s.getID()))
            }
        }
    }

    override fun preFilter(id: Int, metaData: HashMap<String, Any>?): Int {
        return if (idsToRemove.contains(DataUtil.integer(id))) {
            PREFILTER_INCLUDE
        } else {
            PREFILTER_EXCLUDE
        }
    }

    override fun matches(e: Ledger): Boolean {
        // We're doing everything with pre-filtering
        return false
    }
}
