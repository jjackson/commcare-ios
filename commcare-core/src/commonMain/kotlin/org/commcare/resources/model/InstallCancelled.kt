package org.commcare.resources.model

interface InstallCancelled {
    /**
     * @return was the resource install process cancelled by the user or system?
     */
    fun wasInstallCancelled(): Boolean
}
