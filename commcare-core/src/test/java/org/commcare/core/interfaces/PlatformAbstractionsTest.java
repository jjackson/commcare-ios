package org.commcare.core.interfaces;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for platform abstraction layer (Wave 4).
 */
public class PlatformAbstractionsTest {

    // --- URL Tests ---

    @Test
    public void testUrlParsing() {
        PlatformUrl url = new PlatformUrl("https://www.example.com:8080/path/to/resource?key=value");
        assertEquals("https", url.getScheme());
        assertEquals("www.example.com", url.getHost());
        assertEquals(8080, url.getPort());
        assertEquals("/path/to/resource", url.getPath());
        assertEquals("key=value", url.getQuery());
    }

    @Test
    public void testUrlParsingNoPort() {
        PlatformUrl url = new PlatformUrl("http://example.com/path");
        assertEquals("http", url.getScheme());
        assertEquals("example.com", url.getHost());
        assertEquals(-1, url.getPort());
        assertEquals("/path", url.getPath());
        assertNull(url.getQuery());
    }

    @Test
    public void testUrlToString() {
        String original = "https://commcare.example.com/api/v1/cases";
        PlatformUrl url = new PlatformUrl(original);
        assertEquals(original, url.toString());
    }

    @Test
    public void testIsValidUrl() {
        assertTrue(PlatformUrlKt.isValidUrl("https://example.com"));
        assertTrue(PlatformUrlKt.isValidUrl("http://localhost:8080/path?q=1"));
        assertFalse(PlatformUrlKt.isValidUrl("not a url"));
        assertFalse(PlatformUrlKt.isValidUrl(""));
    }

    // --- Crypto Tests ---

    @Test
    public void testSha256() {
        byte[] hash = PlatformCrypto.INSTANCE.sha256("hello".getBytes());
        assertNotNull(hash);
        assertEquals(32, hash.length); // SHA-256 is 32 bytes
    }

    @Test
    public void testSha256Deterministic() {
        byte[] hash1 = PlatformCrypto.INSTANCE.sha256("test".getBytes());
        byte[] hash2 = PlatformCrypto.INSTANCE.sha256("test".getBytes());
        assertArrayEquals(hash1, hash2);
    }

    @Test
    public void testMd5() {
        byte[] hash = PlatformCrypto.INSTANCE.md5("hello".getBytes());
        assertNotNull(hash);
        assertEquals(16, hash.length); // MD5 is 16 bytes
    }

    @Test
    public void testRandomBytes() {
        byte[] bytes = PlatformCrypto.INSTANCE.randomBytes(32);
        assertNotNull(bytes);
        assertEquals(32, bytes.length);
    }

    @Test
    public void testAesRoundTrip() {
        byte[] key = PlatformCrypto.INSTANCE.generateAesKey(256);
        byte[] plaintext = "Hello, CommCare!".getBytes();
        byte[] encrypted = PlatformCrypto.INSTANCE.aesEncrypt(plaintext, key);
        byte[] decrypted = PlatformCrypto.INSTANCE.aesDecrypt(encrypted, key);
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    public void testGenerateAesKey() {
        byte[] key128 = PlatformCrypto.INSTANCE.generateAesKey(128);
        assertEquals(16, key128.length);
        byte[] key256 = PlatformCrypto.INSTANCE.generateAesKey(256);
        assertEquals(32, key256.length);
    }

    // --- File I/O Tests ---

    @Test
    public void testFileRoundTrip() throws Exception {
        String path = PlatformFiles.INSTANCE.createTempFile("test", ".dat");
        try {
            byte[] data = "test file content".getBytes();
            PlatformFiles.INSTANCE.writeBytes(path, data);
            assertTrue(PlatformFiles.INSTANCE.exists(path));
            byte[] read = PlatformFiles.INSTANCE.readBytes(path);
            assertArrayEquals(data, read);
            assertEquals(data.length, PlatformFiles.INSTANCE.fileSize(path));
            assertFalse(PlatformFiles.INSTANCE.isDirectory(path));
        } finally {
            PlatformFiles.INSTANCE.delete(path);
            assertFalse(PlatformFiles.INSTANCE.exists(path));
        }
    }

    @Test
    public void testListDir() throws Exception {
        String tempFile = PlatformFiles.INSTANCE.createTempFile("listdir", ".tmp");
        try {
            File f = new File(tempFile);
            String dir = f.getParent();
            assertTrue(PlatformFiles.INSTANCE.isDirectory(dir));
            assertTrue(PlatformFiles.INSTANCE.listDir(dir).size() > 0);
        } finally {
            PlatformFiles.INSTANCE.delete(tempFile);
        }
    }
}
