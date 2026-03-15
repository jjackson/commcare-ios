package org.commcare.app.oracle

import org.commcare.app.viewmodel.DemoState
import org.commcare.app.viewmodel.SettingsViewModel
import org.commcare.app.viewmodel.UpdateState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Oracle tests for app lifecycle management: settings, updates, demo mode.
 */
class AppLifecycleOracleTest {

    @Test
    fun testSettingsDefaults() {
        val vm = SettingsViewModel()
        assertEquals("https://www.commcarehq.org", vm.serverUrl)
        assertEquals(15, vm.syncFrequencyMinutes)
        assertTrue(vm.autoSync)
        assertTrue(vm.fuzzySearchEnabled)
        assertEquals(0.8f, vm.fuzzySearchThreshold)
        assertNull(vm.localeOverride)
        assertTrue(vm.autoUpdateEnabled)
        assertEquals(24, vm.autoUpdateFrequencyHours)
        assertEquals("warn", vm.logLevel)
        assertFalse(vm.developerMode)
        assertFalse(vm.showDebugInfo)
        assertFalse(vm.showFormHierarchy)
        assertFalse(vm.enableXPathTester)
    }

    @Test
    fun testSettingsResetToDefaults() {
        val vm = SettingsViewModel()
        vm.serverUrl = "https://custom.example.com"
        vm.syncFrequencyMinutes = 60
        vm.developerMode = true
        vm.logLevel = "debug"
        vm.localeOverride = "fra"

        vm.resetToDefaults()

        assertEquals("https://www.commcarehq.org", vm.serverUrl)
        assertEquals(15, vm.syncFrequencyMinutes)
        assertFalse(vm.developerMode)
        assertEquals("warn", vm.logLevel)
        assertNull(vm.localeOverride)
        assertEquals("Settings reset to defaults", vm.savedMessage)
    }

    @Test
    fun testSettingsSaveMessage() {
        val vm = SettingsViewModel()
        assertNull(vm.savedMessage)
        vm.save()
        assertEquals("Settings saved", vm.savedMessage)
        vm.clearSavedMessage()
        assertNull(vm.savedMessage)
    }

    @Test
    fun testUpdateStatesAreDistinct() {
        // Verify all update states can be instantiated and are distinct types
        val states = listOf(
            UpdateState.Idle,
            UpdateState.Checking,
            UpdateState.Available,
            UpdateState.UpToDate,
            UpdateState.Installing,
            UpdateState.Complete,
            UpdateState.Error("test error")
        )
        assertEquals(7, states.size)
        assertEquals("test error", (states[6] as UpdateState.Error).message)
    }

    @Test
    fun testDemoStatesAreDistinct() {
        val states = listOf(
            DemoState.Idle,
            DemoState.Loading,
            DemoState.Active,
            DemoState.Error("demo error")
        )
        assertEquals(4, states.size)
        assertEquals("demo error", (states[3] as DemoState.Error).message)
    }

    @Test
    fun testSettingsDeveloperModeToggle() {
        val vm = SettingsViewModel()
        assertFalse(vm.developerMode)
        assertFalse(vm.showFormHierarchy)
        assertFalse(vm.enableXPathTester)

        vm.developerMode = true
        vm.showFormHierarchy = true
        vm.enableXPathTester = true

        assertTrue(vm.developerMode)
        assertTrue(vm.showFormHierarchy)
        assertTrue(vm.enableXPathTester)
    }

    @Test
    fun testLocaleOverride() {
        val vm = SettingsViewModel()
        assertNull(vm.localeOverride)

        vm.localeOverride = "hin"
        assertEquals("hin", vm.localeOverride)

        vm.localeOverride = null
        assertNull(vm.localeOverride)
    }
}
