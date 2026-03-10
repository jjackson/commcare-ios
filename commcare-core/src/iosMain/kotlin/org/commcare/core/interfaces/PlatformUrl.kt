package org.commcare.core.interfaces

/**
 * Pure Kotlin URL parser for iOS.
 * Parses scheme://host:port/path?query format.
 */
actual class PlatformUrl actual constructor(url: String) {
    actual val scheme: String
    actual val host: String
    actual val port: Int
    actual val path: String
    actual val query: String?

    init {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) throw IllegalArgumentException("Malformed URL: $url")
        scheme = url.substring(0, schemeEnd)

        val afterScheme = url.substring(schemeEnd + 3)
        val pathStart = afterScheme.indexOf('/')
        val hostPort = if (pathStart >= 0) afterScheme.substring(0, pathStart) else afterScheme
        val pathAndQuery = if (pathStart >= 0) afterScheme.substring(pathStart) else ""

        val colonIdx = hostPort.indexOf(':')
        if (colonIdx >= 0) {
            host = hostPort.substring(0, colonIdx)
            port = hostPort.substring(colonIdx + 1).toIntOrNull() ?: -1
        } else {
            host = hostPort
            port = -1
        }

        val queryIdx = pathAndQuery.indexOf('?')
        if (queryIdx >= 0) {
            path = pathAndQuery.substring(0, queryIdx)
            query = pathAndQuery.substring(queryIdx + 1)
        } else {
            path = pathAndQuery
            query = null
        }
    }

    actual override fun toString(): String {
        val sb = StringBuilder()
        sb.append(scheme).append("://").append(host)
        if (port >= 0) sb.append(':').append(port)
        sb.append(path)
        if (query != null) sb.append('?').append(query)
        return sb.toString()
    }
}

actual fun isValidUrl(url: String): Boolean {
    return try {
        PlatformUrl(url)
        true
    } catch (e: Exception) {
        false
    }
}
