package org.commcare.cases.entity

interface EntitySortNotificationInterface {
    fun notifyBadFilter(args: Array<String?>)
}
