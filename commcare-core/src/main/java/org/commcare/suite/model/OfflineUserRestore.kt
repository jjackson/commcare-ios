package org.commcare.suite.model

import org.commcare.core.parse.UserXmlParser
import org.commcare.data.xml.DataModelPullParser
import org.commcare.data.xml.TransactionParser
import org.commcare.data.xml.TransactionParserFactory
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.model.User
import org.javarosa.core.reference.InvalidReferenceException
import org.javarosa.core.reference.ReferenceManager
import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.xml.PlatformXmlParser
import org.javarosa.xml.PlatformXmlParserException

import org.javarosa.core.io.createByteArrayInputStream
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.core.util.externalizable.nullIfEmpty
import org.javarosa.core.util.externalizable.emptyIfNull
import org.javarosa.core.io.PlatformInputStream
import kotlin.jvm.JvmStatic

/**
 * User restore xml file sometimes present in apps.
 * Used for offline (demo user) logins.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 * @author Aliza Stone (astone@dimagi.com)
 */
class OfflineUserRestore : Persistable {
    private var recordId: Int = -1
    private var restore: String? = null
    private var reference: String? = null
    private var username: String? = null

    constructor()

    @Throws(
        UnfullfilledRequirementsException::class, PlatformIOException::class, InvalidStructureException::class,
        PlatformXmlParserException::class, InvalidReferenceException::class
    )
    constructor(reference: String?) {
        this.reference = reference
        checkThatRestoreIsValidAndSetUsername()
    }

    @Throws(PlatformIOException::class, InvalidReferenceException::class)
    fun getRestoreStream(): PlatformInputStream {
        return if (reference != null) {
            // user restore xml was installed to a file
            getStreamFromReference()
        } else {
            // user restore xml was installed in memory (CLI)
            getInMemoryStream()
        }
    }

    private fun getInMemoryStream(): PlatformInputStream {
        return createByteArrayInputStream(restore!!.toByteArray(Charsets.UTF_8))
    }

    @Throws(InvalidReferenceException::class, PlatformIOException::class)
    private fun getStreamFromReference(): PlatformInputStream {
        val local = ReferenceManager.instance().DeriveReference(reference)
        return local.getStream()
    }

    fun getReference(): String? = reference

    fun getUsername(): String? = username

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        this.recordId = SerializationHelpers.readInt(`in`)
        this.reference = nullIfEmpty(SerializationHelpers.readString(`in`))
        this.restore = nullIfEmpty(SerializationHelpers.readString(`in`))
        this.username = nullIfEmpty(SerializationHelpers.readString(`in`))
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeNumeric(out, recordId.toLong())
        SerializationHelpers.writeString(out, emptyIfNull(reference))
        SerializationHelpers.writeString(out, emptyIfNull(restore))
        SerializationHelpers.writeString(out, emptyIfNull(username))
    }

    override fun setID(ID: Int) {
        recordId = ID
    }

    override fun getID(): Int = recordId

    @Throws(
        UnfullfilledRequirementsException::class, PlatformIOException::class, InvalidStructureException::class,
        PlatformXmlParserException::class, InvalidReferenceException::class
    )
    private fun checkThatRestoreIsValidAndSetUsername() {
        val factory = TransactionParserFactory { parser ->
            val name = parser.getName()
            if ("registration" == name!!.lowercase()) {
                return@TransactionParserFactory buildUserParser(parser)
            }
            null
        }

        val parser = DataModelPullParser(getRestoreStream(), factory, true, false)
        parser.parse()
    }

    private fun buildUserParser(parser: PlatformXmlParser): TransactionParser<*> {
        return object : UserXmlParser(parser) {
            @Throws(PlatformIOException::class, InvalidStructureException::class)
            override fun commit(parsed: User) {
                if (parsed.getUserType() != User.TYPE_DEMO) {
                    throw InvalidStructureException(
                        "Demo user restore file must be for a user with user_type set to demo"
                    )
                }
                if ("" == parsed.getUsername() || parsed.getUsername() == null) {
                    throw InvalidStructureException(
                        "Demo user restore file must specify a username in the Registration block"
                    )
                } else {
                    this@OfflineUserRestore.username = parsed.getUsername()
                }
            }

            override fun retrieve(entityId: String): User? = null
        }
    }

    companion object {
        const val STORAGE_KEY = "OfflineUserRestore"
        const val DEMO_USER_PASSWORD = "demo-user-password"

        @JvmStatic
        @Throws(
            UnfullfilledRequirementsException::class, PlatformIOException::class, InvalidStructureException::class,
            PlatformXmlParserException::class, InvalidReferenceException::class
        )
        fun buildInMemoryUserRestore(restoreStream: PlatformInputStream): OfflineUserRestore {
            val offlineUserRestore = OfflineUserRestore()
            val restoreBytes = StreamsUtil.inputStreamToByteArray(restoreStream)
            offlineUserRestore.restore = String(restoreBytes)
            offlineUserRestore.checkThatRestoreIsValidAndSetUsername()
            return offlineUserRestore
        }
    }
}
