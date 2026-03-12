package org.commcare.resources.model.installers

import org.commcare.resources.ResourceInstallContext
import org.commcare.resources.model.MissingMediaException
import org.commcare.resources.model.Resource
import org.commcare.resources.model.ResourceInstaller
import org.commcare.resources.model.ResourceLocation
import org.commcare.resources.model.ResourceTable
import org.commcare.resources.model.UnreliableSourceException
import org.commcare.resources.model.UnresolvedResourceException
import org.commcare.util.CommCarePlatform
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.io.StreamsUtil.InputIOException
import org.javarosa.core.reference.InvalidReferenceException
import org.javarosa.core.reference.Reference
import org.javarosa.core.reference.ReferenceManager
import org.javarosa.core.services.locale.Localization
import org.javarosa.core.services.locale.LocalizationUtils
import org.javarosa.core.services.locale.TableLocaleSource
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.io.PlatformInputStream

/**
 * @author ctsims
 */
class LocaleFileInstaller : ResourceInstaller<CommCarePlatform> {

    private var locale: String? = null
    private var localReference: String? = null

    private var cache: MutableMap<String, String>? = null

    companion object {
        private const val valid: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    }

    /**
     * Serialization only!
     */
    @Suppress("unused")
    constructor()

    constructor(locale: String) {
        this.locale = locale
        this.localReference = ""
    }

    @Throws(
        PlatformIOException::class,
        InvalidReferenceException::class,
        InvalidStructureException::class,
        PlatformXmlParserException::class,
        UnfullfilledRequirementsException::class
    )
    override fun initialize(platform: CommCarePlatform, isUpgrade: Boolean): Boolean {
        val currentCache = cache
        if (currentCache == null) {
            Localization.registerLanguageReference(locale!!, localReference!!)
        } else {
            Localization.getGlobalLocalizerAdvanced().addAvailableLocale(locale!!)
            Localization.getGlobalLocalizerAdvanced().registerLocaleResource(locale, TableLocaleSource(currentCache))
        }
        return true
    }

    override fun requiresRuntimeInitialization(): Boolean {
        return true
    }

    @Throws(UnresolvedResourceException::class)
    override fun install(
        r: Resource, location: ResourceLocation,
        ref: Reference, table: ResourceTable,
        platform: CommCarePlatform, upgrade: Boolean,
        resourceInstallContext: ResourceInstallContext
    ): Boolean {
        //If we have local resource authority, and the file exists, things are golden. We can just use that file.
        if (location.getAuthority() == Resource.RESOURCE_AUTHORITY_LOCAL) {
            try {
                if (ref.doesBinaryExist()) {
                    localReference = ref.getURI()
                    table.commit(r, Resource.RESOURCE_STATUS_INSTALLED)
                    return true
                } else {
                    //If the file isn't there, not much we can do about it.
                    return false
                }
            } catch (e: PlatformIOException) {
                e.printStackTrace()
                return false
            }
        } else if (location.getAuthority() == Resource.RESOURCE_AUTHORITY_REMOTE) {
            //We need to download the resource, and store it locally. Either in the cache
            //(if no resource location is available) or in a local reference if one exists.
            var incoming: PlatformInputStream? = null
            try {
                if (!ref.doesBinaryExist()) {
                    return false
                }
                incoming = ref.getStream()
                if (incoming == null) {
                    //if it turns out there isn't actually a remote resource, bail.
                    return false
                }

                //Now we're going to try to find a local location to put the resource.
                //Start with an arbitrary file location (since we don't support destination
                //information yet, which we probably should soon).
                var uri = ref.getURI()
                val lastslash = uri.lastIndexOf('/')

                //Now we have a local part reference
                uri = uri.substring(if (lastslash == -1) 0 else lastslash + 1)

                var cleanUri = ""
                //clean the uri ending. NOTE: This should be replaced with a link to a more
                //robust uri cleaning subroutine
                for (i in uri.indices) {
                    val c = uri[i]
                    cleanUri += if (valid.indexOf(c) == -1) {
                        "_"
                    } else {
                        c
                    }
                }

                uri = cleanUri

                var copy = 0

                try {
                    var destination = ReferenceManager.instance().DeriveReference("jr://file/$uri")
                    while (destination.doesBinaryExist()) {
                        //Need a different location.
                        copy++
                        val newUri = "$uri.$copy"
                        destination = ReferenceManager.instance().DeriveReference("jr://file/$newUri")
                    }

                    if (destination.isReadOnly()) {
                        return cache(incoming, r, table, upgrade)
                    }
                    //destination is now a valid local reference, so we can store the file there.

                    val output = destination.getOutputStream()
                    try {
                        //We're now reading from incoming, so if this fails, we need to ensure that it is closed
                        StreamsUtil.writeFromInputToOutputSpecific(incoming, output)
                    } catch (e: InputIOException) {
                        //TODO: This won't necessarily catch issues with the _output)
                        //stream failing. Test for that.
                        throw UnreliableSourceException(r, e.message)
                    } finally {
                        output.close()
                    }

                    this.localReference = destination.getURI()
                    if (upgrade) {
                        table.commit(r, Resource.RESOURCE_STATUS_UPGRADE)
                    } else {
                        table.commit(r, Resource.RESOURCE_STATUS_INSTALLED)
                    }
                    return true
                } catch (e: InvalidReferenceException) {
                    //Local location doesn't exist, put this in the cache
                    return cache(ref.getStream(), r, table, upgrade)
                } catch (e: PlatformIOException) {
                    //This is a catch-all for local references failing in unexpected ways.
                    return cache(ref.getStream(), r, table, upgrade)
                }
            } catch (e: PlatformIOException) {
                val exception = UnreliableSourceException(r, e.message)
                throw exception
            } finally {
                try {
                    incoming?.close()
                } catch (e: PlatformIOException) {
                }
            }

            //TODO: Implement local cache code
            //    return false;
        }
        return false
    }

