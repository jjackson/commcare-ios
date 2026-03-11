package org.commcare.cases.instance

import org.commcare.cases.model.StorageIndexedTreeElementModel
import org.javarosa.core.model.IndexedFixtureIdentifier
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.model.xform.XPathReference
import org.javarosa.xpath.expr.XPathPathExpr
import kotlin.jvm.JvmField

/**
 * The root element for the an indexed fixture data instance:
 * instance('some-indexed-fixture')/fixture-root
 * All children are nodes in a database table associated with the fixture.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
abstract class IndexedFixtureInstanceTreeElement(
    instanceRoot: AbstractTreeElement?,
    storage: IStorageUtilityIndexed<StorageIndexedTreeElementModel>,
    indexedFixtureIdentifier: IndexedFixtureIdentifier
) : StorageInstanceTreeElement<StorageIndexedTreeElementModel, IndexedFixtureChildElement>(
    instanceRoot, storage,
    indexedFixtureIdentifier.fixtureBase,
    indexedFixtureIdentifier.fixtureChild
) {

    private var storageIndexMap: HashMap<XPathPathExpr, String>? = null
    private val cacheKey: String

    @JvmField
    protected var attrHolder: ByteArray?

    init {
        attrHolder = indexedFixtureIdentifier.rootAttributes
        cacheKey = indexedFixtureIdentifier.fixtureBase + "|" + indexedFixtureIdentifier.fixtureChild
    }

    override fun buildElement(
        storageInstance: StorageInstanceTreeElement<StorageIndexedTreeElementModel, IndexedFixtureChildElement>,
        recordId: Int,
        id: String?,
        mult: Int
    ): IndexedFixtureChildElement {
        return IndexedFixtureChildElement(storageInstance, mult, recordId)
    }

    override fun getChildTemplate(): IndexedFixtureChildElement {
        return IndexedFixtureChildElement.buildFixtureChildTemplate(this)
    }

    override fun getStorageIndexMap(): HashMap<XPathPathExpr, String> {
        if (storageIndexMap == null) {
            storageIndexMap = HashMap()

            val template = getModelTemplate()
            for (fieldName in template.getMetaDataFields()) {
                val entry = StorageIndexedTreeElementModel.getElementOrAttributeFromSqlColumnName(fieldName)
                storageIndexMap!![XPathReference.getPathExpr(entry)] = fieldName
            }
        }

        return storageIndexMap!!
    }

    override fun getAttributeCount(): Int {
        return loadAttributes().getAttributeCount()
    }

    override fun getAttributeNamespace(index: Int): String? {
        return loadAttributes().getAttributeNamespace(index)
    }

    override fun getAttributeName(index: Int): String? {
        return loadAttributes().getAttributeName(index)
    }

    override fun getAttributeValue(index: Int): String? {
        return loadAttributes().getAttributeValue(index)
    }

    override fun getAttribute(namespace: String?, name: String): AbstractTreeElement? {
        val attr = loadAttributes().getAttribute(namespace, name)
        if (attr != null) {
            attr.setParent(this)
        }
        return attr
    }

    override val storageCacheName: String?
        get() = cacheKey

    protected abstract fun loadAttributes(): TreeElement
}
