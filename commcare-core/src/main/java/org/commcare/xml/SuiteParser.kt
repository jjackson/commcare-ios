package org.commcare.xml

import org.commcare.resources.model.Resource
import org.commcare.resources.model.ResourceInstaller
import org.commcare.resources.model.ResourceTable
import org.commcare.suite.model.Detail
import org.commcare.suite.model.Endpoint
import org.commcare.suite.model.Entry
import org.commcare.suite.model.Menu
import org.commcare.suite.model.Suite
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.kxml2.io.KXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import java.io.InputStream

/**
 * Parses a suite file resource and creates the associated object
 * containing the menu, detail, entry, etc definitions. This parser
 * will also create models for any resource installers that are defined
 * by the suite file and add them to the resource table provided
 * with the suite resource as the parent, that behavior can be skipped
 * by setting a flag if the resources have already been promised.
 *
 * @author ctsims
 */
open class SuiteParser : ElementParser<Suite> {
    private val fixtureStorage: IStorageUtilityIndexed<FormInstance>

    private var table: ResourceTable
    private var resourceGuid: String
    private var maximumResourceAuthority = -1

    /**
     * If set to true, the parser won't process adding incoming resources to the resource table.
     * This is helpful if the suite is being processed during a non-install phase
     */
    private val skipResources: Boolean
    private val isValidationPass: Boolean
    private val isUpgrade: Boolean

    @Throws(PlatformIOException::class)
    constructor(
        suiteStream: InputStream,
        table: ResourceTable,
        resourceGuid: String,
        fixtureStorage: IStorageUtilityIndexed<FormInstance>
    ) : super(ElementParser.instantiateParser(suiteStream)) {
        this.table = table
        this.resourceGuid = resourceGuid
        this.fixtureStorage = fixtureStorage
        this.skipResources = false
        this.isValidationPass = false
        this.isUpgrade = false
    }

    @Throws(PlatformIOException::class)
    protected constructor(
        suiteStream: InputStream,
        table: ResourceTable, resourceGuid: String,
        fixtureStorage: IStorageUtilityIndexed<FormInstance>,
        skipResources: Boolean, isValidationPass: Boolean,
        isUpgrade: Boolean
    ) : super(ElementParser.instantiateParser(suiteStream)) {
        this.table = table
        this.resourceGuid = resourceGuid
        this.fixtureStorage = fixtureStorage
        this.skipResources = skipResources
        this.isValidationPass = isValidationPass
        this.isUpgrade = isUpgrade
    }

    @Throws(
        InvalidStructureException::class, PlatformIOException::class,
        PlatformXmlParserException::class, UnfullfilledRequirementsException::class
    )
    override fun parse(): Suite {
        checkNode("suite")

        val sVersion = parser.getAttributeValue(null, "version")
        val version = Integer.parseInt(sVersion)
        val details = HashMap<String, Detail>()
        val entries = HashMap<String, Entry>()

        val menus = ArrayList<Menu>()
        val endpoints = HashMap<String, Endpoint>()

        try {
            parser.next()

            var eventType = parser.eventType
            do {
                if (eventType == KXmlParser.START_TAG) {
                    val tagName = parser.name.lowercase()
                    when (tagName) {
                        "entry" -> {
                            val entry = EntryParser.buildEntryParser(parser).parse()
                            entries[entry.commandId!!] = entry
                        }
                        "view" -> {
                            val viewEntry = EntryParser.buildViewParser(parser).parse()
                            entries[viewEntry.commandId!!] = viewEntry
                        }
                        EntryParser.REMOTE_REQUEST_TAG -> {
                            val remoteRequestEntry = EntryParser.buildRemoteSyncParser(parser).parse()
                            entries[remoteRequestEntry.commandId!!] = remoteRequestEntry
                        }
                        "locale" -> {
                            val localeKey = parser.getAttributeValue(null, "language")
                            parser.nextTag()
                            val localeResource = ResourceParser(parser, maximumResourceAuthority).parse()
                            if (!skipResources) {
                                table.addResource(
                                    localeResource,
                                    table.getInstallers().getLocaleFileInstaller(localeKey),
                                    resourceGuid
                                )
                            }
                        }
                        "media" -> {
                            val path = parser.getAttributeValue(null, "path")
                            while (this.nextTagInBlock("media")) {
                                val mediaResource = ResourceParser(parser, maximumResourceAuthority).parse()
                                if (!skipResources) {
                                    table.addResource(
                                        mediaResource,
                                        table.getInstallers().getMediaInstaller(path),
                                        resourceGuid
                                    )
                                }
                            }
                        }
                        "xform-update-info", "xform" -> {
                            parser.nextTag()
                            val xformResource = ResourceParser(parser, maximumResourceAuthority).parse()
                            if (!skipResources) {
                                val resourceInstaller: ResourceInstaller<*> =
                                    if (tagName.contentEquals("xform-update-info"))
                                        table.getInstallers().getXFormUpdateInfoInstaller()
                                    else
                                        table.getInstallers().getXFormInstaller()
                                table.addResource(xformResource, resourceInstaller, resourceGuid)
                            }
                        }
                        "user-restore" -> {
                            parser.nextTag()
                            val userRestoreResource = ResourceParser(parser, maximumResourceAuthority).parse()
                            if (!skipResources) {
                                table.addResource(
                                    userRestoreResource,
                                    table.getInstallers().getUserRestoreInstaller(),
                                    resourceGuid
                                )
                            }
                        }
                        "detail" -> {
                            val d = getDetailParser().parse()
                            details[d.id!!] = d
                        }
                        "menu" -> {
                            val m = MenuParser(parser).parse()
                            menus.add(m)
                        }
                        "fixture" -> {
                            if (!isValidationPass) {
                                FixtureXmlParser(parser, isUpgrade, fixtureStorage).parse()
                            }
                        }
                        EndpointParser.NAME_ENDPOINT -> {
                            val endpoint = EndpointParser(parser).parse()
                            endpoints[endpoint.id!!] = endpoint
                        }
                        else -> System.out.println("Unrecognized Tag: ${parser.name}")
                    }
                }
                eventType = parser.next()
            } while (eventType != KXmlParser.END_DOCUMENT)

            return Suite(version, details, entries, menus, endpoints)
        } catch (e: PlatformXmlParserException) {
            e.printStackTrace()
            throw InvalidStructureException("Pull Parse Exception, malformed XML.", parser)
        }
    }

    fun setMaximumAuthority(authority: Int) {
        maximumResourceAuthority = authority
    }

    protected open fun getDetailParser(): DetailParser {
        return DetailParser(parser)
    }
}
