package org.commcare.core.interfaces

import org.commcare.cases.ledger.Ledger
import org.commcare.cases.model.Case
import org.commcare.cases.model.StorageIndexedTreeElementModel
import org.javarosa.core.model.IndexedFixtureIdentifier
import org.javarosa.core.model.User
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.services.storage.IStorageUtilityIndexed

/**
 * Interface to be implemented by sandboxes for a user's CommCare instance data
 *
 * @author wpride1
 */
abstract class UserSandbox {

    open var syncToken: String? = null

    abstract fun getCaseStorage(): IStorageUtilityIndexed<Case>

    abstract fun getLedgerStorage(): IStorageUtilityIndexed<Ledger>

    abstract fun getUserStorage(): IStorageUtilityIndexed<User>

    /**
     * Get user-level (encrypted) storage for a storage-indexed fixture
     */
    abstract fun getIndexedFixtureStorage(fixtureName: String): IStorageUtilityIndexed<StorageIndexedTreeElementModel>

    /**
     * Setup indexed fixture storage table and indexes over that table.
     * Must clear existing data associated with the given fixture.
     */
    abstract fun setupIndexedFixtureStorage(
        fixtureName: String,
        exampleEntry: StorageIndexedTreeElementModel,
        indices: Set<String>
    )

    /**
     * Gets the base and child name associated with a fixture id.
     *
     * For example, gets 'products' and 'products' for the data instance
     * "instance('commtrack:products')/products/product/..."
     */
    abstract fun getIndexedFixtureIdentifier(fixtureName: String): IndexedFixtureIdentifier?

    /**
     * Associates a fixture with a base name and child name.
     *
     * For example, to instantiate a data instance like "instance('commtrack:products')/products/product/..."
     * we must associate 'commtrack:products' with the 'products' base name and the 'product' child name.
     */
    abstract fun setIndexedFixturePathBases(fixtureName: String, baseName: String, childName: String, attrs: TreeElement)

    abstract fun getUserFixtureStorage(): IStorageUtilityIndexed<FormInstance>

    abstract fun getAppFixtureStorage(): IStorageUtilityIndexed<FormInstance>

    abstract fun getLoggedInUser(): User

    @Throws(RuntimeException::class)
    abstract fun getLoggedInUserUnsafe(): User

    abstract fun setLoggedInUser(user: User)
}
