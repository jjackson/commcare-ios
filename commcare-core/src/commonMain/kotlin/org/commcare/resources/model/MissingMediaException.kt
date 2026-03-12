package org.commcare.resources.model

/**
 * @author ctsims
 */
class MissingMediaException : Exception {

    val r: Resource
    private val type: MissingMediaExceptionType
    private var URI: String?

    enum class MissingMediaExceptionType {
        FILE_NOT_FOUND,
        FILE_NOT_ACCESSIBLE,
        INVALID_REFERENCE,
        NONE
    }

    constructor(r: Resource, message: String, mediaExceptionType: MissingMediaExceptionType)
            : this(r, message, message, mediaExceptionType)

    constructor(r: Resource, message: String, uri: String?, mediaExceptionType: MissingMediaExceptionType)
            : super(message) {
        this.URI = uri
        this.r = r
        this.type = mediaExceptionType
    }

    fun getResource(): Resource {
        return r
    }

    fun getURI(): String? {
        return URI
    }

    override fun equals(other: Any?): Boolean {
        if (other !is MissingMediaException) {
            return false
        }

        val thisUri = URI
        val otherUri = other.getURI()

        if (thisUri == null || otherUri == null) {
            return false
        }
        return thisUri == otherUri
    }

    override fun hashCode(): Int {
        return URI?.hashCode() ?: 0
    }

    override fun toString(): String {
        return URI ?: ""
    }

    fun getType(): MissingMediaExceptionType {
        return type
    }
}
