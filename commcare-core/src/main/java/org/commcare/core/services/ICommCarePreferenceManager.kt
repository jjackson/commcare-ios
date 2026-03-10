package org.commcare.core.services

/**
 * Interface to be implemented by any custom preference implementation. Add any putXXX getXXX methods as you need them.
 */
interface ICommCarePreferenceManager {

    fun putLong(key: String, value: Long)

    fun getLong(key: String, defaultValue: Long): Long
}
