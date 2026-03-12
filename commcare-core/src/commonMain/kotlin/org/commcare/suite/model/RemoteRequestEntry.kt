package org.commcare.suite.model

import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Suite entry for performing a synchronous query/post request to an external
 * server. Lifecycle is: gather query params, query the server, process
 * response data, and complete the transaction with a post to the server.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
class RemoteRequestEntry : Entry {
    private var post: PostRequest? = null

    constructor()

    constructor(
        commandId: String?, display: DisplayUnit?,
        data: ArrayList<SessionDatum>?,
        instances: HashMap<String, DataInstance<*>>?,
        stackOperations: ArrayList<StackOperation>?,
        assertions: AssertionSet?,
        post: PostRequest?
    ) : super(commandId, display, data, instances, stackOperations, assertions) {
        this.post = post
    }

    override fun getPostRequest(): PostRequest? = post

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        super.readExternal(`in`, pf)
        post = SerializationHelpers.readExternalizable(`in`, pf) { PostRequest() }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        super.writeExternal(out)
        SerializationHelpers.write(out, post!!)
    }
}
