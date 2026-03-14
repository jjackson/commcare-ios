package org.commcare.test

/**
 * Cross-platform test resource loader. Loads test fixture files as ByteArray.
 *
 * JVM: uses classloader resource loading.
 * iOS: not yet available — depends on XFormLoader iOS implementation.
 */
expect object TestResources {
    fun loadResource(path: String): ByteArray
}
