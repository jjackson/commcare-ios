package org.commcare.cases.util.test

import org.commcare.cases.util.CasePurgeFilter
import org.commcare.cases.util.InvalidCaseGraphException
import org.commcare.core.parse.ParseUtils
import org.commcare.core.sandbox.SandboxUtils
import org.commcare.util.mocks.MockDataUtils
import org.commcare.util.mocks.MockUserDataSandbox
import org.junit.Before
import org.junit.Test

/**
 * Quick test to be able to restore a set of user data
 * and ensure users and groups are properly being included
 * in case purges.
 *
 * @author ctsims
 */
class CasePurgeIntegrationTest {

    private lateinit var sandbox: MockUserDataSandbox
    private lateinit var owners: ArrayList<String>

    @Before
    @Throws(Exception::class)
    fun setUp() {
        sandbox = MockDataUtils.getStaticStorage()
        ParseUtils.parseIntoSandbox(javaClass.classLoader.getResourceAsStream("case_create_purge.xml"), sandbox)
        owners = SandboxUtils.extractEntityOwners(sandbox)
    }

    @Test
    @Throws(InvalidCaseGraphException::class)
    fun test() {
        val purger = CasePurgeFilter(sandbox.getCaseStorage(), owners)
        val removedCases = sandbox.getCaseStorage().removeAll(purger).size

        if (removedCases == 0) {
            throw RuntimeException("Failed to remove purged case")
        }

        if (sandbox.getCaseStorage().getNumRecords() < 1) {
            throw RuntimeException("Incorrectly purged case")
        }

        if (sandbox.getCaseStorage().getNumRecords() > 1) {
            throw RuntimeException("Incorrectly retained case")
        }

        if ("sync_token_a" != sandbox.syncToken) {
            throw RuntimeException("Invalid Sync Token: " + sandbox.syncToken)
        }
    }
}
