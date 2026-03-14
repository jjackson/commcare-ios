package org.commcare.backend.suite.model.test

import org.commcare.resources.model.Resource
import org.commcare.resources.model.ResourceTable
import org.commcare.suite.model.AndroidPackageDependency
import org.commcare.suite.model.Credential
import org.commcare.suite.model.Profile
import org.commcare.test.utilities.PersistableSandbox
import org.commcare.util.engine.CommCareConfigEngine
import org.commcare.xml.ProfileParser
import org.javarosa.core.services.storage.util.DummyIndexedStorageUtility
import org.javarosa.core.util.ArrayUtilities
import org.javarosa.core.util.externalizable.LivePrototypeFactory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Arrays

/**
 * Regressions and unit tests for the profile model.
 *
 * Covers functioning specific to the profile itself, not
 * to setup/usage of resource tables or other models.
 *
 * @author ctsims
 */
class ProfileTests {
    private lateinit var mSandbox: PersistableSandbox
    private lateinit var mAppPlatform: CommCareConfigEngine
    private lateinit var mFreshResourceTable: ResourceTable

    companion object {
        private const val BASIC_PROFILE_PATH = "/basic_profile.ccpr"
        private const val MULT_APPS_PROFILE_PATH = "/multiple_apps_profile.ccpr"
    }

    @Before
    fun setUp() {
        mSandbox = PersistableSandbox()
        mAppPlatform = CommCareConfigEngine()
        mFreshResourceTable = ResourceTable.RetrieveTable(
            DummyIndexedStorageUtility(Resource::class.java, LivePrototypeFactory())
        )
    }

    @Test
    fun testProfileParse() {
        val p = getProfile(BASIC_PROFILE_PATH)
        assertEquals("Profile is not set to the correct version: (102)", p.getVersion(), 102)
    }

    @Test
    fun testBasicProfileSerialization() {
        val p = getProfile(BASIC_PROFILE_PATH)
        val serializedProfile = PersistableSandbox.serialize(p)

        val deserialized = mSandbox.deserialize(serializedProfile, Profile::class.java)

        // Maybe this should just be p.equals(deserialized)? Kind of hard to say with deep
        // models of this type.
        compareProfiles(p, deserialized)
    }

    @Test
    fun testMultipleAppsProfileSerialization() {
        val p = getProfile(MULT_APPS_PROFILE_PATH)
        val serializedProfile = PersistableSandbox.serialize(p)
        val deserialized = mSandbox.deserialize(serializedProfile, Profile::class.java)
        compareProfiles(p, deserialized)
    }

    // Tests that a profile.ccpr which was missing the necessary fields for multiple apps has
    // them generated correctly by the parser
    @Test
    fun testGeneratedProfileFields() {
        val p = getProfile(BASIC_PROFILE_PATH)
        assertNotNull("Profile uniqueId was null", p.getUniqueId())
        assertNotNull("Profile display name was null", p.getDisplayName())
    }

    @Test
    fun testDependenciesParse() {
        val p = getProfile(BASIC_PROFILE_PATH)
        assertTrue(p.isFeatureActive("dependencies"))
        val expectedDependencies = arrayOf(
            AndroidPackageDependency("org.commcare.reminders"),
            AndroidPackageDependency("org.commcare.test")
        )
        assertEquals(
            Arrays.toString(expectedDependencies),
            Arrays.toString(p.getDependencies().toTypedArray())
        )
    }

    @Test
    fun testCredentialsParse() {
        val p = getProfile(BASIC_PROFILE_PATH)
        assertTrue(p.isFeatureActive("credentials"))
        val expectedCredentials = arrayOf(
            Credential("3MON_ACTIVE", "APP_ACTIVITY"),
            Credential("6MON_ACTIVE", "APP_ACTIVITY")
        )
        assertEquals(
            Arrays.toString(expectedCredentials),
            Arrays.toString(p.getCredentials().toTypedArray())
        )
    }

    private fun compareProfiles(a: Profile, b: Profile) {
        @Suppress("UNCHECKED_CAST")
        if (!ArrayUtilities.arraysEqual(a.getPropertySetters() as Array<Any>, b.getPropertySetters() as Array<Any>)) {
            fail("Mismatch of property setters between profiles")
        }

        assertEquals("Mismatched auth references", a.getAuthReference(), b.getAuthReference())
        assertEquals("Mismatched profile versions", a.getVersion(), b.getVersion())
        assertEquals("Mismatched profile unique ids", a.getUniqueId(), b.getUniqueId())
        assertEquals("Mismatched profile display names", a.getDisplayName(), b.getDisplayName())
        assertEquals("Mismatched profiles on old version", a.isOldVersion(), b.isOldVersion())

        // TODO: compare root references and other mismatched fields
    }

    private fun getProfile(path: String): Profile {
        try {
            val inputStream = this.javaClass.getResourceAsStream(path)
                ?: throw RuntimeException("Test resource missing: $path")

            val parser = ProfileParser(
                inputStream, mAppPlatform.platform, mFreshResourceTable, "profile",
                Resource.RESOURCE_VERSION_UNKNOWN, false
            )

            return parser.parse()
        } catch (e: Exception) {
            e.printStackTrace()
            throw PersistableSandbox.wrap("Error during profile test setup", e)
        }
    }
}
