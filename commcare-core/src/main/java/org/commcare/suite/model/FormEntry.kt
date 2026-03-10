package org.commcare.suite.model

import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Hashtable
import java.util.Vector

/**
 * Describes a user-initiated form entry action. Includes information that
 * needs to be collected before that action can begin and what the UI should
 * present to the user regarding this action.
 *
 * @author ctsims
 */
class FormEntry : Entry {
    private var xFormNamespace: String? = null
    private var post: PostRequest? = null

    /**
     * Serialization only!
     */
    constructor()

    constructor(
        commandId: String?, display: DisplayUnit?,
        data: Vector<SessionDatum>?, formNamespace: String?,
        instances: Hashtable<String, DataInstance<*>>?,
        stackOperations: Vector<StackOperation>?, assertions: AssertionSet?, post: PostRequest?
    ) : super(commandId, display, data, instances, stackOperations, assertions) {
        this.xFormNamespace = formNamespace
        this.post = post
    }

    /**
     * @return The XForm Namespace of the form which should be filled out in
     * the form entry session triggered by this action.
     */
    override fun getXFormNamespace(): String? = xFormNamespace

    override fun getPostRequest(): PostRequest? = post

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        super.readExternal(`in`, pf)
        this.xFormNamespace = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        this.post = ExtUtil.read(`in`, ExtWrapNullable(PostRequest::class.java), pf) as PostRequest?
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        super.writeExternal(out)
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(xFormNamespace))
        ExtUtil.write(out, ExtWrapNullable(post))
    }
}
