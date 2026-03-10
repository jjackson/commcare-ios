package org.javarosa.core.util.externalizable;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests that PlatformDataInputStream and PlatformDataOutputStream produce
 * byte-compatible output with java.io.DataInputStream/DataOutputStream.
 */
public class PlatformStreamRoundTripTest {

    @Test
    public void testRoundTripByte() {
        PlatformDataOutputStream out = new PlatformDataOutputStream();
        out.writeByte(0);
        out.writeByte(127);
        out.writeByte(-128);
        out.writeByte(255); // wraps to -1 as signed byte

        PlatformDataInputStream in = new PlatformDataInputStream(out.toByteArray());
        assertEquals((byte) 0, in.readByte());
        assertEquals((byte) 127, in.readByte());
        assertEquals((byte) -128, in.readByte());
        assertEquals((byte) -1, in.readByte());
        in.close();
    }

    @Test
    public void testRoundTripInt() {
        PlatformDataOutputStream out = new PlatformDataOutputStream();
        out.writeInt(0);
        out.writeInt(1);
        out.writeInt(-1);
        out.writeInt(Integer.MAX_VALUE);
        out.writeInt(Integer.MIN_VALUE);
        out.writeInt(0x12345678);

        PlatformDataInputStream in = new PlatformDataInputStream(out.toByteArray());
        assertEquals(0, in.readInt());
        assertEquals(1, in.readInt());
        assertEquals(-1, in.readInt());
        assertEquals(Integer.MAX_VALUE, in.readInt());
        assertEquals(Integer.MIN_VALUE, in.readInt());
        assertEquals(0x12345678, in.readInt());
        in.close();
    }

    @Test
    public void testRoundTripLong() {
        PlatformDataOutputStream out = new PlatformDataOutputStream();
        out.writeLong(0L);
        out.writeLong(1L);
        out.writeLong(-1L);
        out.writeLong(Long.MAX_VALUE);
        out.writeLong(Long.MIN_VALUE);
        out.writeLong(0x123456789ABCDEF0L);

        PlatformDataInputStream in = new PlatformDataInputStream(out.toByteArray());
        assertEquals(0L, in.readLong());
        assertEquals(1L, in.readLong());
        assertEquals(-1L, in.readLong());
        assertEquals(Long.MAX_VALUE, in.readLong());
        assertEquals(Long.MIN_VALUE, in.readLong());
        assertEquals(0x123456789ABCDEF0L, in.readLong());
        in.close();
    }

    @Test
    public void testRoundTripChar() {
        PlatformDataOutputStream out = new PlatformDataOutputStream();
        out.writeChar('A');
        out.writeChar('Z');
        out.writeChar('\u00E9'); // e-acute
        out.writeChar('\u4E16'); // Chinese character

        PlatformDataInputStream in = new PlatformDataInputStream(out.toByteArray());
        assertEquals('A', in.readChar());
        assertEquals('Z', in.readChar());
        assertEquals('\u00E9', in.readChar());
        assertEquals('\u4E16', in.readChar());
        in.close();
    }

    @Test
    public void testRoundTripDouble() {
        PlatformDataOutputStream out = new PlatformDataOutputStream();
        out.writeDouble(0.0);
        out.writeDouble(1.0);
        out.writeDouble(-1.0);
        out.writeDouble(Double.MAX_VALUE);
        out.writeDouble(Double.MIN_VALUE);
        out.writeDouble(Math.PI);
        out.writeDouble(Double.NaN);
        out.writeDouble(Double.POSITIVE_INFINITY);
        out.writeDouble(Double.NEGATIVE_INFINITY);

        PlatformDataInputStream in = new PlatformDataInputStream(out.toByteArray());
        assertEquals(0.0, in.readDouble(), 0.0);
        assertEquals(1.0, in.readDouble(), 0.0);
        assertEquals(-1.0, in.readDouble(), 0.0);
        assertEquals(Double.MAX_VALUE, in.readDouble(), 0.0);
        assertEquals(Double.MIN_VALUE, in.readDouble(), 0.0);
        assertEquals(Math.PI, in.readDouble(), 0.0);
        assertTrue(Double.isNaN(in.readDouble()));
        assertEquals(Double.POSITIVE_INFINITY, in.readDouble(), 0.0);
        assertEquals(Double.NEGATIVE_INFINITY, in.readDouble(), 0.0);
        in.close();
    }

    @Test
    public void testRoundTripBoolean() {
        PlatformDataOutputStream out = new PlatformDataOutputStream();
        out.writeBoolean(true);
        out.writeBoolean(false);

        PlatformDataInputStream in = new PlatformDataInputStream(out.toByteArray());
        assertTrue(in.readBoolean());
        assertFalse(in.readBoolean());
        in.close();
    }

    @Test
    public void testRoundTripUTF() {
        PlatformDataOutputStream out = new PlatformDataOutputStream();
        out.writeUTF("Hello, World!");
        out.writeUTF("");
        out.writeUTF("café");
        out.writeUTF("\u4E16\u754C"); // "世界" (Chinese for "world")
        out.writeUTF("abc\u0000def"); // embedded null (modified UTF-8 edge case)

        PlatformDataInputStream in = new PlatformDataInputStream(out.toByteArray());
        assertEquals("Hello, World!", in.readUTF());
        assertEquals("", in.readUTF());
        assertEquals("café", in.readUTF());
        assertEquals("\u4E16\u754C", in.readUTF());
        assertEquals("abc\u0000def", in.readUTF());
        in.close();
    }

