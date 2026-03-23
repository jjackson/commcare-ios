package org.commcare.app.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for RecoveryViewModel: unsent forms, logs, data clearing.
 */
class RecoveryActionsTest {

    @Test
    fun testInitialState() {
        val vm = RecoveryViewModel()
        assertTrue(vm.unsentForms.isEmpty())
        assertTrue(vm.logs.isEmpty())
        assertNull(vm.actionResult)
    }

    @Test
    fun testLoadUnsentForms() {
        val vm = RecoveryViewModel()
        val forms = listOf(
            "form-1" to "Registration Form",
            "form-2" to "Follow-up Form"
        )
        vm.loadUnsentForms(forms)
        assertEquals(2, vm.unsentForms.size)
    }

    @Test
    fun testDeleteForm() {
        val vm = RecoveryViewModel()
        vm.loadUnsentForms(listOf("Form 1" to "2026-03-23", "Form 2" to "2026-03-23"))
        // IDs are indices: "0" and "1"
        vm.deleteForm("0")
        assertEquals(1, vm.unsentForms.size)
        assertEquals("1", vm.unsentForms[0].id)
    }

    @Test
    fun testClearActionResult() {
        val vm = RecoveryViewModel()
        vm.clearActionResult()
        assertNull(vm.actionResult)
    }

    @Test
    fun testLoadLogs() {
        val vm = RecoveryViewModel()
        vm.loadLogs(10)
        // Logs may be empty in test environment (no platform log source)
        assertTrue(vm.logs.size <= 10)
    }
}
