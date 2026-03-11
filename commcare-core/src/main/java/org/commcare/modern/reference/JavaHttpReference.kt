package org.commcare.modern.reference

import org.commcare.core.network.CaptivePortalRedirectException
import org.commcare.util.NetworkStatus
import org.javarosa.core.reference.Reference

import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.io.PlatformInputStream
import org.javarosa.core.io.PlatformOutputStream
import java.net.HttpURLConnection
import java.net.URL

import javax.net.ssl.SSLException

/**
 * @author ctsims
 */
class JavaHttpReference(private val uri: String) : Reference {

    @Throws(PlatformIOException::class)
    override fun doesBinaryExist(): Boolean {
        //For now....
        return true
    }

    @Throws(PlatformIOException::class)
    override fun getOutputStream(): PlatformOutputStream {
        throw PlatformIOException("Http references are read only!")
    }

    @Throws(PlatformIOException::class)
    override fun getStream(): PlatformInputStream {
        try {
            val url = URL(uri)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true  //you still need to handle redirect manully.
            HttpURLConnection.setFollowRedirects(true)

            return conn.inputStream
        } catch (e: SSLException) {
            if (NetworkStatus.isCaptivePortal()) {
                throw CaptivePortalRedirectException()
            }
            throw e
        }
    }

    override fun getURI(): String {
        return uri
    }

    override fun isReadOnly(): Boolean {
        return true
    }

    @Throws(PlatformIOException::class)
    override fun remove() {
        throw PlatformIOException("Http references are read only!")
    }

    override fun getLocalURI(): String {
        return uri
    }
}
