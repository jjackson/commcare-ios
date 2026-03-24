package org.commcare.app.perf

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.storage.SqlDelightUserSandbox
import org.commcare.core.parse.ParseUtils
import org.javarosa.core.io.createByteArrayInputStream
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Performance benchmarks for OTA restore XML parsing.
 *
 * Generates synthetic restore payloads with varying case counts and measures
 * end-to-end parse time through [ParseUtils.parseIntoSandbox]. This exercises
 * the full XML pull-parser -> CaseXmlParser -> InMemoryStorage pipeline, which
 * is the critical path during user login and sync.
 *
 * Each test prints a BENCHMARK line for CI log parsing and asserts a generous
 * upper bound so the test only fails on severe regressions.
 */
class RestoreBenchmark {

    private fun createSandbox(): SqlDelightUserSandbox {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CommCareDatabase.Schema.create(driver)
        val db = CommCareDatabase(driver)
        return SqlDelightUserSandbox(db)
    }

    /**
     * Generate a valid CommCare OTA restore XML payload with [caseCount] cases.
     * Each case has a create block (case_type, case_name, owner_id) and an
     * update block with 3 properties, matching the v2 case transaction format.
     */
    private fun generateRestoreXml(caseCount: Int): String {
        val sb = StringBuilder(caseCount * 400) // rough estimate per case
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
            sb.append("""<case_type>patient</case_type>""")
            sb.append("""<case_name>Patient $i</case_name>""")
            sb.append("""<owner_id>user-bench</owner_id>""")
            sb.append("""</create>""")
            sb.append("""<update>""")
            sb.append("""<first_name>Patient</first_name>""")
            sb.append("""<last_name>Number $i</last_name>""")
            sb.append("""<age>${20 + (i % 60)}</age>""")
            sb.append("""</update>""")
            sb.append("""</case>""")
        }

        sb.append("""</OpenRosaResponse>""")
        return sb.toString()
    }

    @Test
    fun benchmarkRestore100Cases() {
        val sandbox = createSandbox()
        val xml = generateRestoreXml(100)

        val ms = measureTimeMillis {
            ParseUtils.parseIntoSandbox(
                createByteArrayInputStream(xml.encodeToByteArray()),
                sandbox,
                false
            )
        }

        val caseCount = sandbox.getCaseStorage().getNumRecords()
        println("BENCHMARK: 100 cases restore parsed in ${ms}ms ($caseCount cases stored)")
        assertEquals(100, caseCount, "All 100 cases should be stored")
        assertTrue(ms < 10000, "100-case restore should parse within 10s, took ${ms}ms")
    }

    @Test
    fun benchmarkRestore500Cases() {
        val sandbox = createSandbox()
        val xml = generateRestoreXml(500)

        val ms = measureTimeMillis {
            ParseUtils.parseIntoSandbox(
                createByteArrayInputStream(xml.encodeToByteArray()),
                sandbox,
                false
            )
        }

        val caseCount = sandbox.getCaseStorage().getNumRecords()
        println("BENCHMARK: 500 cases restore parsed in ${ms}ms ($caseCount cases stored)")
        assertEquals(500, caseCount, "All 500 cases should be stored")
        assertTrue(ms < 30000, "500-case restore should parse within 30s, took ${ms}ms")
    }

    @Test
    fun benchmarkRestore500CasesBulk() {
        val sandbox = createSandbox()
        val xml = generateRestoreXml(500)

        val ms = measureTimeMillis {
            ParseUtils.parseIntoSandbox(
                createByteArrayInputStream(xml.encodeToByteArray()),
                sandbox,
                false,
                bulkProcessingEnabled = true
            )
        }

        val caseCount = sandbox.getCaseStorage().getNumRecords()
        println("BENCHMARK: 500 cases restore (bulk) parsed in ${ms}ms ($caseCount cases stored)")
        assertEquals(500, caseCount, "All 500 cases should be stored in bulk mode")
        assertTrue(ms < 30000, "500-case bulk restore should parse within 30s, took ${ms}ms")
    }

    @Test
    fun benchmarkRestoreXmlGeneration() {
        // Verify XML generation itself is fast and not a bottleneck in other benchmarks
        val ms = measureTimeMillis {
            val xml = generateRestoreXml(1000)
            assertTrue(xml.length > 100_000, "1000-case XML should be substantial")
        }
        println("BENCHMARK: 1000-case XML generation in ${ms}ms")
        assertTrue(ms < 5000, "XML generation for 1000 cases should be fast, took ${ms}ms")
    }
}
