package org.javarosa.core.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Represents any additional information included in an "upload" question type via extra
 * attributes that are parsed by UploadQuestionExtensionParser
 *
 * @author amstone
 */
class UploadQuestionExtension : QuestionDataExtension {

    var maxDimen: Int = 0
        private set

    @Suppress("unused")
    constructor() {
        // for deserialization
    }

    constructor(maxDimen: Int) {
        this.maxDimen = maxDimen
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(dis: DataInputStream, pf: PrototypeFactory) {
        this.maxDimen = ExtUtil.readInt(dis)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(dos: DataOutputStream) {
        ExtUtil.writeNumeric(dos, maxDimen.toLong())
    }
}
