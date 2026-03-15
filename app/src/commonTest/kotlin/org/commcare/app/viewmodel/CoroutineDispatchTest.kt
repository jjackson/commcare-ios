package org.commcare.app.viewmodel

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cross-platform test verifying that coroutines on Dispatchers.Default can write
 * to Compose mutableStateOf without crashing. This runs on both JVM and iOS simulator
 * via CI, validating the async ViewModel pattern won't deadlock on iOS.
 */
class CoroutineDispatchTest {

    @Test
    fun testDispatchersDefaultIsAvailable() = runBlocking {
        // Verify Dispatchers.Default doesn't throw on this platform
        var launched = false
        val job = CoroutineScope(Dispatchers.Default).launch {
            launched = true
        }
        job.join()
        assertEquals(true, launched, "Coroutine on Dispatchers.Default should execute")
    }

    @Test
    fun testMutableStateOfFromBackgroundCoroutine() = runBlocking {
        // This is the exact pattern used in LoginViewModel, SyncViewModel, etc.
        // If this crashes on iOS, our async ViewModels will crash too.
        val state = mutableStateOf("initial")

        val job = CoroutineScope(Dispatchers.Default).launch {
            state.value = "updated"
        }
        job.join()

        assertEquals("updated", state.value,
            "mutableStateOf should be writable from Dispatchers.Default coroutine")
    }

    @Test
    fun testMultipleStateUpdatesFromCoroutine() = runBlocking {
        // Simulates the login flow: multiple state transitions from background
        val state = mutableStateOf(0)

        val job = CoroutineScope(Dispatchers.Default).launch {
            state.value = 1
            state.value = 2
            state.value = 3
        }
        job.join()

        assertEquals(3, state.value,
            "Multiple mutableStateOf updates from coroutine should work")
    }

    @Test
    fun testCoroutineDoesNotBlockCaller() = runBlocking {
        // Verify launch returns immediately (doesn't block)
        val state = mutableStateOf("before")

        // Launch a slow coroutine
        CoroutineScope(Dispatchers.Default).launch {
            delay(200)
            state.value = "after"
        }

        // Immediately after launch, state should still be "before"
        assertEquals("before", state.value,
            "launch should not block — state should still be 'before' immediately after")
    }
}
