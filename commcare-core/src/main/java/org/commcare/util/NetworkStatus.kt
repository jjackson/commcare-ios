package org.commcare.util

import org.commcare.core.network.CommCareNetworkServiceGenerator
import org.javarosa.core.services.Logger
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * @author $|-|!˅@M
 */
object NetworkStatus {
    @JvmStatic
    fun isCaptivePortal(): Boolean {
        val captivePortalURL = "http://www.commcarehq.org/serverup.txt"
        val commCareNetworkService =
            CommCareNetworkServiceGenerator.createNoAuthCommCareNetworkService()
        return try {
            val response =
                commCareNetworkService.makeGetRequest(captivePortalURL, HashMap()).execute()
            response.code() == 200 && "success" != response.body()?.string()
        } catch (e: PlatformIOException) {
            Logger.log(LogTypes.TYPE_WARNING_NETWORK, "Detecting captive portal failed with exception" + e.message)
            false
        }
    }
}
