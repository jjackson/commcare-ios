package org.commcare.core.ios.storage

import org.commcare.cases.ledger.Ledger
import org.commcare.cases.model.Case
import org.commcare.cases.model.StorageIndexedTreeElementModel
import org.commcare.core.interfaces.UserSandbox
import org.javarosa.core.model.IndexedFixtureIdentifier
import org.javarosa.core.model.User
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.util.externalizable.PrototypeFactory

/**
 * iOS UserSandbox implementation using in-memory storage.
 * Stores cases, ledgers, users, and fixtures for a single user session.
 */
class IosUserSandbox(private val factory: PrototypeFactory) : UserSandbox() {

    private val caseStorage: IStorageUtilityIndexed<Case> =
        IosInMemoryStorage(Case::class, { Case() }, factory)

    private val ledgerStorage: IStorageUtilityIndexed<Ledger> =
        IosInMemoryStorage(Ledger::class, { Ledger() }, factory)

    private val userStorage: IStorageUtilityIndexed<User> =
        IosInMemoryStorage(User::class, { User() }, factory)

    private val userFixtureStorage: IStorageUtilityIndexed<FormInstance> =
        IosInMemoryStorage(FormInstance::class, { FormInstance() }, factory)

    private val appFixtureStorage: IStorageUtilityIndexed<FormInstance> =
        IosInMemoryStorage(FormInstance::class, { FormInstance() }, factory)

    private val indexedFixtureStorages = HashMap<String, IStorageUtilityIndexed<StorageIndexedTreeElementModel>>()
    private val indexedFixtureIdentifiers = HashMap<String, IndexedFixtureIdentifier>()

    private var loggedInUser: User? = null

    override fun getCaseStorage(): IStorageUtilityIndexed<Case> = caseStorage

    override fun getLedgerStorage(): IStorageUtilityIndexed<Ledger> = ledgerStorage

    override fun getUserStorage(): IStorageUtilityIndexed<User> = userStorage

    override fun getIndexedFixtureStorage(fixtureName: String): IStorageUtilityIndexed<StorageIndexedTreeElementModel> {
        return indexedFixtureStorages[fixtureName]
            ?: throw RuntimeException("No indexed fixture storage for: $fixtureName")
    }

    override fun setupIndexedFixtureStorage(
        fixtureName: String,
        exampleEntry: StorageIndexedTreeElementModel,
        indices: Set<String>
    ) {
        indexedFixtureStorages[fixtureName] = IosInMemoryStorage(exampleEntry, factory)
    }

    override fun getIndexedFixtureIdentifier(fixtureName: String): IndexedFixtureIdentifier? {
        return indexedFixtureIdentifiers[fixtureName]
    }

    override fun setIndexedFixturePathBases(fixtureName: String, baseName: String, childName: String, attrs: TreeElement) {
        indexedFixtureIdentifiers[fixtureName] = IndexedFixtureIdentifier(baseName, childName, null)
    }

    override fun getUserFixtureStorage(): IStorageUtilityIndexed<FormInstance> = userFixtureStorage

    override fun getAppFixtureStorage(): IStorageUtilityIndexed<FormInstance> = appFixtureStorage

    override fun getLoggedInUser(): User {
        return loggedInUser ?: throw RuntimeException("No user logged in")
    }

    override fun getLoggedInUserUnsafe(): User {
        return loggedInUser ?: throw RuntimeException("No user logged in")
    }

    override fun setLoggedInUser(user: User) {
        loggedInUser = user
    }
}
