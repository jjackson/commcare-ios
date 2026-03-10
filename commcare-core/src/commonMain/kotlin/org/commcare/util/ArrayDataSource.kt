package org.commcare.util

/**
 * Define where to get localized array values from
 */
interface ArrayDataSource {
    fun getArray(key: String): Array<String>
}
