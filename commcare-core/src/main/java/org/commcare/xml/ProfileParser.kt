package org.commcare.xml

import org.commcare.cases.util.StringUtils
import org.commcare.resources.model.Resource
import org.commcare.resources.model.ResourceTable
import org.commcare.suite.model.AndroidPackageDependency
import org.commcare.suite.model.Credential
import org.commcare.suite.model.Profile
import org.commcare.util.CommCarePlatform
import org.javarosa.core.reference.RootTranslator
import org.javarosa.core.util.PropertyUtils
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.kxml2.io.KXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.io.PlatformInputStream

/**
 * @author ctsims
 */
class ProfileParser(
    suiteStream: PlatformInputStream,
    private val instance: CommCarePlatform?,
    private var table: ResourceTable,
    private var resourceId: String,
    private var initialResourceStatus: Int,
    private var forceVersion: Boolean
) : ElementParser<Profile>(ElementParser.instantiateParser(suiteStream)) {

    companion object {
        private const val NAME_DEPENDENCIES = "dependencies"
        private const val NAME_ANDROID_PACKAGE = "android_package"
        private const val NAME_CREDENTIALS = "credentials"
        private const val NAME_CREDENTIAL = "credential"
        private const val ATTR_ID = "id"
        private const val ATTR_CREDENTIAL_LEVEL = "level"
        private const val ATTR_CREDENTIAL_TYPE = "type"
    }

    private var maximumResourceAuthority = -1

    @Throws(
        InvalidStructureException::class, PlatformIOException::class,
        PlatformXmlParserException::class, UnfullfilledRequirementsException::class
    )
    override fun parse(): Profile {
        checkNode("profile")
        val profile = parseProfileElement()

        try {
            parser.next()
            var eventType: Int
            eventType = parser.eventType
            do {
                if (eventType == KXmlParser.START_TAG) {
                    when (parser.name.lowercase()) {
                        "property" -> parseProperty(profile)
                        "root" -> {
                            val root = RootParser(this.parser).parse()
                            profile.addRoot(root)
                        }
                        "login" -> parseLogin()
                        "features" -> parseFeatures(profile)
                        "suite" -> parseSuite()
                        else -> System.out.println("Unrecognized Tag: ${parser.name}")
                    }
                }
                eventType = parser.next()
            } while (eventType != KXmlParser.END_DOCUMENT)

            return profile
        } catch (e: PlatformXmlParserException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
            throw InvalidStructureException("Pull Parse Exception, malformed XML.", parser)
        }
    }

    @Throws(InvalidStructureException::class, UnfullfilledRequirementsException::class)
    private fun parseProfileElement(): Profile {
        val version = parseInt(parser.getAttributeValue(null, "version"))
        val authRef = parser.getAttributeValue(null, "update")
        val sMajor = parser.getAttributeValue(null, "requiredMajor")
        val sMinor = parser.getAttributeValue(null, "requiredMinor")
        val sMinimal = parser.getAttributeValue(null, "requiredMinimal")
        var uniqueId = parser.getAttributeValue(null, "uniqueid")
        var displayName = parser.getAttributeValue(null, "name")
        var buildProfileId = parser.getAttributeValue(null, "buildProfileID")

        var major = -1
        var minor = -1

        // defaults to 0 since old app builds don't have requiredMinimal defined in Profile
        var minimal = 0

        if (sMajor != null) {
            major = parseInt(sMajor)
        }
        if (sMinor != null) {
            minor = parseInt(sMinor)
        }
        if (sMinimal != null) {
            minimal = parseInt(sMinimal)
        }

        //If version information is available, check valid versions
        if ((!forceVersion && this.instance != null) && (major != -1) && (minor != -1)) {

            if (this.instance.majorVersion != -1
                && this.instance.majorVersion != major
            ) {
                throw UnfullfilledRequirementsException(
                    "Major Version Mismatch (Required: $major | Available: ${this.instance.majorVersion})",
                    major,
                    minor,
                    minimal,
                    this.instance.majorVersion,
                    this.instance.minorVersion,
                    this.instance.minimalVersion,
                    UnfullfilledRequirementsException.RequirementType.MAJOR_APP_VERSION
                )
            }

            if (this.instance.minorVersion != -1
                && this.instance.minorVersion < minor
            ) {
                throw UnfullfilledRequirementsException(
                    "Minor Version Mismatch (Required: $minor | Available: ${this.instance.minorVersion})",
                    major,
                    minor,
                    minimal,
                    this.instance.majorVersion,
                    this.instance.minorVersion,
                    this.instance.minimalVersion,
                    UnfullfilledRequirementsException.RequirementType.MINOR_APP_VERSION
                )
            }

            if (this.instance.minorVersion == minor && this.instance.minimalVersion < minimal) {
                throw UnfullfilledRequirementsException(
                    "Minimal Version Mismatch (Required: $minimal | Available: ${this.instance.minimalVersion})",
                    major,
                    minor,
                    minimal,
                    this.instance.majorVersion,
                    this.instance.minorVersion,
                    this.instance.minimalVersion,
                    UnfullfilledRequirementsException.RequirementType.MINOR_APP_VERSION
                )
            }
        }

        val fromOld = (uniqueId == null) || (displayName == null)
        if (uniqueId == null) {
            uniqueId = PropertyUtils.genUUID()
        }
        if (displayName == null) {
            displayName = ""
        }

        if (buildProfileId == null) {
            buildProfileId = ""
        }

        return Profile(version, authRef, uniqueId, displayName, fromOld, buildProfileId)
    }

    private fun parseProperty(profile: Profile) {
        val key = parser.getAttributeValue(null, "key")
        val value = parser.getAttributeValue(null, "value")
        val force = parser.getAttributeValue(null, "force")
        addPropertySetter(profile, key, value, force)
    }

    private fun addPropertySetter(profile: Profile, key: String, value: String, force: String?) {
        if (force != null) {
            if ("true" == force.lowercase()) {
                profile.addPropertySetter(key, value, true)
            } else {
                profile.addPropertySetter(key, value, false)
            }
        } else {
            profile.addPropertySetter(key, value)
        }
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    private fun parseLogin() {
        // Get the resource block or fail out
        getNextTagInBlock("login")
        val resource = ResourceParser(parser, maximumResourceAuthority).parse()
        table.addResource(resource, table.getInstallers().getLoginImageInstaller(), resourceId, initialResourceStatus)
    }

    @Throws(PlatformXmlParserException::class, PlatformIOException::class, InvalidStructureException::class)
    private fun parseFeatures(profile: Profile) {
        while (nextTagInBlock("features")) {
            val tag = parser.name.lowercase()
            val active = parser.getAttributeValue(null, "active")
            var isActive = false
            if (active != null && active.lowercase() == "true") {
                isActive = true
            }
            if (tag == "checkoff") {
                // no-op
            } else if (tag == "reminders") {
                if (nextTagInBlock("reminders")) {
                    checkNode("time")
                    val reminderTime = parser.nextText()
                }
            } else if (tag == "package") {
                //nothing (yet)
            } else if (tag == "users") {
                while (nextTagInBlock("users")) {
                    if (parser.name.lowercase() == "registration") {
                        profile.addPropertySetter("user_reg_namespace", parser.nextText(), true)
                    } else if (parser.name.lowercase() == "logo") {
                        val logo = parser.nextText()
                        profile.addPropertySetter("cc_login_image", logo, true)
                    } else {
                        throw InvalidStructureException(
                            "Unrecognized tag ${parser.name} inside of users feature block", parser
                        )
                    }
                }
            } else if (tag == NAME_DEPENDENCIES) {
                profile.setDependencies(parseDependencies())
            } else if (tag == "sense") {
                // no-op
            } else if (tag == NAME_CREDENTIALS) {
                profile.setCredentials(parseCredentials())
            }

            profile.setFeatureActive(tag, isActive)
        }
    }

    @Throws(InvalidStructureException::class, PlatformXmlParserException::class, PlatformIOException::class)
    private fun parseCredentials(): ArrayList<Credential> {
        val appCredentials = ArrayList<Credential>()
        while (nextTagInBlock(NAME_CREDENTIALS)) {
            val tag = parser.name.lowercase()
            if (tag == NAME_CREDENTIAL) {
                val level = parser.getAttributeValue(null, ATTR_CREDENTIAL_LEVEL)
                val type = parser.getAttributeValue(null, ATTR_CREDENTIAL_TYPE)
                if (StringUtils.isEmpty(level)) {
                    throw InvalidStructureException("No level defined for credential")
                }
                if (StringUtils.isEmpty(type)) {
                    throw InvalidStructureException("No type defined for credential")
                }
                appCredentials.add(Credential(level, type))
            }
        }
        return appCredentials
    }

    @Throws(InvalidStructureException::class, PlatformXmlParserException::class, PlatformIOException::class)
    private fun parseDependencies(): ArrayList<AndroidPackageDependency> {
        val appDependencies = ArrayList<AndroidPackageDependency>()
        while (nextTagInBlock(NAME_DEPENDENCIES)) {
            val tag = parser.name.lowercase()
            if (tag == NAME_ANDROID_PACKAGE) {
                val appId = parser.getAttributeValue(null, ATTR_ID)
                    ?: throw InvalidStructureException("No id defined for app dependency")
                appDependencies.add(AndroidPackageDependency(appId))
            }
        }
        return appDependencies
    }

    @Throws(InvalidStructureException::class, PlatformXmlParserException::class, PlatformIOException::class)
    private fun parseSuite() {
        // Get the resource block or fail out
        getNextTagInBlock("suite")
        val resource = ResourceParser(parser, maximumResourceAuthority).parse()
        table.addResource(resource, table.getInstallers().getSuiteInstaller(), resourceId, initialResourceStatus)
    }

    fun setMaximumAuthority(authority: Int) {
        maximumResourceAuthority = authority
    }
}
