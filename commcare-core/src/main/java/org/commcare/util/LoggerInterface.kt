package org.commcare.util

interface LoggerInterface {
    fun logError(message: String, cause: Exception)
    fun logError(message: String)
}
