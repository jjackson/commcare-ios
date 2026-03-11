package org.commcare.suite.model

import org.commcare.util.CommCarePlatform
import org.javarosa.core.reference.RootTranslator
import org.javarosa.core.services.PropertyManager
import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.ExtWrapMap
import org.javarosa.core.util.externalizable.PrototypeFactory

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Profile is a model which defines the operating profile
 * of a CommCare application. An application's profile
 * defines what CommCare features should be activated,
 * certain properties which should be defined, and
 * any JavaRosa URI reference roots which should be
 * available.
 *
 * @author ctsims
 */
class Profile : Persistable {
    private var recordId: Int = -1
    private var version: Int = 0
    private var authRef: String? = null
    private var properties: ArrayList<PropertySetter> = ArrayList()
    private var roots: ArrayList<RootTranslator> = ArrayList()
    private var dependencies: ArrayList<AndroidPackageDependency> = ArrayList()
    private var featureStatus: HashMap<String, Boolean> = HashMap()

    private var uniqueId: String? = null
    private var displayName: String? = null
    private var buildProfileId: String? = null

    /**
     * Indicates if this was generated from an old version of the profile file, before fields
     * were added for multiple app seating functionality
     */
    private var fromOld: Boolean = false
    private var credentials: ArrayList<Credential> = ArrayList()

    constructor()

    /**
     * Creates an application profile with the provided
     * version and authoritative reference URI.
     *
     * @param version The version of this profile which
     *                is represented by this definition.
     * @param authRef A URI which represents the authoritative
     *                source of this profile's master definition. If the
     *                profile definition read at this URI claims a higher
     *                version number than this profile's version, this profile
     *                is obsoleted by it.
     */
    constructor(
        version: Int, authRef: String?, uniqueId: String?, displayName: String?,
        fromOld: Boolean, buildProfileId: String?
    ) {
        this.version = version
        this.authRef = authRef
        this.uniqueId = uniqueId
        this.displayName = displayName
        this.buildProfileId = buildProfileId
        this.fromOld = fromOld
        properties = ArrayList()
        roots = ArrayList()
        dependencies = ArrayList()
        credentials = ArrayList()
        featureStatus = HashMap()
        // turn on default features
        featureStatus["users"] = true
    }

    override fun getID(): Int = recordId

    override fun setID(ID: Int) {
        recordId = ID
    }

    /**
     * @return the uniqueId assigned to this app from HQ
     */
    fun getUniqueId(): String? = this.uniqueId

    /**
     * @return the displayName assigned to this app from HQ if it was assigned, or an empty string
     * (If this object was generated from an old version of the profile file, there will be no
     * displayName given and this method will return an empty string, signalling CommCareApp to
     * use the app name from Localization strings instead)
     */
    fun getDisplayName(): String? = this.displayName

    /**
     * @return the buildProfileId for this particular app profile
     */
    fun getBuildProfileId(): String? = buildProfileId

    /**
     * @return if this object was generated from an old version of the profile.ccpr file
     */
    fun isOldVersion(): Boolean = this.fromOld

    /**
     * @return The version of this profile which
     * is represented by this definition.
     */
    fun getVersion(): Int = version

    /**
     * @return A URI which represents the authoritative
     * source of this profile's master definition. If the
     * profile definition read at this URI claims a higher
     * version number than this profile's version, this profile
     * is obsoleted by it.
     */
    fun getAuthReference(): String? = authRef

    /**
     * Determines whether or not a specific CommCare feature should
     * be active in the current application.
     *
     * @param feature The key of the feature being requested.
     * @return Whether or not in the application being defined
     * by this profile the feature requested should be made available
     * to end users.
     */
    fun isFeatureActive(feature: String): Boolean {
        return featureStatus.containsKey(feature) && featureStatus[feature]!!
    }

    // The below methods should all be replaced by a model builder
    // or a change to how the profile parser works

    fun addRoot(r: RootTranslator) {
        this.roots.add(r)
    }

    fun addPropertySetter(key: String, value: String) {
        this.addPropertySetter(key, value, false)
    }

    fun addPropertySetter(key: String, value: String, force: Boolean) {
        properties.add(PropertySetter(key, value, force))
    }

    fun getPropertySetters(): Array<PropertySetter> {
        val setters = Array(properties.size) { i -> properties[i] }
        return setters
    }

    fun setFeatureActive(feature: String, active: Boolean) {
        this.featureStatus[feature] = active
    }

    fun getDependencies(): ArrayList<AndroidPackageDependency> = dependencies

    fun setDependencies(dependencies: ArrayList<AndroidPackageDependency>) {
        this.dependencies = dependencies
    }

    fun setCredentials(credentials: ArrayList<Credential>) {
        this.credentials = credentials
    }

    fun getCredentials(): ArrayList<Credential> = credentials

    /**
     * A helper method which initializes the properties specified
     * by this profile definition.
     *
     * Note: This should probably be stored elsewhere, since the operation
     * mutates the model by removing the properties afterwards. Probably
     * in the property installer?
     *
     * NOTE: Moving at earliest opportunity to j2me profile installer
     */
    fun initializeProperties(platform: CommCarePlatform, enableForce: Boolean) {
        val propertyManager = platform.getPropertyManager()!!
        for (setter in properties) {
            val property = propertyManager.getSingularProperty(setter.getKey())
            // We only want to set properties which are undefined or are forced
            if (property == null || (enableForce && setter.force)) {
                propertyManager.setProperty(setter.getKey(), setter.getValue())
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        recordId = ExtUtil.readInt(`in`)
        version = ExtUtil.readInt(`in`)
        authRef = ExtUtil.readString(`in`)
        uniqueId = ExtUtil.readString(`in`)
        displayName = ExtUtil.readString(`in`)
        fromOld = ExtUtil.readBool(`in`)

        properties = ExtUtil.read(`in`, ExtWrapList(PropertySetter::class.java), pf) as ArrayList<PropertySetter>
        roots = ExtUtil.read(`in`, ExtWrapList(RootTranslator::class.java), pf) as ArrayList<RootTranslator>
        featureStatus = ExtUtil.read(`in`, ExtWrapMap(String::class.java, Boolean::class.javaObjectType), pf) as HashMap<String, Boolean>
        buildProfileId = ExtUtil.readString(`in`)
        dependencies = ExtUtil.read(`in`, ExtWrapList(AndroidPackageDependency::class.java), pf) as ArrayList<AndroidPackageDependency>
        credentials = ExtUtil.read(`in`, ExtWrapList(Credential::class.java), pf) as ArrayList<Credential>
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeNumeric(out, recordId.toLong())
        ExtUtil.writeNumeric(out, version.toLong())
        ExtUtil.writeString(out, authRef)
        ExtUtil.writeString(out, uniqueId)
        ExtUtil.writeString(out, displayName)
        ExtUtil.writeBool(out, fromOld)

        ExtUtil.write(out, ExtWrapList(properties))
        ExtUtil.write(out, ExtWrapList(roots))
        ExtUtil.write(out, ExtWrapMap(featureStatus))
        ExtUtil.writeString(out, buildProfileId)
        ExtUtil.write(out, ExtWrapList(dependencies))
        ExtUtil.write(out, ExtWrapList(credentials))
    }

    companion object {
        const val STORAGE_KEY = "PROFILE"
        const val FEATURE_REVIEW = "checkoff"
    }
}
