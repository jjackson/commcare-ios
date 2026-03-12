package org.commcare.resources.model.installers

import org.commcare.util.CommCarePlatform
import org.javarosa.core.reference.InvalidReferenceException
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * TODO: This should possibly just be replaced by a basic file installer along
 * with a reference for the login screen. We'll see.
 *
 * @author ctsims
 */
class LoginImageInstaller : BasicInstaller() {

    @Throws(
        PlatformIOException::class,
        InvalidReferenceException::class,
        InvalidStructureException::class,
        PlatformXmlParserException::class,
        UnfullfilledRequirementsException::class
    )
    override fun initialize(platform: CommCarePlatform, isUpgrade: Boolean): Boolean {
        //Tell the login screen where to get this?
        return true
    }

    override fun requiresRuntimeInitialization(): Boolean {
        return true
    }
}
