package org.javarosa.core.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

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

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        name = ExtUtil.read(`in`, ExtWrapNullable(String::class.java), pf) as String?
        textId = ExtUtil.read(`in`, ExtWrapNullable(String::class.java), pf) as String?
        textInner = ExtUtil.read(`in`, ExtWrapNullable(String::class.java), pf) as String?
        textFallback = ExtUtil.read(`in`, ExtWrapNullable(String::class.java), pf) as String?
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.write(out, ExtWrapNullable(name))
        ExtUtil.write(out, ExtWrapNullable(textId))
        ExtUtil.write(out, ExtWrapNullable(textInner))
        ExtUtil.write(out, ExtWrapNullable(textFallback))
    }
}
