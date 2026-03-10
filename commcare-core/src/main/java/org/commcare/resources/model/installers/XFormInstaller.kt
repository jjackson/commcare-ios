package org.commcare.resources.model.installers

import org.commcare.resources.ResourceInstallContext
import org.commcare.resources.model.MissingMediaException
import org.commcare.resources.model.Resource
import org.commcare.resources.model.ResourceLocation
import org.commcare.resources.model.ResourceTable
import org.commcare.resources.model.UnreliableSourceException
import org.commcare.resources.model.UnresolvedResourceException
import org.commcare.util.CommCarePlatform
import org.javarosa.core.model.FormDef
import org.javarosa.core.reference.Reference
import org.javarosa.core.util.SizeBoundUniqueVector
import org.javarosa.form.api.FormEntryCaption
import org.javarosa.xform.parse.XFormParseException
import org.javarosa.xform.util.XFormUtils
import java.io.IOException
import java.io.InputStreamReader
import java.util.Vector

/**
 * @author ctsims
 */
class XFormInstaller : CacheInstaller<FormDef>() {

    companion object {
        private const val UPGRADE_EXT: String = "_TEMP"
        private const val STAGING_EXT: String = "_STAGING-OPENROSA"
        private val exts = arrayOf(UPGRADE_EXT, STAGING_EXT)
    }

    override fun getCacheKey(): String {
        return FormDef.STORAGE_KEY
    }

    @Throws(UnresolvedResourceException::class)
    override fun install(
        r: Resource, location: ResourceLocation, ref: Reference,
        table: ResourceTable, platform: CommCarePlatform,
        upgrade: Boolean, resourceInstallContext: ResourceInstallContext
    ): Boolean {
        var incoming: java.io.InputStream? = null
        try {
            if (location.getAuthority() == Resource.RESOURCE_AUTHORITY_CACHE) {
                //If it's in the cache, we should just get it from there
                return false
            } else {
                incoming = ref.getStream()
                if (incoming == null) {
                    return false
                }
                val formDef = XFormUtils.getFormRaw(InputStreamReader(incoming, "UTF-8"))
                if (formDef == null) {
                    //Bad Form!
                    return false
                }
                val instance = formDef.getInstance()!!
                if (upgrade) {
                    //There's already a record in the cache with this namespace, so we can't overwrite it.
                    //TODO: If something broke, this record might already exist. Might be worth checking.
                    instance.schema = instance.schema + UPGRADE_EXT
                    storage(platform).write(formDef)
                    cacheLocation = formDef.getID()

                    //Resource is installed and ready for upgrade
                    table.commit(r, Resource.RESOURCE_STATUS_UPGRADE)
                } else {
                    storage(platform).write(formDef)
                    cacheLocation = formDef.getID()
                    //Resource is fully installed
                    table.commit(r, Resource.RESOURCE_STATUS_INSTALLED)
                }
                return true
            }
        } catch (e: IOException) {
            val exception = UnreliableSourceException(r, e.message)
            exception.initCause(e)
            throw exception
        } catch (xpe: XFormParseException) {
            throw UnresolvedResourceException(r, xpe.message, true)
        } finally {
            try {
                incoming?.close()
            } catch (e: IOException) {
            }
        }
    }

    @Throws(UnresolvedResourceException::class)
    override fun upgrade(r: Resource, platform: CommCarePlatform): Boolean {
        //Basically some content as revert. Merge;
        val form = storage(platform).read(cacheLocation)
        val instance = form.getInstance()!!
        val tempString = instance.schema ?: return true

        //Atomic. Don't re-do this if it was already done.
        if (tempString.contains(UPGRADE_EXT)) {
            instance.schema = tempString.substring(0, tempString.indexOf(UPGRADE_EXT))
            storage(platform).write(form)
        }
        return true
    }

    override fun unstage(r: Resource, newStatus: Int, platform: CommCarePlatform): Boolean {
        //This either unstages back to upgrade mode or
        //to unstaged mode. Figure out which one
        val destination = if (newStatus == Resource.RESOURCE_STATUS_UNSTAGED) {
            STAGING_EXT
        } else {
            UPGRADE_EXT
        }

        //Make sure that this form's
        val form = storage(platform).read(cacheLocation)
        val instance = form.getInstance()!!
        val tempString = instance.schema ?: ""

        //This method should basically be atomic, so don't re-temp it if it's already
        //temp'd.
        if (tempString.contains(destination)) {
            return true
        } else {
            instance.schema = tempString + destination
            storage(platform).write(form)
            return true
        }
    }

