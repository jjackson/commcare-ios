package org.commcare.app.viewmodel

import org.commcare.app.model.MessageThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for Connect messaging polling behavior.
 */
class MessagingPollingTest {

    @Test
    fun testMessageThreadModel() {
        val thread = MessageThread(
            id = "thread-1",
            participantName = "John Doe",
            lastMessage = "Hello!",
            lastMessageDate = "2026-03-23",
            unreadCount = 3
        )
        assertEquals("thread-1", thread.id)
        assertEquals(3, thread.unreadCount)
        assertTrue(thread.isConsented, "Default isConsented should be true")
    }

    @Test
    fun testMessageThreadWithConsentFalse() {
        val thread = MessageThread(
            id = "thread-2",
            participantName = "Jane",
            lastMessage = "",
            lastMessageDate = "",
            unreadCount = 0,
            isConsented = false
        )
        assertEquals(false, thread.isConsented)
    }

}
