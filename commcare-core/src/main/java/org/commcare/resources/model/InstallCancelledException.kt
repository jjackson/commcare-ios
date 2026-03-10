package org.commcare.resources.model

/**
 * Exception signifying that the user or system cancelled a running update/install
 */
class InstallCancelledException(message: String) : Exception(message)
