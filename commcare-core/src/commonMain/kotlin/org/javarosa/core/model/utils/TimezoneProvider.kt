package org.javarosa.core.model.utils

/**
 * Provider for timezone information. Can be overridden to force a specific timezone.
 */
open class TimezoneProvider {

    open fun getTimezoneOffsetMillis(): Int {
        return -1
    }

    open fun getTimezoneId(): String? {
        return null
    }
}
