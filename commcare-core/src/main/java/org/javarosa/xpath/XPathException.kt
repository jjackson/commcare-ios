package org.javarosa.xpath

open class XPathException : RuntimeException {

    // A reference to the "Source" of this message helpful
    // for tracking down where the invalid xpath was declared
    var source: String? = null
    private var prefix: String? = null

    constructor()

    constructor(s: String?) : super(s)

    constructor(cause: Throwable?) : super(cause)

    fun setMessagePrefix(prefix: String?) {
        this.prefix = prefix
    }

    override val message: String?
        get() {
            if (prefix != null) {
                return "$prefix\n${super.message}"
            }
            return if (source != null) {
                "The problem was located in $source:\n${super.message}"
            } else {
                super.message
            }
        }
}
