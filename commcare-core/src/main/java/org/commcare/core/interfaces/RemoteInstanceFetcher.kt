package org.commcare.core.interfaces

import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.ExternalDataInstanceSource

/**
 * Fetches remote instance definitions from cache or by making a remote web call
 */
interface RemoteInstanceFetcher {

    @Throws(RemoteInstanceException::class)
    fun getExternalRoot(instanceId: String, source: ExternalDataInstanceSource, refId: String): AbstractTreeElement

    fun getVirtualDataInstanceStorage(): VirtualDataInstanceStorage

    class RemoteInstanceException : Exception {
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)
    }
}
