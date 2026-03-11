package org.javarosa.core.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Created by wpride1 on 5/7/15.
 *
 * Represents the complete set of text strings that can be displayed
 * with a question. Does not contain multimedia references.
 */
class QuestionString : Externalizable {

    var name: String? = null
        private set
    var textId: String? = null
    var textInner: String? = null
    var textFallback: String? = null

    constructor()

    constructor(name: String?) {
        this.name = name
    }

    constructor(name: String?, inner: String?) {
        this.name = name
        this.textInner = inner
    }

    override fun toString(): String {
        return "Name: $name ID: $textId Inner: $textInner Fallback: $textFallback"
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        name = SerializationHelpers.readNullableString(`in`, pf)
        textId = SerializationHelpers.readNullableString(`in`, pf)
        textInner = SerializationHelpers.readNullableString(`in`, pf)
        textFallback = SerializationHelpers.readNullableString(`in`, pf)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeNullable(out, name)
        SerializationHelpers.writeNullable(out, textId)
        SerializationHelpers.writeNullable(out, textInner)
        SerializationHelpers.writeNullable(out, textFallback)
    }
}
