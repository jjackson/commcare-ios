package org.javarosa.core.model

import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * A Submission Profile is a class which is responsible for
 * holding and processing the details of how a <submission/>
 * should be handled.
 *
 * @author ctsims
 */
class SubmissionProfile : Externalizable {

    var targetRef: TreeReference? = null
        private set
    var ref: TreeReference? = null
        private set
    var resource: String? = null
        private set

    constructor()

    constructor(resource: String?, targetRef: TreeReference?, ref: TreeReference?) {
        this.ref = ref
        this.targetRef = targetRef
        this.resource = resource
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        resource = SerializationHelpers.readString(`in`)
        targetRef = SerializationHelpers.readExternalizable(`in`, pf) { TreeReference() }
        ref = SerializationHelpers.readNullableExternalizable(`in`, pf) { TreeReference() }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeString(out, resource!!)
        SerializationHelpers.write(out, targetRef!!)
        SerializationHelpers.writeNullable(out, ref)
    }
}