    @Test
    public void testRoundTripBytes() {
        byte[] data = new byte[]{1, 2, 3, 4, 5, -1, -128, 127, 0};
        PlatformDataOutputStream out = new PlatformDataOutputStream();
        out.write(data);

        PlatformDataInputStream in = new PlatformDataInputStream(out.toByteArray());
        byte[] result = new byte[data.length];
        in.readFully(result);
        assertArrayEquals(data, result);
        in.close();
    }

    @Test
    public void testRoundTripPartialRead() {
        byte[] data = new byte[]{10, 20, 30, 40, 50};
        PlatformDataOutputStream out = new PlatformDataOutputStream();
        out.write(data);

        PlatformDataInputStream in = new PlatformDataInputStream(out.toByteArray());
        byte[] buf = new byte[3];
        int read = in.read(buf, 0, 3);
        assertEquals(3, read);
        assertEquals(10, buf[0]);
        assertEquals(20, buf[1]);
        assertEquals(30, buf[2]);
        assertEquals(2, in.available());
        in.close();
    }

    @Test
    public void testMixedTypes() {
        PlatformDataOutputStream out = new PlatformDataOutputStream();
        out.writeInt(42);
        out.writeUTF("test");
        out.writeBoolean(true);
        out.writeDouble(3.14);
        out.writeLong(999999999999L);
        out.writeByte(0xFF);
        out.writeChar('X');

        PlatformDataInputStream in = new PlatformDataInputStream(out.toByteArray());
        assertEquals(42, in.readInt());
        assertEquals("test", in.readUTF());
        assertTrue(in.readBoolean());
        assertEquals(3.14, in.readDouble(), 0.0);
        assertEquals(999999999999L, in.readLong());
        assertEquals((byte) -1, in.readByte());
        assertEquals('X', in.readChar());
        assertEquals(0, in.available());
        in.close();
    }

    /**
     * Verify that Platform streams produce byte-identical output to java.io.Data*Stream.
     * This ensures cross-platform serialization compatibility.
     */
    @Test
    public void testByteCompatibilityWithJavaIO() throws Exception {
        // Write using java.io
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream javaDos = new DataOutputStream(baos);
        javaDos.writeInt(42);
        javaDos.writeUTF("Hello");
        javaDos.writeBoolean(true);
        javaDos.writeDouble(Math.PI);
        javaDos.writeLong(Long.MAX_VALUE);
        javaDos.writeChar('Z');
        javaDos.writeByte(99);
        javaDos.flush();
        byte[] javaBytes = baos.toByteArray();

        // Write using Platform streams
        PlatformDataOutputStream platformDos = new PlatformDataOutputStream();
        platformDos.writeInt(42);
        platformDos.writeUTF("Hello");
        platformDos.writeBoolean(true);
        platformDos.writeDouble(Math.PI);
        platformDos.writeLong(Long.MAX_VALUE);
        platformDos.writeChar('Z');
        platformDos.writeByte(99);
        byte[] platformBytes = platformDos.toByteArray();

        // Byte-identical output
        assertArrayEquals(javaBytes, platformBytes);

        // Read java.io bytes with Platform stream
        PlatformDataInputStream platformDis = new PlatformDataInputStream(javaBytes);
        assertEquals(42, platformDis.readInt());
        assertEquals("Hello", platformDis.readUTF());
        assertTrue(platformDis.readBoolean());
        assertEquals(Math.PI, platformDis.readDouble(), 0.0);
        assertEquals(Long.MAX_VALUE, platformDis.readLong());
        assertEquals('Z', platformDis.readChar());
        assertEquals(99, platformDis.readByte());

        // Read Platform bytes with java.io
        DataInputStream javaDis = new DataInputStream(new ByteArrayInputStream(platformBytes));
        assertEquals(42, javaDis.readInt());
        assertEquals("Hello", javaDis.readUTF());
        assertTrue(javaDis.readBoolean());
        assertEquals(Math.PI, javaDis.readDouble(), 0.0);
        assertEquals(Long.MAX_VALUE, javaDis.readLong());
        assertEquals('Z', javaDis.readChar());
        assertEquals(99, javaDis.readByte());
    }

    @Test
    public void testWritePartialByteArray() {
        byte[] data = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        PlatformDataOutputStream out = new PlatformDataOutputStream();
        out.write(data, 3, 4); // write bytes at indices 3,4,5,6

        byte[] result = out.toByteArray();
        assertEquals(4, result.length);
        assertEquals(3, result[0]);
        assertEquals(4, result[1]);
        assertEquals(5, result[2]);
        assertEquals(6, result[3]);
    }

    @Test
    public void testWriteSingleByte() {
        PlatformDataOutputStream out = new PlatformDataOutputStream();
        out.write(0xAB);

        PlatformDataInputStream in = new PlatformDataInputStream(out.toByteArray());
        assertEquals((byte) 0xAB, in.readByte());
    }
}
