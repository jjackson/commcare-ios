package org.javarosa.core.log

class FatalException : WrappedException {
    constructor(message: String?) : super(message)
    constructor(message: String?, child: Exception?) : super(message, child)
}