    override fun revert(r: Resource, table: ResourceTable, platform: CommCarePlatform): Boolean {
        //Basically some content as upgrade. Merge;
        val form = storage(platform).read(cacheLocation)
        val instance = form.getInstance()!!
        val tempString = instance.schema ?: return true

        //TODO: Aggressively wipe out anything which might conflict with the uniqueness
        //of the new schema

        for (ext in exts) {
            //Removing any staging/upgrade placeholders.
            if (tempString.contains(ext)) {
                instance.schema = tempString.substring(0, tempString.indexOf(ext))
                storage(platform).write(form)
            }
        }
        return true
    }

    override fun rollback(r: Resource, platform: CommCarePlatform): Int {
        val status = r.getStatus()

        val form = storage(platform).read(cacheLocation)
        val currentSchema = form.getInstance()!!.schema ?: ""

        //Just figure out whether we finished and return that
        return when (status) {
            Resource.RESOURCE_STATUS_INSTALL_TO_UNSTAGE,
            Resource.RESOURCE_STATUS_UNSTAGE_TO_INSTALL -> {
                if (currentSchema.contains(STAGING_EXT)) {
                    Resource.RESOURCE_STATUS_UNSTAGED
                } else {
                    Resource.RESOURCE_STATUS_INSTALLED
                }
            }
            Resource.RESOURCE_STATUS_UPGRADE_TO_INSTALL,
            Resource.RESOURCE_STATUS_INSTALL_TO_UPGRADE -> {
                if (currentSchema.contains(UPGRADE_EXT)) {
                    Resource.RESOURCE_STATUS_UPGRADE
                } else {
                    Resource.RESOURCE_STATUS_INSTALLED
                }
            }
            else -> throw RuntimeException("Unexpected status for rollback! $status")
        }
    }

    override fun verifyInstallation(
        r: Resource, problemList: Vector<MissingMediaException>,
        platform: CommCarePlatform
    ): Boolean {
        @Suppress("UNCHECKED_CAST")
        val sizeBoundProblems = problemList as SizeBoundUniqueVector<MissingMediaException>

        //Check to see whether the formDef exists and reads correctly
        val formDef: FormDef
        try {
            formDef = storage(platform).read(cacheLocation)
        } catch (e: Exception) {
            sizeBoundProblems.addElement(
                MissingMediaException(
                    r, "Form did not properly save into persistent storage",
                    MissingMediaException.MissingMediaExceptionType.NONE
                )
            )
            return true
        }
        //Otherwise, we want to figure out if the form has media, and we need to see whether it's properly
        //available
        val localizer = formDef.getLocalizer()
        //get this out of the memory ASAP!
        if (localizer == null) {
            //things are fine
            return false
        }

        for (locale in localizer.availableLocales) {
            val localeData = localizer.getLocaleData(locale) ?: continue
            for (key in localeData.keys) {
                if (key.contains(";")) {
                    //got some forms here
                    val form = key.substring(key.indexOf(";") + 1, key.length)

                    if (form == FormEntryCaption.TEXT_FORM_VIDEO) {
                        val externalMedia = localeData[key]
                        if (externalMedia != null) {
                            InstallerUtil.checkMedia(r, externalMedia, sizeBoundProblems, InstallerUtil.MediaType.VIDEO)
                        }
                    }

                    if (form == FormEntryCaption.TEXT_FORM_IMAGE) {
                        val externalMedia = localeData[key]
                        if (externalMedia != null) {
                            InstallerUtil.checkMedia(r, externalMedia, sizeBoundProblems, InstallerUtil.MediaType.IMAGE)
                        }
                    }

                    if (form == FormEntryCaption.TEXT_FORM_AUDIO) {
                        val externalMedia = localeData[key]
                        if (externalMedia != null) {
                            InstallerUtil.checkMedia(r, externalMedia, sizeBoundProblems, InstallerUtil.MediaType.AUDIO)
                        }
                    }
                }
            }
        }
        return sizeBoundProblems.size != 0
    }
}
