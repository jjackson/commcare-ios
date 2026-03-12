package org.commcare.suite.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.xpath.expr.XPathExpression

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

class DetailGroup : Externalizable {
    internal var function: XPathExpression? = null
    private var headerRows: Int = 0

    /**
     * Serialization only!!!
     */
    constructor()

    constructor(function: XPathExpression?, headerRows: Int) {
        this.function = function
        this.headerRows = headerRows
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        function = SerializationHelpers.readTagged(`in`, pf) as XPathExpression
        headerRows = SerializationHelpers.readInt(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeTagged(out, function!!)
        SerializationHelpers.write(out, headerRows)
    }

    // function is internal property

    fun getHeaderRows(): Int = headerRows
}
