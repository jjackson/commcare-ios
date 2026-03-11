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
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.xml.PlatformXmlParser
import org.javarosa.xml.PlatformXmlParserException

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import java.io.InputStream
import java.io.UnsupportedEncodingException

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
    fun getRestoreStream(): InputStream {
        return if (reference != null) {
            // user restore xml was installed to a file
            getStreamFromReference()
        } else {
            // user restore xml was installed in memory (CLI)
            getInMemoryStream()
        }
    }

    private fun getInMemoryStream(): InputStream {
        try {
            return ByteArrayInputStream(restore!!.toByteArray(charset("UTF-8")))
        } catch (e: UnsupportedEncodingException) {
            throw RuntimeException(e)
        }
    }

    @Throws(InvalidReferenceException::class, PlatformIOException::class)
    private fun getStreamFromReference(): InputStream {
        val local = ReferenceManager.instance().DeriveReference(reference)
        return local.getStream()
    }

    fun getReference(): String? = reference

    fun getUsername(): String? = username

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        this.recordId = ExtUtil.readInt(`in`)
        this.reference = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        this.restore = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        this.username = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeNumeric(out, recordId.toLong())
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(reference))
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(restore))
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(username))
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
        fun buildInMemoryUserRestore(restoreStream: InputStream): OfflineUserRestore {
            val offlineUserRestore = OfflineUserRestore()
            val restoreBytes = StreamsUtil.inputStreamToByteArray(restoreStream)
            offlineUserRestore.restore = String(restoreBytes)
            offlineUserRestore.checkThatRestoreIsValidAndSetUsername()
            return offlineUserRestore
        }
    }
}
