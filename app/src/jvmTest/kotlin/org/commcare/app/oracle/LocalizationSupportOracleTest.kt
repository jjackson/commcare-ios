package org.commcare.app.oracle

import org.commcare.app.engine.PrintTemplateEngine
import org.commcare.app.platform.CrashReport
import org.commcare.app.platform.PlatformCrashReporter
import org.commcare.app.platform.PlatformPrinting
import org.commcare.app.ui.CalendarWidget
import org.commcare.app.viewmodel.RecoveryViewModel
import org.commcare.app.viewmodel.UnsentForm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Oracle tests for localization, printing, recovery, and crash logging.
 */
class LocalizationSupportOracleTest {

    @Test
    fun testCalendarWidgetDetection() {
        assertTrue(CalendarWidget.isAlternativeCalendar("ethiopian"))
        assertTrue(CalendarWidget.isAlternativeCalendar("nepali"))
        assertFalse(CalendarWidget.isAlternativeCalendar("gregorian"))
        assertFalse(CalendarWidget.isAlternativeCalendar(null))
    }

    @Test
    fun testCalendarWidgetMonthNames() {
        val ethMonths = CalendarWidget.getMonthNames("ethiopian")
        assertEquals(13, ethMonths.size)
        assertEquals("Meskerem", ethMonths[0])
        assertEquals("Pagume", ethMonths[12])

        val nepMonths = CalendarWidget.getMonthNames("nepali")
        assertEquals(12, nepMonths.size)
        assertEquals("Baishakh", nepMonths[0])
        assertEquals("Chaitra", nepMonths[11])
    }

    @Test
    fun testCalendarWidgetFormatting() {
        val ethiopian = CalendarWidget.formatConvertedDate(2018, 6, 15, "ethiopian")
        assertEquals("Yekatit 15, 2018", ethiopian)

        val nepali = CalendarWidget.formatConvertedDate(2082, 3, 10, "nepali")
        assertEquals("Ashadh 10, 2082", nepali)

        val gregorian = CalendarWidget.formatConvertedDate(2026, 3, 15, null)
        assertEquals("2026-03-15", gregorian)
    }

    @Test
    fun testCalendarName() {
        assertEquals("Ethiopian", CalendarWidget.getCalendarName("ethiopian"))
        assertEquals("Nepali", CalendarWidget.getCalendarName("nepali"))
        assertNull(CalendarWidget.getCalendarName(null))
        assertNull(CalendarWidget.getCalendarName("gregorian"))
    }

    @Test
    fun testPrintTemplateRendering() {
        val engine = PrintTemplateEngine()
        val template = "<h1>{{title}}</h1><p>Patient: {{name}}</p>"
        val data = mapOf("title" to "Case Report", "name" to "John Doe")
        val html = engine.render(template, data)
        assertTrue(html.contains("Case Report"))
        assertTrue(html.contains("John Doe"))
        assertFalse(html.contains("{{"))
    }

    @Test
    fun testPrintTemplateHtmlEscaping() {
        val engine = PrintTemplateEngine()
        val template = "<p>{{content}}</p>"
        val data = mapOf("content" to "<script>alert('xss')</script>")
        val html = engine.render(template, data)
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test
    fun testPrintTemplateDefaultLayout() {
        val engine = PrintTemplateEngine()
        val html = engine.generateDefaultHtml(
            "Patient Report",
            listOf("Name" to "Jane", "Age" to "30", "Village" to "Nairobi")
        )
        assertTrue(html.contains("Patient Report"))
        assertTrue(html.contains("Jane"))
        assertTrue(html.contains("<table>"))
    }

    @Test
    fun testPrintingJvmUnavailable() {
        val printing = PlatformPrinting()
        assertFalse(printing.canPrint())
    }

    @Test
    fun testRecoveryViewModelFormManagement() {
        val vm = RecoveryViewModel()
        vm.loadUnsentForms(listOf(
            "Registration Form" to "2026-03-15 10:00",
            "Follow-up Form" to "2026-03-15 11:00"
        ))
        assertEquals(2, vm.unsentForms.size)

        vm.deleteForm("0")
        assertEquals(1, vm.unsentForms.size)
        assertEquals("Follow-up Form", vm.unsentForms[0].title)
    }

    @Test
    fun testRecoveryViewModelExport() {
        val vm = RecoveryViewModel()
        vm.loadUnsentForms(listOf("Test Form" to "2026-03-15"))
        val xml = vm.exportFormXml("0")
        assertNotNull(xml)
        assertTrue(xml.contains("<form"))
        assertTrue(xml.contains("Test Form"))
    }

    @Test
    fun testCrashReporterJvm() {
        val reporter = PlatformCrashReporter()
        assertEquals(0, reporter.getPendingReports().size)
        reporter.clearReports()
        assertEquals(0, reporter.getPendingReports().size)
    }

    @Test
    fun testCrashReportModel() {
        val report = CrashReport(
            timestamp = "1710500000000",
            message = "NullPointerException",
            stackTrace = "at org.test.foo()",
            deviceInfo = mapOf("platform" to "JVM", "os" to "Linux")
        )
        assertEquals("NullPointerException", report.message)
        assertEquals("JVM", report.deviceInfo["platform"])
    }
}
