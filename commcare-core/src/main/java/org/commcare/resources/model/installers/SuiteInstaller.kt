package org.commcare.resources.model.installers

import org.commcare.resources.ResourceInstallContext
import org.commcare.resources.model.MissingMediaException
import org.commcare.resources.model.Resource
import org.commcare.resources.model.ResourceLocation
import org.commcare.resources.model.ResourceTable
import org.commcare.resources.model.UnreliableSourceException
import org.commcare.resources.model.UnresolvedResourceException
import org.commcare.suite.model.Suite
import org.commcare.util.CommCarePlatform
import org.commcare.xml.SuiteParser
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.reference.InvalidReferenceException
import org.javarosa.core.reference.Reference
import org.javarosa.core.services.locale.Localization
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.util.SizeBoundUniqueVector
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * @author ctsims
 */
class SuiteInstaller : CacheInstaller<Suite>() {

    @Throws(
        PlatformIOException::class,
        InvalidReferenceException::class,
        InvalidStructureException::class,
        PlatformXmlParserException::class,
        UnfullfilledRequirementsException::class
    )
    override fun initialize(platform: CommCarePlatform, isUpgrade: Boolean): Boolean {
        platform.registerSuite(storage(platform).read(cacheLocation))
        return true
    }

    override fun requiresRuntimeInitialization(): Boolean {
        return true
    }

    override fun getCacheKey(): String {
        return Suite.STORAGE_KEY
    }

    @Throws(UnresolvedResourceException::class, UnfullfilledRequirementsException::class)
    override fun install(
        r: Resource, location: ResourceLocation, ref: Reference,
        table: ResourceTable, platform: CommCarePlatform,
        upgrade: Boolean, resourceInstallContext: ResourceInstallContext
    ): Boolean {
        if (location.getAuthority() == Resource.RESOURCE_AUTHORITY_CACHE) {
            //If it's in the cache, we should just get it from there
            return false
        } else {
            var incoming: java.io.InputStream? = null
            try {
                incoming = ref.getStream()
                @Suppress("UNCHECKED_CAST")
                val parser = SuiteParser(
                    incoming, table, r.getRecordGuid(),
                    platform.getStorageManager()!!.getStorage("fixture") as IStorageUtilityIndexed<FormInstance>
                )
                if (location.getAuthority() == Resource.RESOURCE_AUTHORITY_REMOTE) {
                    parser.setMaximumAuthority(Resource.RESOURCE_AUTHORITY_REMOTE)
                }
                val s = parser.parse()
                storage(platform).write(s)
                cacheLocation = s.getID()

                table.commitCompoundResource(r, Resource.RESOURCE_STATUS_INSTALLED)

                // TODO: Add a resource location for r for its cache location
                // so it can be uninstalled appropriately.
                return true
            } catch (e: InvalidStructureException) {
                throw UnresolvedResourceException(r, e.message, true)
            } catch (e: PlatformIOException) {
                val exception = UnreliableSourceException(r, e.message)
                exception.initCause(e)
                throw exception
            } catch (e: PlatformXmlParserException) {
                e.printStackTrace()
                return false
            } finally {
                try {
                    incoming?.close()
                } catch (e: PlatformIOException) {
                }
            }
        }
    }

    override fun verifyInstallation(
        r: Resource, problemList: ArrayList<MissingMediaException>,
        platform: CommCarePlatform
    ): Boolean {
        @Suppress("UNCHECKED_CAST")
        val sizeBoundProblems = problemList as SizeBoundUniqueVector<MissingMediaException>

        InstallerUtil.checkMedia(r, Localization.get("icon.demo.path"), sizeBoundProblems, InstallerUtil.MediaType.IMAGE)
        InstallerUtil.checkMedia(r, Localization.get("icon.login.path"), sizeBoundProblems, InstallerUtil.MediaType.IMAGE)

        //Check to see whether the formDef exists and reads correctly
        val suite: Suite
        try {
            suite = storage(platform).read(cacheLocation)
        } catch (e: Exception) {
            e.printStackTrace()
            sizeBoundProblems.add(
                MissingMediaException(
                    r, "Suite did not properly save into persistent storage",
                    MissingMediaException.MissingMediaExceptionType.NONE
                )
            )
            return true
        }
        //Otherwise, we want to figure out if the form has media, and we need to see whether it's properly
        //available
        try {
            for (menu in suite.getMenus()) {
                val aURI = menu.getAudioURI()
                if (aURI != null) {
                    InstallerUtil.checkMedia(r, aURI, sizeBoundProblems, InstallerUtil.MediaType.AUDIO)
                }

                val iURI = menu.getImageURI()
                if (iURI != null) {
                    InstallerUtil.checkMedia(r, iURI, sizeBoundProblems, InstallerUtil.MediaType.IMAGE)
                }
            }
        } catch (exc: Exception) {
            System.out.println("fail: " + exc.message)
            System.out.println("fail: " + exc.toString())
        }
        return problemList.size != 0
    }
}
