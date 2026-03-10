package org.commcare.core.interfaces

/**
 * Cross-platform URL parsing and validation.
 * Replaces java.net.URL for KMP compatibility.
 */
expect class PlatformUrl(url: String) {
    val scheme: String
    val host: String
    val port: Int
    val path: String
    val query: String?
    override fun toString(): String
}

/**
 * Returns true if the given string is a valid URL.
 */
expect fun isValidUrl(url: String): Boolean
