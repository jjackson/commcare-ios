package org.javarosa.core.model

import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.util.restorable.Restorable
import org.javarosa.core.model.util.restorable.RestoreUtils
import org.javarosa.core.services.storage.IMetaData
import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapMap
import org.javarosa.core.util.externalizable.PrototypeFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream

/**
 * Persistable object representing a CommCare mobile user.
 *
 * @author ctsims
 * @author wspride
 */
class User : Persistable, Restorable, IMetaData {

    @JvmField
    var recordId: Int = -1 // record id on device

    private var username: String? = null
    private var passwordHash: String? = null
    private var uniqueId: String? = null // globally-unique id

    private var rememberMe: Boolean = false
    private var syncToken: String? = null
    private var wrappedKey: ByteArray? = null

    @JvmField
    var properties: HashMap<String, String> = HashMap()

    // Don't ever save!
    private var cachedPwd: String? = null

    constructor() {
        setUserType(STANDARD)
    }

    constructor(name: String?, passw: String?, uniqueID: String?) : this(name, passw, uniqueID, STANDARD)

    constructor(name: String?, passw: String?, uniqueID: String?, userType: String?) {
        username = name
        passwordHash = passw
        uniqueId = uniqueID
        setUserType(userType)
        rememberMe = false
    }

    // fetch the value for the default user and password from the RMS
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        this.username = ExtUtil.readString(`in`)
        this.passwordHash = ExtUtil.readString(`in`)
        this.recordId = ExtUtil.readInt(`in`)
        this.uniqueId = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        this.rememberMe = ExtUtil.readBool(`in`)
        this.syncToken = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        @Suppress("UNCHECKED_CAST")
        this.properties = ExtUtil.read(`in`, ExtWrapMap(String::class.java, String::class.java), pf) as HashMap<String, String>
        this.wrappedKey = ExtUtil.nullIfEmpty(ExtUtil.readBytes(`in`))
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(username))
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(passwordHash))
        ExtUtil.writeNumeric(out, recordId.toLong())
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(uniqueId))
        ExtUtil.writeBool(out, rememberMe)
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(syncToken))
        ExtUtil.write(out, ExtWrapMap(properties))
        ExtUtil.writeBytes(out, ExtUtil.emptyIfNull(wrappedKey))
    }

    fun getUsername(): String? {
        return username
    }

    fun setUsername(username: String?) {
        this.username = username
    }

    fun getPasswordHash(): String? {
        return passwordHash
    }

    fun setPassword(passwordHash: String?) {
        this.passwordHash = passwordHash
    }

    override fun setID(recordId: Int) {
        this.recordId = recordId
    }

    override fun getID(): Int {
        return recordId
    }

    fun getUserType(): String? {
        return if (properties.containsKey(KEY_USER_TYPE)) {
            properties[KEY_USER_TYPE]
        } else {
            null
        }
    }

    fun setUserType(userType: String?) {
        properties[KEY_USER_TYPE] = userType ?: return
    }

    fun setRememberMe(rememberMe: Boolean) {
        this.rememberMe = rememberMe
    }

    fun setUuid(uuid: String?) {
        this.uniqueId = uuid
    }

    fun getUniqueId(): String? {
        return uniqueId
    }

    fun setProperty(key: String, `val`: String?) {
        this.properties[key] = `val` ?: return
    }

    fun getProperty(key: String): String? {
        return this.properties[key]
    }

    fun getProperties(): HashMap<String, String> {
        return this.properties
    }

    override fun templateData(dm: FormInstance, parentRef: TreeReference) {
        RestoreUtils.applyDataType(dm, "name", parentRef, String::class.java)
        RestoreUtils.applyDataType(dm, "pass", parentRef, String::class.java)
        RestoreUtils.applyDataType(dm, "type", parentRef, String::class.java)
        RestoreUtils.applyDataType(dm, "user-id", parentRef, Int::class.java)
        RestoreUtils.applyDataType(dm, "uuid", parentRef, String::class.java)
        RestoreUtils.applyDataType(dm, "remember", parentRef, Boolean::class.java)
    }

    override fun getMetaData(fieldName: String): Any {
        if (META_UID == fieldName) {
            return uniqueId as Any
        } else if (META_USERNAME == fieldName) {
            return username as Any
        } else if (META_ID == fieldName) {
            return recordId
        } else if (META_WRAPPED_KEY == fieldName) {
            return wrappedKey as Any
        } else if (META_SYNC_TOKEN == fieldName) {
            return ExtUtil.emptyIfNull(syncToken) as Any
        }
        throw IllegalArgumentException("No metadata field $fieldName for User Models")
    }

    // TODO: Add META_WRAPPED_KEY back in?
    override fun getMetaDataFields(): Array<String> {
        return arrayOf(META_UID, META_USERNAME, META_ID, META_SYNC_TOKEN)
    }

    fun setCachedPwd(password: String?) {
        this.cachedPwd = password
    }

    fun getCachedPwd(): String? {
        return this.cachedPwd
    }

    fun getLastSyncToken(): String? {
        return syncToken
    }

    fun setLastSyncToken(syncToken: String?) {
        this.syncToken = syncToken
    }

    fun setWrappedKey(key: ByteArray?) {
        this.wrappedKey = key
    }

    fun getWrappedKey(): ByteArray? {
        return wrappedKey
    }

    companion object {
        const val STORAGE_KEY: String = "USER"
        const val STANDARD: String = "standard"
        const val KEY_USER_TYPE: String = "user_type"
        const val TYPE_DEMO: String = "demo"
        const val META_UID: String = "uid"
        const val META_USERNAME: String = "username"
        const val META_ID: String = "userid"
        const val META_WRAPPED_KEY: String = "wrappedkey"
        const val META_SYNC_TOKEN: String = "synctoken"
    }
}
