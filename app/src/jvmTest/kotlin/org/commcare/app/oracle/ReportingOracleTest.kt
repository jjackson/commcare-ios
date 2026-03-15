package org.commcare.app.oracle

import org.commcare.app.ui.GraphConfig
import org.commcare.app.ui.GraphSeries
import org.commcare.app.ui.GraphType
import org.commcare.app.ui.generateGraphHtml
import org.commcare.app.viewmodel.ReportData
import org.commcare.app.viewmodel.ReportState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Oracle tests for graphing and reporting features.
 */
class ReportingOracleTest {

    @Test
    fun testGraphConfigModel() {
        val config = GraphConfig(
            title = "Cases by Month",
            type = GraphType.Bar,
            series = listOf(
                GraphSeries("Cases", listOf("Jan" to 10.0, "Feb" to 15.0, "Mar" to 8.0))
            ),
            xLabel = "Month",
            yLabel = "Count"
        )
        assertEquals("Cases by Month", config.title)
        assertEquals(GraphType.Bar, config.type)
        assertEquals(1, config.series.size)
        assertEquals(3, config.series[0].values.size)
    }

    @Test
    fun testGraphHtmlGeneration() {
        val config = GraphConfig(
            title = "Test",
            type = GraphType.Bar,
            series = listOf(
                GraphSeries("Data", listOf("A" to 5.0, "B" to 10.0))
            )
        )
        val html = generateGraphHtml(config)
        assertTrue(html.contains("<html>"))
        assertTrue(html.contains("<svg"))
        assertTrue(html.contains("Test"))
        assertTrue(html.contains("rect"))
    }

    @Test
    fun testGraphHtmlEmptyData() {
        val config = GraphConfig(
            title = "Empty",
            type = GraphType.Line,
            series = emptyList()
        )
        val html = generateGraphHtml(config)
        assertTrue(html.contains("No data"))
    }

    @Test
    fun testGraphTypes() {
        val types = GraphType.entries
        assertEquals(4, types.size)
        assertTrue(types.contains(GraphType.Bar))
        assertTrue(types.contains(GraphType.Line))
        assertTrue(types.contains(GraphType.Pie))
        assertTrue(types.contains(GraphType.Bubble))
    }

    @Test
    fun testReportDataModel() {
        val data = ReportData(
            columns = listOf("Name", "Age", "Village"),
            rows = listOf(
                listOf("John", "25", "Nairobi"),
                listOf("Jane", "30", "Mombasa")
            ),
            totalRows = 2
        )
        assertEquals(3, data.columns.size)
        assertEquals(2, data.rows.size)
        assertEquals("John", data.rows[0][0])
        assertEquals(2, data.totalRows)
    }

    @Test
    fun testReportStates() {
        val states = listOf(
            ReportState.Idle,
            ReportState.Loading,
            ReportState.Loaded,
            ReportState.Error
        )
        assertEquals(4, states.size)
    }

    @Test
    fun testGraphSeriesValues() {
        val series = GraphSeries(
            label = "Monthly Cases",
            values = listOf("Jan" to 10.0, "Feb" to 20.0, "Mar" to 15.0, "Apr" to 25.0)
        )
        assertEquals("Monthly Cases", series.label)
        assertEquals(4, series.values.size)
        assertEquals(25.0, series.values[3].second)
    }
}
