package org.commcare.core.services

import kotlin.jvm.JvmStatic

/**
 * Wiring to allow access to Android preferences in commcare-core, but can potentially be used for any temporary key value storage
 */
object CommCarePreferenceManagerFactory {

    private var sCommCarePreferenceManager: ICommCarePreferenceManager? = null

    @JvmStatic
    fun init(commCarePreferenceManager: ICommCarePreferenceManager) {
        sCommCarePreferenceManager = commCarePreferenceManager
    }

    @JvmStatic
    fun getCommCarePreferenceManager(): ICommCarePreferenceManager? {
        return sCommCarePreferenceManager
    }
}
