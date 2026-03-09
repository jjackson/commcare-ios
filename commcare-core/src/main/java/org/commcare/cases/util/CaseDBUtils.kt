package org.commcare.cases.util

import org.commcare.cases.model.Case
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.util.MD5

/**
 * @author ctsims
 */
object CaseDBUtils {

    @JvmStatic
    fun computeCaseDbHash(storage: IStorageUtilityIndexed<Case>): String {
        var data = ByteArray(MD5.length)
        for (i in data.indices) {
            data[i] = 0
        }
        var casesExist = false
        val iterator = storage.iterate()
        while (iterator.hasMore()) {
            val c = iterator.nextRecord()
            val record = c.getCaseId()
            val current = MD5.hash(record!!.toByteArray())
            data = xordata(data, current)
            casesExist = true
        }

        // In the base case (with no cases), the case hash is empty
        if (!casesExist) {
            return ""
        }
        return MD5.toHex(data)
    }

    @JvmStatic
    fun xordata(one: ByteArray, two: ByteArray): ByteArray {
        if (one.size != two.size) {
            // Pad?
            throw RuntimeException("Invalid XOR operation between byte arrays of unequal length")
        }
        val output = ByteArray(one.size)
        for (i in one.indices) {
            output[i] = (one[i].toInt() xor two[i].toInt()).toByte()
        }
        return output
    }
}
