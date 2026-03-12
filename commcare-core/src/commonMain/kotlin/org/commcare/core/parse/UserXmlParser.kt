package org.commcare.core.parse

import org.commcare.data.xml.TransactionParser
import org.javarosa.core.model.User
import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.xml.PlatformXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * @author ctsims
 */
open class UserXmlParser : TransactionParser<User> {

    private var storage: IStorageUtilityIndexed<User>? = null

    constructor(parser: PlatformXmlParser) : super(parser)

    constructor(parser: PlatformXmlParser, storage: IStorageUtilityIndexed<User>) : super(parser) {
        this.storage = storage
    }

    @Throws(
        InvalidStructureException::class,
        PlatformIOException::class,
        PlatformXmlParserException::class,
        UnfullfilledRequirementsException::class
    )
    override fun parse(): User {
        this.checkNode("registration")

        // parse (with verification) the next tag
        this.nextTag("username")
        val username = parser.nextText()

        this.nextTag("password")
        val passwordHash = parser.nextText()

        this.nextTag("uuid")
        val uuid = parser.nextText()

        this.nextTag("date")
        val dateModified = parser.nextText()
        DateUtils.parseDateTime(dateModified)

        var u = retrieve(uuid)

        if (u == null) {
            u = User(username, passwordHash, uuid)
        } else {
            if (passwordHash != null && passwordHash != u.getPasswordHash()) {
                u.setPassword(passwordHash)
            }
        }

        // Now look for optional components
        while (this.nextTagInBlock("registration")) {
            val tag = parser.getName()!!.lowercase()

            if (tag == "registering_phone_id") {
                parser.nextText()
            } else if (tag == "token") {
                parser.nextText()
            } else if (tag == "user_data") {
                while (this.nextTagInBlock("user_data")) {
                    this.checkNode("data")

                    val key = this.parser.getAttributeValue(null, "key")
                    val value = this.parser.nextText()

                    u.setProperty(key!!, value)
                }

                // This should be the last block in the registration stuff...
                break
            } else {
                throw InvalidStructureException(
                    "Unrecognized tag in user registraiton data: $tag", parser
                )
            }
        }

        addCustomData(u)

        commit(u)
        return u
    }

    open fun addCustomData(u: User) {
        // Don't do anything in base class
    }

    @Throws(PlatformIOException::class, InvalidStructureException::class)
    override fun commit(parsed: User) {
        storage()!!.write(parsed)
    }

    open fun retrieve(entityId: String): User? {
        val storage = storage()
        return try {
            @Suppress("UNCHECKED_CAST")
            storage!!.getRecordForValue(User.META_UID, entityId) as User
        } catch (nsee: NoSuchElementException) {
            null
        }
    }

    open fun storage(): IStorageUtilityIndexed<*>? {
        return storage
    }
}
