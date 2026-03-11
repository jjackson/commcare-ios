package org.javarosa.core.log

import kotlin.jvm.JvmStatic

open class WrappedException : RuntimeException {

    val wrappedMessage: String?
    val child: Exception?

    constructor(message: String?) : this(message, null)

    constructor(child: Exception?) : this(null, child)

    constructor(message: String?, child: Exception?) : super(constructMessage(message, child)) {
        this.wrappedMessage = message
        this.child = child
    }

    companion object {
        @JvmStatic
        fun constructMessage(message: String?, child: Exception?): String {
            var str = ""
            if (message != null) {
                str += message
            }
            if (child != null) {
                str += (if (message != null) " => " else "") + printException(child)
            }

            if (str == "")
                str = "[exception]"
            return str
        }

        @JvmStatic
        fun printException(e: Throwable): String {
            return if (e is WrappedException) {
                (if (e is FatalException) "FATAL: " else "") + e.message
            } else {
                (e::class.simpleName ?: e::class.toString()) + "[" + e.message + "]"
            }
        }
    }
}
