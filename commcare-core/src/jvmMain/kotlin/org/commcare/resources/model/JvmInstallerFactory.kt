package org.commcare.resources.model

import org.commcare.resources.model.installers.XFormInstaller

/**
 * JVM-specific InstallerFactory that provides XFormInstaller (depends on kxml2).
 */
open class JvmInstallerFactory : InstallerFactory() {

    override fun getXFormInstaller(): ResourceInstaller<*> {
        return XFormInstaller()
    }
}
