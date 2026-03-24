package org.commcare.app.perf

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.storage.SqlDelightUserSandbox
import org.commcare.cases.model.Case
import org.commcare.core.parse.ParseUtils
import org.javarosa.core.io.createByteArrayInputStream
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Performance benchmarks for case list loading from storage.
 *
 * Populates a sandbox with cases via restore XML, then measures the time to
 * iterate all cases, read individual records, and query by meta-data index.
 * This reflects the critical path when a user opens a case list screen.
 *
 * Each test prints a BENCHMARK line for CI log parsing and asserts a generous
 * upper bound so the test only fails on severe regressions.
 */
class CaseListBenchmark {

    private fun createSandbox(): SqlDelightUserSandbox {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CommCareDatabase.Schema.create(driver)
        val db = CommCareDatabase(driver)
        return SqlDelightUserSandbox(db)
    }

    /**
     * Generate restore XML with [caseCount] cases of the given [caseType].
     */
    private fun generateRestoreXml(caseCount: Int, caseType: String = "patient"): String {
        val sb = StringBuilder(caseCount * 400)
        sb.append("""<?xml version="1.0"?>""")
        sb.append("""<OpenRosaResponse xmlns="http://openrosa.org/http/response">""")
        sb.append("""<message nature="ota_restore_success">Success</message>""")
        sb.append("""<Sync xmlns="http://commcarehq.org/sync">""")
        sb.append("""<restore_id>bench-sync-token</restore_id>""")
        sb.append("""</Sync>""")

        for (i in 1..caseCount) {
            sb.append("""<case case_id="case-$i" date_modified="2026-03-24T12:00:00.000000Z" """)
            sb.append("""xmlns="http://commcarehq.org/case/transaction/v2">""")
            sb.append("""<create>""")
            sb.append("""<case_type>$caseType</case_type>""")
            sb.append("""<case_name>Record $i</case_name>""")
            sb.append("""<owner_id>user-bench</owner_id>""")
            sb.append("""</create>""")
            sb.append("""<update>""")
            sb.append("""<first_name>First $i</first_name>""")
            sb.append("""<last_name>Last $i</last_name>""")
            sb.append("""<age>${20 + (i % 60)}</age>""")
            sb.append("""</update>""")
            sb.append("""</case>""")
        }

        sb.append("""</OpenRosaResponse>""")
        return sb.toString()
    }

    /**
     * Populate a sandbox with [count] cases via restore parsing.
     */
    private fun populateSandbox(sandbox: SqlDelightUserSandbox, count: Int, caseType: String = "patient") {
        val xml = generateRestoreXml(count, caseType)
        ParseUtils.parseIntoSandbox(
            createByteArrayInputStream(xml.encodeToByteArray()),
            sandbox,
            false
        )
    }

    @Test
    fun benchmarkCaseListLoad100() {
        val sandbox = createSandbox()
        populateSandbox(sandbox, 100)
        val storage = sandbox.getCaseStorage()
        assertEquals(100, storage.getNumRecords())

        // Measure full iteration (simulates case list screen load)
        val ms = measureTimeMillis {
            repeat(10) {
                val iter = storage.iterate()
                var count = 0
                while (iter.hasMore()) {
                    val c = iter.nextRecord()
                    // Access properties like a case list would
                    c.getName()
                    c.getTypeId()
                    count++
                }
                assertEquals(100, count)
            }
        }
        println("BENCHMARK: 10x iterate 100 cases in ${ms}ms (${ms / 10.0}ms/iteration)")
        assertTrue(ms < 5000, "10x iterating 100 cases should complete within 5s, took ${ms}ms")
    }

    @Test
    fun benchmarkCaseListLoad500() {
        val sandbox = createSandbox()
        populateSandbox(sandbox, 500)
        val storage = sandbox.getCaseStorage()
        assertEquals(500, storage.getNumRecords())

        val ms = measureTimeMillis {
            repeat(10) {
                val iter = storage.iterate()
                var count = 0
                while (iter.hasMore()) {
                    val c = iter.nextRecord()
                    c.getName()
                    c.getTypeId()
                    count++
                }
                assertEquals(500, count)
            }
        }
        println("BENCHMARK: 10x iterate 500 cases in ${ms}ms (${ms / 10.0}ms/iteration)")
        assertTrue(ms < 10000, "10x iterating 500 cases should complete within 10s, took ${ms}ms")
    }

    @Test
    fun benchmarkCaseMetaIndexLookup() {
        val sandbox = createSandbox()
        // Create mixed-type cases: 200 patient + 100 household
        populateSandbox(sandbox, 200, "patient")
        // Add household cases via direct storage since restore would overwrite sync token
        val storage = sandbox.getCaseStorage()
        for (i in 1..100) {
            val c = Case("household-$i", "household")
            c.setName("Household $i")
            c.setCaseId("hh-case-$i")
            storage.add(c)
        }
        assertEquals(300, storage.getNumRecords())

        // Measure index lookup by case-type (used for filtering case lists)
        val ms = measureTimeMillis {
            repeat(1000) {
                val patientIds = storage.getIDsForValue("case-type", "patient")
                assertEquals(200, patientIds.size)
            }
        }
        println("BENCHMARK: 1000x meta-index lookup (case-type) in ${ms}ms (${ms / 1000.0}ms/op)")
        assertTrue(ms < 5000, "1000 index lookups should complete within 5s, took ${ms}ms")
    }

    @Test
    fun benchmarkBulkRead() {
        val sandbox = createSandbox()
        populateSandbox(sandbox, 200)
        val storage = sandbox.getCaseStorage()
        assertEquals(200, storage.getNumRecords())

        // Get all IDs
        val allIds = storage.getIDsForValue("case-type", "patient")
        val idSet = LinkedHashSet(allIds)

        // Measure bulk read (used when rendering case list with all data)
        val ms = measureTimeMillis {
            repeat(50) {
                val recordMap = HashMap<Int, Case>()
                storage.bulkRead(idSet, recordMap)
                assertEquals(200, recordMap.size)
            }
        }
        println("BENCHMARK: 50x bulkRead 200 cases in ${ms}ms (${ms / 50.0}ms/op)")
        assertTrue(ms < 5000, "50x bulk reading 200 cases should complete within 5s, took ${ms}ms")
    }

    @Test
    fun benchmarkCasePropertyAccess() {
        val sandbox = createSandbox()
        populateSandbox(sandbox, 100)
        val storage = sandbox.getCaseStorage()

        // Read all cases into a list
        val cases = mutableListOf<Case>()
        val iter = storage.iterate()
        while (iter.hasMore()) {
            cases.add(iter.nextRecord())
        }
        assertEquals(100, cases.size)

        // Measure property access (simulates rendering case detail fields)
        val ms = measureTimeMillis {
            repeat(100) {
                for (c in cases) {
                    c.getName()
                    c.getTypeId()
                    c.getCaseId()
                    c.isClosed()
                    c.getProperty("first_name")
                    c.getProperty("last_name")
                    c.getProperty("age")
                }
            }
        }
        println("BENCHMARK: 100x access 7 properties on 100 cases in ${ms}ms")
        assertTrue(ms < 5000, "100x property access on 100 cases should complete within 5s, took ${ms}ms")
    }
}
