package org.commcare.core.sandbox

import org.commcare.core.interfaces.UserSandbox
import org.commcare.core.process.CommCareInstanceInitializer
import org.javarosa.core.model.User
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.ExternalDataInstance
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.InstanceInitializationFactory
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.util.ArrayUtilities
import org.javarosa.model.xform.XPathReference
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmOverloads

/**
 * Created by wpride1 on 8/11/15.
 */
object SandboxUtils {

    /**
     * A quick way to request an evaluation context with an abstract instance available.
     *
     * Used in Touchforms
     */
    @Suppress("unused")
    @JvmStatic
    fun getInstanceContexts(sandbox: UserSandbox, instanceId: String, instanceRef: String): EvaluationContext {
        val iif: InstanceInitializationFactory = CommCareInstanceInitializer(sandbox)

        val instances = HashMap<String, DataInstance<*>>()
        val edi = ExternalDataInstance(instanceRef, instanceId)
        edi.initialize(iif, instanceId)
        instances[instanceId] = edi

        return EvaluationContext(null, instances)
    }

    /**
     * Load the referenced fixture out of storage for the provided user
     *
     * @param sandbox The current user's sandbox
     * @param refId The jr:// reference
     * @param userId The user's ID
     * @param appFixtureStorage Optionally, override the location to look for app fixtures
     * @return The form instance matching the refId in the sandbox
     */
    @JvmStatic
    @JvmOverloads
    fun loadFixture(
        sandbox: UserSandbox,
        refId: String,
        userId: String,
        appFixtureStorage: IStorageUtilityIndexed<FormInstance>? = null
    ): FormInstance? {
        val userFixtureStorage = sandbox.getUserFixtureStorage()

        val userFixtures = userFixtureStorage.getIDsForValue(FormInstance.META_ID, refId)
        if (userFixtures.size == 1) {
            return userFixtureStorage.read(userFixtures[0])
            // TODO: Userid check anyway?
        } else if (userFixtures.size > 1) {
            val result = intersectFixtureSets(userFixtureStorage, userId, userFixtures)
            if (result != null) {
                return result
            }
        }

        if (appFixtureStorage != null) {
            val result = loadAppFixture(appFixtureStorage, refId, userId)
            if (result != null) {
                return result
            }
        }
        return loadAppFixture(sandbox, refId, userId)
    }

    private fun intersectFixtureSets(
        userFixtureStorage: IStorageUtilityIndexed<FormInstance>,
        userId: String,
        userFixtures: ArrayList<Int>
    ): FormInstance? {
        val relevantUserFixtures =
            userFixtureStorage.getIDsForValue(FormInstance.META_XMLNS, userId)

        if (relevantUserFixtures.isNotEmpty()) {
            val userFixture = ArrayUtilities.intersectSingle(userFixtures, relevantUserFixtures)
            if (userFixture != null) {
                return userFixtureStorage.read(userFixture)
            }
        }
        return null
    }

    private fun loadAppFixture(sandbox: UserSandbox, refId: String, userId: String): FormInstance? {
        val appFixtureStorage = sandbox.getAppFixtureStorage()
        return loadAppFixture(appFixtureStorage, refId, userId)
    }

    private fun loadAppFixture(
        appFixtureStorage: IStorageUtilityIndexed<FormInstance>,
        refId: String,
        userId: String
    ): FormInstance? {
        val appFixtures = appFixtureStorage.getIDsForValue(FormInstance.META_ID, refId)
        val globalFixture = ArrayUtilities.intersectSingle(
            appFixtureStorage.getIDsForValue(FormInstance.META_XMLNS, ""),
            appFixtures
        )
        return if (globalFixture != null) {
            appFixtureStorage.read(globalFixture)
        } else {
            // See if we have one manually placed in the suite
            val userFixture = ArrayUtilities.intersectSingle(
                appFixtureStorage.getIDsForValue(FormInstance.META_XMLNS, userId),
                appFixtures
            )
            if (userFixture != null) {
                appFixtureStorage.read(userFixture)
            } else {
                // Otherwise, nothing
                null
            }
        }
    }

    /**
     * For the users and groups in the provided sandbox, extracts out the list
     * of valid "owners" for entities (cases, ledgers, etc) in the universe.
     */
    @JvmStatic
    fun extractEntityOwners(sandbox: UserSandbox): ArrayList<String> {
        val owners = ArrayList<String>()
        val users = ArrayList<String>()

        val userIterator = sandbox.getUserStorage().iterate()
        while (userIterator.hasMore()) {
            val id = userIterator.nextRecord().getUniqueId()!!
            owners.add(id)
            users.add(id)
        }

        // Now add all of the relevant groups
        // TODO: Wow. This is.... kind of megasketch
        for (userId in users) {
            val instance: DataInstance<*>? = loadFixture(sandbox, "user-groups", userId)
            if (instance == null) {
                continue
            }
            val ec = EvaluationContext(instance)
            val refs = ec.expandReference(XPathReference.getPathExpr("/groups/group/@id").getReference())
            if (refs != null) {
                for (ref in refs) {
                    val idElement = ec.resolveReference(ref)
                    if (idElement != null && idElement.getValue() != null) {
                        owners.add(idElement.getValue()!!.uncast().getString()!!)
                    }
                }
            }
        }

        return owners
    }
}
