package org.commcare.resources.model

/**
 * @author ctsims
 */
open class UnresolvedResourceException : Exception {
    private val r: Resource
    private val userFacing: Boolean

    constructor(r: Resource, message: String?) : this(r, message, false)

    constructor(r: Resource, message: String?, userFacing: Boolean) : super(message) {
        this.r = r
        this.userFacing = userFacing
    }

    constructor(r: Resource, cause: Throwable, message: String?, userFacing: Boolean) : super(message, cause) {
        this.r = r
        this.userFacing = userFacing
    }

    fun getResource(): Resource {
        return r
    }

    fun isMessageUseful(): Boolean {
        return userFacing
    }
}
