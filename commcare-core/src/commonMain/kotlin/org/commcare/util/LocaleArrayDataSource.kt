package org.commcare.util

import org.javarosa.core.services.locale.Localization
import org.javarosa.core.util.NoLocalizedTextException

/**
 * Get localized arrays from the Localization file system (stored as comma separated lists)
 */
class LocaleArrayDataSource : ArrayDataSource {

    private var fallback: ArrayDataSource? = null

    constructor()

    constructor(fallback: ArrayDataSource) {
        this.fallback = fallback
    }

    override fun getArray(key: String): Array<String> {
        try {
            return Localization.getArray(key)
        } catch (e: NoLocalizedTextException) {
            val fb = fallback
            if (fb != null) {
                return fb.getArray(key)
            } else {
                throw e
            }
        }
    }
}
