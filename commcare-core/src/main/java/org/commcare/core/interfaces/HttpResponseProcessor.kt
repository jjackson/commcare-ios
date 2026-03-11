package org.commcare.core.interfaces

import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.io.PlatformInputStream

/**
 * Callbacks for different http response result codes
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
interface HttpResponseProcessor {
    /**
     * Http response was in the 200s
     */
    fun processSuccess(responseCode: Int, responseData: PlatformInputStream, apiVersion: String?)

    /**
     * Http response was in the 400s.
     *
     * Can represent authentication issues, data parity issues between client
     * and server, among other things
     */
    fun processClientError(responseCode: Int, errorStream: PlatformInputStream?)

    /**
     * Http response was in the 500s
     */
    fun processServerError(responseCode: Int)

    /**
     * Http response that had a code not in the 200-599 range
     */
    fun processOther(responseCode: Int)

    /**
     * A issue occurred while processing the http request or response
     */
    fun handleIOException(exception: PlatformIOException)
}
