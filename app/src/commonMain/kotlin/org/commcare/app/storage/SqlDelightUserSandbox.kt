package org.commcare.app.storage

import org.commcare.cases.ledger.Ledger
import org.commcare.cases.model.Case
import org.commcare.cases.model.StorageIndexedTreeElementModel
import org.commcare.core.interfaces.UserSandbox
import org.javarosa.core.model.IndexedFixtureIdentifier
import org.javarosa.core.model.User
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.services.storage.IStorageUtilityIndexed

/**
 * Cross-platform UserSandbox backed by in-memory storage (with SQLDelight for persistence).
 * The engine's storage APIs use IStorageUtilityIndexed which operates on in-memory objects.
 * SQLDelight handles durability of form queue, user credentials, and sync tokens.
 */
class SqlDelightUserSandbox(
    val db: CommCareDatabase
) : UserSandbox() {

    private val caseStorage: IStorageUtilityIndexed<Case> =
        InMemoryStorage(Case::class, { Case() })

    private val ledgerStorage: IStorageUtilityIndexed<Ledger> =
        InMemoryStorage(Ledger::class, { Ledger() })

    private val userStorage: IStorageUtilityIndexed<User> =
        InMemoryStorage(User::class, { User() })

    private val userFixtureStorage: IStorageUtilityIndexed<FormInstance> =
        InMemoryStorage(FormInstance::class, { FormInstance() })

    private val appFixtureStorage: IStorageUtilityIndexed<FormInstance> =
        InMemoryStorage(FormInstance::class, { FormInstance() })

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
        indexedFixtureStorages[fixtureName] = InMemoryStorage(
            StorageIndexedTreeElementModel::class,
            { StorageIndexedTreeElementModel() }
        )
    }

    override fun getIndexedFixtureIdentifier(fixtureName: String): IndexedFixtureIdentifier? {
        return indexedFixtureIdentifiers[fixtureName]
    }

    override fun setIndexedFixturePathBases(
        fixtureName: String,
        baseName: String,
        childName: String,
        attrs: TreeElement
    ) {
        indexedFixtureIdentifiers[fixtureName] = IndexedFixtureIdentifier(baseName, childName, null)
    }

    override fun getUserFixtureStorage(): IStorageUtilityIndexed<FormInstance> = userFixtureStorage

    override fun getAppFixtureStorage(): IStorageUtilityIndexed<FormInstance> = appFixtureStorage

    override fun getLoggedInUser(): User {
        return loggedInUser ?: throw RuntimeException("No user logged in")
    }

    @Throws(RuntimeException::class)
    override fun getLoggedInUserUnsafe(): User {
        return loggedInUser ?: throw RuntimeException("No user logged in")
    }

    override fun setLoggedInUser(user: User) {
        loggedInUser = user
        // Also persist to SQLDelight for offline access
        db.commCareQueries.insertUser(
            user_id = user.getUniqueId() ?: "",
            username = user.getUsername() ?: "",
            domain = "",
            password_hash = null,
            sync_token = syncToken
        )
    }

    /**
     * Persist the current sync token to the database.
     */
    fun persistSyncToken(userId: String) {
        db.commCareQueries.updateSyncToken(
            sync_token = syncToken,
            user_id = userId
        )
    }

    /**
     * Load sync token from the database for a user.
     */
    fun loadSyncToken(userId: String): String? {
        val user = db.commCareQueries.selectUserById(userId).executeAsOneOrNull()
        return user?.sync_token
    }
}
