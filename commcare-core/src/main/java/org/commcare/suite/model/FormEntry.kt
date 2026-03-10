package org.commcare.suite.model

import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.PrototypeFactory
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
        this.xFormNamespace = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        this.post = ExtUtil.read(`in`, ExtWrapNullable(PostRequest::class.java), pf) as PostRequest?
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        super.writeExternal(out)
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(xFormNamespace))
        ExtUtil.write(out, ExtWrapNullable(post))
    }
}
