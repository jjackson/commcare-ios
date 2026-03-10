package org.commcare.resources.model

/**
 * Register resource table installation success and failure stats.
 */
interface InstallStatsLogger {
    fun recordResourceInstallFailure(resourceName: String,
                                     errorMsg: Exception)

    fun recordResourceInstallSuccess(resourceName: String)
}
