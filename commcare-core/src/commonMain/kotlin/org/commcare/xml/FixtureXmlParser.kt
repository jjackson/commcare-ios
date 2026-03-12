package org.commcare.xml

import org.commcare.data.xml.TransactionParser
import org.commcare.modern.util.Pair
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.xml.TreeElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.xml.PlatformXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.emptyIfNull
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * The Fixture XML Parser is responsible for parsing incoming fixture data and
 * storing it as a file with a pointer in a db.
 *
 * @author ctsims
 */
open class FixtureXmlParser(
    parser: PlatformXmlParser,
    private val overwrite: Boolean,
    @JvmField var storage: IStorageUtilityIndexed<FormInstance>
) : TransactionParser<FormInstance>(parser) {

    companion object {
        @JvmStatic
        fun setupInstance(
            storage: IStorageUtilityIndexed<FormInstance>?,
            root: TreeElement, fixtureId: String,
            userId: String?, overwrite: Boolean
        ): Pair<FormInstance, Boolean> {
            val instance = FormInstance(root, fixtureId)

            //This is a terrible hack and clayton should feeel terrible about it
            if (userId != null) {
                instance.schema = userId
            }

            //If we're using storage, deal properly
            if (storage != null) {
                var recordId = -1
                val matchingFixtures = storage.getIDsForValue(FormInstance.META_ID, fixtureId)
                if (matchingFixtures.size > 0) {
                    //find all fixtures with the same user
                    val matchingUsers = storage.getIDsForValue(
                        FormInstance.META_XMLNS, emptyIfNull(userId)
                    )
                    for (i in matchingFixtures) {
                        if (matchingUsers.indexOf(i) != -1) {
                            recordId = i
                        }
                    }
                }

                if (recordId != -1) {
                    if (!overwrite) {
                        //parse it out, but don't write anything to memory if one already exists
                        return Pair.create(instance, false)
                    }
                    instance.setID(recordId)
                }
            }
            return Pair.create(instance, true)
        }
    }

    @Throws(
        InvalidStructureException::class, PlatformIOException::class,
        PlatformXmlParserException::class, UnfullfilledRequirementsException::class
    )
    override fun parse(): FormInstance? {
        this.checkNode("fixture")

        val fixtureId = parser.getAttributeValue(null, "id")
            ?: throw InvalidStructureException("fixture is lacking id attribute", parser)

        val userId = parser.getAttributeValue(null, "user_id")

        if (!nextTagInBlock("fixture")) {
            // fixture with no body; don't commit to storage
            return null
        }
        //TODO: We need to overwrite any matching records here.
        val root = TreeElementParser(parser, 0, fixtureId).parse()

        val instanceAndCommitStatus = setupInstance(storage(), root, fixtureId, userId, overwrite)

        if (instanceAndCommitStatus.second) {
            commit(instanceAndCommitStatus.first)
        }

        return instanceAndCommitStatus.first
    }

    @Throws(PlatformIOException::class, InvalidStructureException::class)
    override fun commit(parsed: FormInstance) {
        storage().write(parsed)
    }

    open fun storage(): IStorageUtilityIndexed<FormInstance> {
        return storage
    }
}
