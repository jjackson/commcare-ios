package org.commcare.core.interfaces

import java.net.MalformedURLException
import java.net.URL

actual class PlatformUrl actual constructor(url: String) {
    private val javaUrl = URL(url)

    actual val scheme: String get() = javaUrl.protocol
    actual val host: String get() = javaUrl.host
    actual val port: Int get() = javaUrl.port
    actual val path: String get() = javaUrl.path
    actual val query: String? get() = javaUrl.query

    actual override fun toString(): String = javaUrl.toString()
}

actual fun isValidUrl(url: String): Boolean {
    return try {
        URL(url)
        true
    } catch (e: MalformedURLException) {
        false
    }
}
