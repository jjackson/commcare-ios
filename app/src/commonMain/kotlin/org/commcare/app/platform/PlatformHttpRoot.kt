package org.commcare.app.platform

import org.javarosa.core.reference.ReferenceFactory

/**
 * Returns the platform-specific HTTP reference factory for resolving
 * http:// and https:// URIs in the ReferenceManager.
 */
expect fun createHttpReferenceFactory(): ReferenceFactory
