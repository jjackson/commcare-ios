package org.javarosa.core.model

import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

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

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        resource = ExtUtil.readString(`in`)
        targetRef = ExtUtil.read(`in`, TreeReference::class.java, pf) as TreeReference
        ref = ExtUtil.read(`in`, ExtWrapNullable(TreeReference::class.java), pf) as TreeReference?
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeString(out, resource!!)
        ExtUtil.write(out, targetRef!!)
        ExtUtil.write(out, ExtWrapNullable(ref))
    }
}