    @Throws(UnresolvedResourceException::class)
    private fun cache(
        incoming: PlatformInputStream, r: Resource,
        table: ResourceTable, upgrade: Boolean
    ): Boolean {
        //NOTE: Incoming here needs to be _fresh_. It's extremely important that
        //nothing have gotten the stream first

        try {
            cache = LocalizationUtils.parseLocaleInput(incoming)
            table.commit(
                r,
                if (upgrade) Resource.RESOURCE_STATUS_UPGRADE else Resource.RESOURCE_STATUS_INSTALLED
            )
            return true
        } catch (e: PlatformIOException) {
            throw UnreliableSourceException(r, e.message)
        } finally {
            try {
                incoming.close()
            } catch (e: PlatformIOException) {
            }
        }
    }

    @Throws(UnresolvedResourceException::class)
    override fun upgrade(r: Resource, platform: CommCarePlatform): Boolean {
        //TODO: Rename file to take off ".N"?
        return true
    }

    override fun unstage(r: Resource, newStatus: Int, platform: CommCarePlatform): Boolean {
        return true
    }

    override fun revert(r: Resource, table: ResourceTable, platform: CommCarePlatform): Boolean {
        return true
    }

    override fun rollback(r: Resource, platform: CommCarePlatform): Int {
        //This does nothing
        return Resource.getCleanFlag(r.getStatus())
    }

    @Throws(UnresolvedResourceException::class)
    override fun uninstall(r: Resource, platform: CommCarePlatform): Boolean {
        //If we're not using files, just deal with the cache (this is even likely unnecessary).
        if (cache != null) {
            cache!!.clear()
            cache = null
            return true
        }
        try {
            val reference = ReferenceManager.instance().DeriveReference(localReference)
            if (!reference.isReadOnly()) {
                reference.remove()
            }
            //CTS: The table should take care of this for the installer
            //table.removeResource(r);
            return true
        } catch (e: InvalidReferenceException) {
            e.printStackTrace()
            throw UnresolvedResourceException(
                r,
                "Could not resolve locally installed reference at$localReference"
            )
        } catch (e: PlatformIOException) {
            e.printStackTrace()
            throw UnresolvedResourceException(
                r,
                "Problem removing local data at reference $localReference"
            )
        }
    }

    override fun cleanup() {
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        locale = SerializationHelpers.readString(`in`)
        localReference = SerializationHelpers.readString(`in`)
        val readCache = SerializationHelpers.readStringStringMap(`in`)
        cache = if (readCache.isEmpty()) null else readCache
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeString(out, locale ?: "")
        SerializationHelpers.writeString(out, localReference ?: "")
        SerializationHelpers.writeMap(out, cache ?: HashMap<String, String>())
    }

    override fun verifyInstallation(
        r: Resource, problemList: ArrayList<MissingMediaException>,
        platform: CommCarePlatform
    ): Boolean {
        try {
            if (locale == null) {
                problemList.add(
                    MissingMediaException(
                        r, "Bad metadata, no locale",
                        MissingMediaException.MissingMediaExceptionType.NONE
                    )
                )
                return true
            }
            if (cache != null) {
                //If we've gotten the cache into memory, we're fine
            } else {
                try {
                    if (!ReferenceManager.instance().DeriveReference(localReference).doesBinaryExist()) {
                        throw MissingMediaException(
                            r, "Locale data does note exist at: $localReference", localReference,
                            MissingMediaException.MissingMediaExceptionType.FILE_NOT_FOUND
                        )
                    }
                } catch (e: PlatformIOException) {
                    throw MissingMediaException(
                        r, "Problem reading locale data from: $localReference", localReference,
                        MissingMediaException.MissingMediaExceptionType.FILE_NOT_ACCESSIBLE
                    )
                } catch (e: InvalidReferenceException) {
                    throw MissingMediaException(
                        r, "Locale reference is invalid: $localReference", localReference,
                        MissingMediaException.MissingMediaExceptionType.INVALID_REFERENCE
                    )
                }
            }
        } catch (ure: MissingMediaException) {
            problemList.add(ure)
            return true
        }
        return false
    }
}
