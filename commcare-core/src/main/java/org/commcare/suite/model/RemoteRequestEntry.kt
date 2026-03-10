package org.commcare.suite.model

import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Hashtable
import java.util.Vector

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
        data: Vector<SessionDatum>?,
        instances: Hashtable<String, DataInstance<*>>?,
        stackOperations: Vector<StackOperation>?,
        assertions: AssertionSet?,
        post: PostRequest?
    ) : super(commandId, display, data, instances, stackOperations, assertions) {
        this.post = post
    }

    override fun getPostRequest(): PostRequest? = post

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        super.readExternal(`in`, pf)
        post = ExtUtil.read(`in`, PostRequest::class.java, pf) as PostRequest
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        super.writeExternal(out)
        ExtUtil.write(out, post)
    }
}
