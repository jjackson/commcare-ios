package org.javarosa.test_utils

import org.javarosa.core.model.utils.TimezoneProvider

class MockTimezoneProvider : TimezoneProvider() {

    private var offsetMillis: Int = 0

    fun setOffset(offset: Int) {
        this.offsetMillis = offset
    }

    override fun getTimezoneOffsetMillis(): Int = offsetMillis
}
