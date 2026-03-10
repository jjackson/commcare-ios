package org.commcare.xml

import org.commcare.cases.instance.FixtureIndexSchema
import org.commcare.cases.model.StorageIndexedTreeElementModel
import org.commcare.core.interfaces.UserSandbox
import org.commcare.data.xml.TransactionParser
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.xml.TreeElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParserException
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Creates a table for the indexed fixture and parses each element into a
 * StorageIndexedTreeElementModel and stores that as a table row.
 *
 * Also stores base and child names associated with fixture in another database.
 * For example, if we have a fixture referenced by
 * instance('product-list')/products/product/... then we need to associate
 * ('product-list', 'products', 'product') to be able to reconstruct the
 * fixture instance
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
class IndexedFixtureXmlParser(
    parser: KXmlParser,
    private val fixtureName: String?,
    schema: FixtureIndexSchema?,
    private val sandbox: UserSandbox
) : TransactionParser<StorageIndexedTreeElementModel>(parser) {

    private val indices: Set<String>
    private val columnIndices: Set<String>
    private var indexedFixtureStorage: IStorageUtilityIndexed<StorageIndexedTreeElementModel>? = null
    private var normalFixtureStorage: IStorageUtilityIndexed<FormInstance>? = null

    init {
        if (schema == null) {
            // don't create any table indices if there was no fixture index schema
            this.indices = HashSet()
            this.columnIndices = HashSet()
        } else {
            this.indices = schema.getSingleIndices()
            this.columnIndices = schema.getColumnIndices()
        }
    }

    @Throws(
        InvalidStructureException::class, PlatformIOException::class,
        XmlPullParserException::class, UnfullfilledRequirementsException::class
    )
    override fun parse(): StorageIndexedTreeElementModel? {
        checkNode("fixture")

        if (fixtureName == null) {
            throw InvalidStructureException("fixture is lacking id attribute", parser)
        }

        if (nextTagInBlock("fixture")) {
            // only commit fixtures with bodies to storage
            val root = TreeElementParser(parser, 0, fixtureName).parse()
            processRoot(root)

            // commit whole instance to normal fixture storage to allow for
            // migrations going forward, if ever needed
            val userId = parser.getAttributeValue(null, "user_id")
            val instanceAndCommitStatus =
                FixtureXmlParser.setupInstance(
                    getNormalFixtureStorage(),
                    root, fixtureName, userId, true
                )
            commitToNormalStorage(instanceAndCommitStatus.first)
        }

        return null
    }

    @Throws(PlatformIOException::class)
    private fun processRoot(root: TreeElement) {
        if (root.hasChildren()) {
            val entryName = root.getChildAt(0)!!.getName()
            writeFixtureIndex(root, entryName!!)

            for (entry in root.getChildrenWithName(entryName)) {
                processEntry(entry as TreeElement, indices)
            }
        } else {
            val storage = sandbox.getIndexedFixtureStorage(fixtureName!!)
            storage?.removeAll()
        }
    }

    @Throws(PlatformIOException::class)
    private fun processEntry(child: TreeElement, indices: Set<String>) {
        val model = StorageIndexedTreeElementModel(indices, child)
        commit(model)
    }

    @Throws(PlatformIOException::class)
    override fun commit(parsed: StorageIndexedTreeElementModel) {
        getIndexedFixtureStorage(parsed).write(parsed)
    }

    @Throws(PlatformIOException::class)
    private fun commitToNormalStorage(instance: FormInstance) {
        getNormalFixtureStorage().write(instance)
    }

    /**
     * Get storage that stores fixture element entries as table rows
     */
    private fun getIndexedFixtureStorage(
        exampleEntry: StorageIndexedTreeElementModel
    ): IStorageUtilityIndexed<StorageIndexedTreeElementModel> {
        if (indexedFixtureStorage == null) {
            sandbox.setupIndexedFixtureStorage(fixtureName!!, exampleEntry, columnIndices)
            indexedFixtureStorage = sandbox.getIndexedFixtureStorage(fixtureName!!)
        }
        return indexedFixtureStorage!!
    }

    private fun getNormalFixtureStorage(): IStorageUtilityIndexed<FormInstance> {
        if (normalFixtureStorage == null) {
            normalFixtureStorage = sandbox.getUserFixtureStorage()
        }
        return normalFixtureStorage!!
    }

    /**
     * Store base and child node names associated with a fixture.
     * Used for reconstructing fixture instance
     */
    private fun writeFixtureIndex(root: TreeElement, childName: String) {
        val attrholder = TreeElement(root.getName())
        for (i in 0 until root.getAttributeCount()) {
            attrholder.setAttribute(
                root.getAttributeNamespace(i),
                root.getAttributeName(i)!!,
                root.getAttributeValue(i)
            )
        }

        sandbox.setIndexedFixturePathBases(fixtureName!!, root.getName()!!, childName, attrholder)
    }
}
