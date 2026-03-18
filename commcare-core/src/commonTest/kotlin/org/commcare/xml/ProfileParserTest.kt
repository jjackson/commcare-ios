package org.commcare.xml

import org.commcare.resources.model.InstallerFactory
import org.commcare.resources.model.Resource
import org.commcare.resources.model.ResourceTable
import org.commcare.test.TestInMemoryStorage
import org.commcare.test.TestStorageFactory
import org.commcare.util.CommCarePlatform
import org.javarosa.core.io.createByteArrayInputStream
import org.javarosa.core.services.storage.StorageManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cross-platform tests for ProfileParser.
 * Verifies that profile XML can be parsed into a Profile object
 * and that child resources (suites) are registered in the ResourceTable.
 * Runs on both JVM and iOS.
 */
class ProfileParserTest {

    companion object {
        /** Minimal profile XML matching real HQ output structure */
        val MINIMAL_PROFILE_XML = """
            <?xml version='1.0' encoding='UTF-8'?>
            <profile version="9"
                     update="https://example.com/profile.ccpr?latest=true"
                     requiredMajor="2"
                     requiredMinor="23"
                     requiredMinimal="0"
                     uniqueid="test-app-id-123"
                     name="Test Application">
                <property key="cur_locale" value="en" force="false"/>
                <property key="PostURL" value="https://example.com/receiver/" force="true"/>
                <suite>
                    <resource id="suite" version="9">
                        <location authority="local">./suite.xml</location>
                        <location authority="remote">https://example.com/suite.xml</location>
                    </resource>
                </suite>
            </profile>
        """.trimIndent()

        /** Profile XML with no suite — should still parse */
        val PROFILE_NO_SUITE_XML = """
            <?xml version='1.0' encoding='UTF-8'?>
            <profile version="1"
                     uniqueid="bare-profile"
                     name="Bare Profile">
                <property key="cur_locale" value="en" force="false"/>
            </profile>
        """.trimIndent()
    }

    private fun createResourceTable(): ResourceTable {
        val storage = TestInMemoryStorage<Resource>(Resource::class)
        return ResourceTable.RetrieveTable(storage, InstallerFactory())
    }

    private fun createPlatform(): CommCarePlatform {
        val storageManager = StorageManager(TestStorageFactory())
        return CommCarePlatform(2, 53, 0, storageManager)
    }

    @Test
    fun testParseMinimalProfile() {
        val table = createResourceTable()
        val platform = createPlatform()

        val stream = createByteArrayInputStream(MINIMAL_PROFILE_XML.encodeToByteArray())
        val parser = ProfileParser(
            stream, platform, table, "test-guid",
            Resource.RESOURCE_STATUS_UNINITIALIZED, false
        )
        parser.setMaximumAuthority(Resource.RESOURCE_AUTHORITY_REMOTE)

        val profile = parser.parse()

        assertNotNull(profile, "Profile should be parsed")
        assertEquals("Test Application", profile.getDisplayName())
        assertEquals("test-app-id-123", profile.getUniqueId())
        assertEquals(9, profile.getVersion())
    }

    @Test
    fun testProfileParserCreatesSuiteResource() {
        val table = createResourceTable()
        val platform = createPlatform()

        val stream = createByteArrayInputStream(MINIMAL_PROFILE_XML.encodeToByteArray())
        val parser = ProfileParser(
            stream, platform, table, "test-guid",
            Resource.RESOURCE_STATUS_UNINITIALIZED, false
        )
        parser.setMaximumAuthority(Resource.RESOURCE_AUTHORITY_REMOTE)
        parser.parse()

        // The parser should have added a suite resource to the table
        val suiteResource = table.getResourceWithId("suite")
        assertNotNull(suiteResource, "Suite resource should be registered in table")
        assertEquals("suite", suiteResource.getResourceId())
    }

    @Test
    fun testParseProfileWithNoSuite() {
        val table = createResourceTable()
        val platform = createPlatform()

        val stream = createByteArrayInputStream(PROFILE_NO_SUITE_XML.encodeToByteArray())
        val parser = ProfileParser(
            stream, platform, table, "test-guid",
            Resource.RESOURCE_STATUS_UNINITIALIZED, false
        )

        val profile = parser.parse()

        assertNotNull(profile, "Profile without suite should still parse")
        assertEquals("Bare Profile", profile.getDisplayName())
    }

    @Test
    fun testProfileVersionCheckRejectsIncompatibleMajor() {
        val table = createResourceTable()
        // Platform with major version 1, but profile requires major 2
        val storageManager = StorageManager(TestStorageFactory())
        val platform = CommCarePlatform(1, 53, 0, storageManager)

        val stream = createByteArrayInputStream(MINIMAL_PROFILE_XML.encodeToByteArray())
        val parser = ProfileParser(
            stream, platform, table, "test-guid",
            Resource.RESOURCE_STATUS_UNINITIALIZED, false
        )

        try {
            parser.parse()
            assertTrue(false, "Should have thrown for version mismatch")
        } catch (e: Exception) {
            assertTrue(
                e.message?.contains("Major Version Mismatch") == true,
                "Expected major version mismatch, got: ${e.message}"
            )
        }
    }
}
