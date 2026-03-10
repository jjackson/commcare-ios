package org.commcare.core.network

import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Exception wrapper which communicates that an exception is the result of the current network
 * being behind a captive portal which is redirecting traffic
 *
 * @author Clayton Sims (csims@dimagi.com)
 */
class CaptivePortalRedirectException : PlatformIOException(
    "The current network is not connected to the internet. You may need to log in from a web browser"
)
