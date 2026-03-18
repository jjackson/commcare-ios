package org.commcare.resources.model

import org.commcare.resources.model.installers.CommonXFormInstaller
import org.commcare.resources.model.installers.LocaleFileInstaller
import org.commcare.resources.model.installers.LoginImageInstaller
import org.commcare.resources.model.installers.MediaInstaller
import org.commcare.resources.model.installers.OfflineUserRestoreInstaller
import org.commcare.resources.model.installers.ProfileInstaller
import org.commcare.resources.model.installers.SuiteInstaller

/**
 * @author ctsims
 */
open class InstallerFactory {

    open fun getProfileInstaller(forceInstall: Boolean): ResourceInstaller<*> {
        return ProfileInstaller(forceInstall)
    }

    open fun getXFormInstaller(): ResourceInstaller<*> {
        return CommonXFormInstaller()
    }

    open fun getUserRestoreInstaller(): ResourceInstaller<*> {
        return OfflineUserRestoreInstaller()
    }

    open fun getSuiteInstaller(): ResourceInstaller<*> {
        return SuiteInstaller()
    }

    open fun getLocaleFileInstaller(locale: String): ResourceInstaller<*> {
        return LocaleFileInstaller(locale)
    }

    open fun getLoginImageInstaller(): ResourceInstaller<*> {
        return LoginImageInstaller()
    }

    open fun getMediaInstaller(path: String): ResourceInstaller<*> {
        return MediaInstaller()
    }

    open fun getXFormUpdateInfoInstaller(): ResourceInstaller<*> {
        return getXFormInstaller()
    }
}
