package org.javarosa.xform.util

/**
 * Created by willpride on 7/15/16.
 */
class UniversalDate(
    @JvmField val year: Int,
    @JvmField val month: Int,
    @JvmField val day: Int,
    @JvmField val millisFromJavaEpoch: Long
) {
    companion object {
        const val MILLIS_IN_DAY: Long = 1000L * 60 * 60 * 24
    }
}
