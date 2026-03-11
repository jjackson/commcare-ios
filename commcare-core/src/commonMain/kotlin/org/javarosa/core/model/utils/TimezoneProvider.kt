package org.javarosa.core.model.utils

/**
 * Created by amstone326 on 1/5/18.
 */
open class TimezoneProvider {

    open fun getTimezoneOffsetMillis(): Int {
        return -1
    }

    open fun getTimezone(): PlatformTimeZone? {
        return null
    }
}
