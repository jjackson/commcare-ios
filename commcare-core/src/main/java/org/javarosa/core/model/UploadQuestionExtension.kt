package org.javarosa.core.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.SerializationHelpers

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
    override fun readExternal(dis: PlatformDataInputStream, pf: PrototypeFactory) {
        this.maxDimen = SerializationHelpers.readInt(dis)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(dos: PlatformDataOutputStream) {
        SerializationHelpers.writeNumeric(dos, maxDimen.toLong())
    }
}
