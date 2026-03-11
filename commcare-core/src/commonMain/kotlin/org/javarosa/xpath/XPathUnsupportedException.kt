package org.javarosa.xpath

open class XPathUnsupportedException : XPathException {
    constructor()

    constructor(s: String?) : super("unsupported construct [$s]")
}
