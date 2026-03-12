package org.commcare.core.interfaces

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformFilesTest {

    @Test
    fun testWriteAndReadBytes() {
        val path = PlatformFiles.createTempFile("test-", ".dat")
        try {
            val data = "Hello, CommCare!".encodeToByteArray()
            PlatformFiles.writeBytes(path, data)
            val read = PlatformFiles.readBytes(path)
            assertEquals(data.toList(), read.toList())
        } finally {
            PlatformFiles.delete(path)
        }
    }

    @Test
    fun testWriteAndReadEmptyFile() {
        val path = PlatformFiles.createTempFile("test-empty-", ".dat")
        try {
            PlatformFiles.writeBytes(path, ByteArray(0))
            val read = PlatformFiles.readBytes(path)
            assertEquals(0, read.size)
        } finally {
            PlatformFiles.delete(path)
        }
    }

    @Test
    fun testWriteAndReadBinaryData() {
        val path = PlatformFiles.createTempFile("test-bin-", ".dat")
        try {
            val data = ByteArray(256) { it.toByte() }
            PlatformFiles.writeBytes(path, data)
            val read = PlatformFiles.readBytes(path)
            assertEquals(data.toList(), read.toList())
        } finally {
            PlatformFiles.delete(path)
        }
    }

    @Test
    fun testExists() {
        val path = PlatformFiles.createTempFile("test-exists-", ".dat")
        try {
            assertTrue(PlatformFiles.exists(path))
            PlatformFiles.delete(path)
            assertFalse(PlatformFiles.exists(path))
        } finally {
            // Cleanup in case test fails
            if (PlatformFiles.exists(path)) PlatformFiles.delete(path)
        }
    }

    @Test
    fun testDelete() {
        val path = PlatformFiles.createTempFile("test-delete-", ".dat")
        PlatformFiles.writeBytes(path, "data".encodeToByteArray())
        assertTrue(PlatformFiles.exists(path))
        assertTrue(PlatformFiles.delete(path))
        assertFalse(PlatformFiles.exists(path))
    }

    @Test
    fun testFileSize() {
        val path = PlatformFiles.createTempFile("test-size-", ".dat")
        try {
            val data = ByteArray(42) { 0x42 }
            PlatformFiles.writeBytes(path, data)
            assertEquals(42L, PlatformFiles.fileSize(path))
        } finally {
            PlatformFiles.delete(path)
        }
    }

    @Test
    fun testCreateTempFile() {
        val path = PlatformFiles.createTempFile("prefix-", ".suffix")
        try {
            assertTrue(PlatformFiles.exists(path))
            assertTrue(path.contains("prefix-"))
            assertTrue(path.endsWith(".suffix"))
        } finally {
            PlatformFiles.delete(path)
        }
    }
}
