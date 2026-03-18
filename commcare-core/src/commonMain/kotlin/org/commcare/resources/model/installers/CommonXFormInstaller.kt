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
import org.javarosa.core.io.PlatformInputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.xform.parse.XFormParseException
import org.javarosa.xform.util.XFormLoader
import org.javarosa.xml.util.UnfullfilledRequirementsException

/**
 * Cross-platform XFormInstaller using XFormLoader (works on both JVM and iOS).
 * Equivalent to the JVM-only XFormInstaller but uses the KMP XFormParser
 * instead of the kxml2-based XFormUtils.
 */
class CommonXFormInstaller : CacheInstaller<FormDef>() {

    companion object {
        private const val UPGRADE_EXT: String = "_TEMP"
        private const val STAGING_EXT: String = "_STAGING-OPENROSA"
        private val exts = arrayOf(UPGRADE_EXT, STAGING_EXT)
    }

    override fun getCacheKey(): String {
        return FormDef.STORAGE_KEY
    }

    @Throws(UnresolvedResourceException::class, UnfullfilledRequirementsException::class)
    override fun install(
        r: Resource, location: ResourceLocation, ref: Reference,
        table: ResourceTable, platform: CommCarePlatform,
        upgrade: Boolean, resourceInstallContext: ResourceInstallContext
    ): Boolean {
        var incoming: PlatformInputStream? = null
        try {
            if (location.getAuthority() == Resource.RESOURCE_AUTHORITY_CACHE) {
                return false
            } else {
                incoming = ref.getStream()
                if (incoming == null) {
                    return false
                }

                // Read entire stream into bytes for cross-platform XFormLoader
                val buffer = ByteArray(4096)
                val chunks = mutableListOf<ByteArray>()
                var totalSize = 0
                while (true) {
                    val bytesRead = incoming.read(buffer)
                    if (bytesRead == -1) break
                    chunks.add(buffer.copyOfRange(0, bytesRead))
                    totalSize += bytesRead
                }
                val xmlBytes = ByteArray(totalSize)
                var offset = 0
                for (chunk in chunks) {
                    chunk.copyInto(xmlBytes, offset)
                    offset += chunk.size
                }

                val formDef = XFormLoader.loadForm(xmlBytes)
                val instance = formDef.getInstance()!!
                if (upgrade) {
                    instance.schema = instance.schema + UPGRADE_EXT
                    storage(platform).write(formDef)
                    cacheLocation = formDef.getID()
                    table.commit(r, Resource.RESOURCE_STATUS_UPGRADE)
                } else {
                    storage(platform).write(formDef)
                    cacheLocation = formDef.getID()
                    table.commit(r, Resource.RESOURCE_STATUS_INSTALLED)
                }
                return true
            }
        } catch (e: PlatformIOException) {
            throw UnreliableSourceException(r, e.message)
        } catch (xpe: XFormParseException) {
            throw UnresolvedResourceException(r, xpe.message, true)
        } finally {
            try {
                incoming?.close()
            } catch (e: PlatformIOException) {
            }
        }
    }

    @Throws(UnresolvedResourceException::class)
    override fun upgrade(r: Resource, platform: CommCarePlatform): Boolean {
        val form = storage(platform).read(cacheLocation)
        val instance = form.getInstance()!!
        val tempString = instance.schema ?: return true

        if (tempString.contains(UPGRADE_EXT)) {
            instance.schema = tempString.substring(0, tempString.indexOf(UPGRADE_EXT))
            storage(platform).write(form)
        }
        return true
    }

    override fun unstage(r: Resource, newStatus: Int, platform: CommCarePlatform): Boolean {
        val destination = if (newStatus == Resource.RESOURCE_STATUS_UNSTAGED) {
            STAGING_EXT
        } else {
            UPGRADE_EXT
        }

        val form = storage(platform).read(cacheLocation)
        val instance = form.getInstance()!!
        val tempString = instance.schema ?: ""

        if (tempString.contains(destination)) {
            return true
        } else {
            instance.schema = tempString + destination
            storage(platform).write(form)
            return true
        }
    }

    override fun revert(r: Resource, table: ResourceTable, platform: CommCarePlatform): Boolean {
        val form = storage(platform).read(cacheLocation)
        val instance = form.getInstance()!!
        val tempString = instance.schema ?: return true

        for (ext in exts) {
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
        r: Resource, problemList: ArrayList<MissingMediaException>,
        platform: CommCarePlatform
    ): Boolean {
        @Suppress("UNCHECKED_CAST")
        val sizeBoundProblems = problemList as SizeBoundUniqueVector<MissingMediaException>

        try {
            storage(platform).read(cacheLocation)
        } catch (e: Exception) {
            sizeBoundProblems.add(
                MissingMediaException(
                    r, "Form did not properly save into persistent storage",
                    MissingMediaException.MissingMediaExceptionType.NONE
                )
            )
            return true
        }
        return false
    }
}
