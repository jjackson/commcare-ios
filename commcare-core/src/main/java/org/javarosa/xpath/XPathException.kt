package org.javarosa.xpath

import java.text.MessageFormat

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
                return MessageFormat.format("{0}\n{1}", prefix, super.message)
            }
            return if (source != null) {
                MessageFormat.format(
                    "The problem was located in {0}:\n{1}", source, super.message
                )
            } else {
                super.message
            }
        }
}
