package org.commcare.suite.model

import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.core.util.externalizable.emptyIfNull
import org.javarosa.core.util.externalizable.nullIfEmpty

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

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
        data: ArrayList<SessionDatum>?, formNamespace: String?,
        instances: HashMap<String, DataInstance<*>>?,
        stackOperations: ArrayList<StackOperation>?, assertions: AssertionSet?, post: PostRequest?
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

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        super.readExternal(`in`, pf)
        this.xFormNamespace = nullIfEmpty(SerializationHelpers.readString(`in`))
        this.post = SerializationHelpers.readNullableExternalizable(`in`, pf) { PostRequest() }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        super.writeExternal(out)
        SerializationHelpers.writeString(out, emptyIfNull(xFormNamespace))
        SerializationHelpers.writeNullable(out, post)
    }
}
