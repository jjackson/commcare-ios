package org.commcare.xml.bulk

import org.commcare.cases.model.Case
import org.javarosa.core.services.storage.IStorageUtilityIndexed

import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.LinkedHashMap
import org.javarosa.xml.PlatformXmlParser

/**
 * Reference implementation of BulkProcessingCaseXMLParser which can be used with no platform
 * specific indexing / db code, but which essentially has no performance gains over a linear
 * parser, and probably operates slower than one.
 *
 * This class can be used to test the implementation of the bulk processing parser independent
 * of the platform specific steps, or where the semantics of the processor are relevant but the
 * bulk aspect is not
 *
 * Created by ctsims on 3/14/2017.
 */
open class LinearBulkProcessingCaseXmlParser(
    parser: PlatformXmlParser,
    private val storage: IStorageUtilityIndexed<Case>
) : BulkProcessingCaseXmlParser(parser) {

    override fun performBulkRead(
        currentBulkReadSet: Set<String>,
        currentOperatingSet: MutableMap<String, Case>
    ) {
        for (index in currentBulkReadSet) {
            val c = retrieve(index)
            if (c != null) {
                currentOperatingSet[index] = c
            }
        }
    }

    @Throws(PlatformIOException::class)
    override fun performBulkWrite(writeLog: LinkedHashMap<String, Case>) {
        for (c in writeLog.values) {
            commit(c)
        }
    }

    @Throws(PlatformIOException::class)
    private fun commit(parsed: Case) {
        storage.write(parsed)
    }

    protected open fun retrieve(entityId: String): Case? {
        return try {
            storage.getRecordForValue(Case.INDEX_CASE_ID, entityId)
        } catch (nsee: NoSuchElementException) {
            null
        }
    }
}
