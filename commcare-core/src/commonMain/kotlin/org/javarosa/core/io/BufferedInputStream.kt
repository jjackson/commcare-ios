package org.javarosa.core.io

import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * An implementation of a Buffered Stream for j2me compatible libraries.
 *
 * Very basic, no mark support (Pretty much only for web related streams
 * anyway).
 *
 * @author ctsims
 */
class BufferedInputStream : PlatformInputStream {

    // TODO: Better close semantics
    // TODO: Threadsafety

    private val `in`: PlatformInputStream
    private var buffer: ByteArray?

    private var position: Int = 0
    private var count: Int = 0

    constructor(`in`: PlatformInputStream) : this(`in`, 2048)

    constructor(`in`: PlatformInputStream, size: Int) {
        this.`in` = `in`
        this.buffer = ByteArray(size)
        cleanBuffer()
    }

    private fun cleanBuffer() {
        position = 0
        count = 0
    }

    @Throws(PlatformIOException::class)
    override fun available(): Int {
        if (count == -1) {
            return 0
        }
        // Size of our stream + the number of bytes we haven't yet read.
        return `in`.available() + (count - position)
    }

    @Throws(PlatformIOException::class)
    override fun close() {
        `in`.close()
        // clear up buffer
        buffer = null
    }

    @Throws(PlatformIOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        // If we've reached EOF, signal that.
        if (count == -1) {
            return -1
        }

        if (len == 0) {
            return 0
        }

        if (off == -1 || len == -1 || off + len > b.size) {
            throw IndexOutOfBoundsException("Bad inputs to input stream read")
        }

        var counter = 0
        var quitEarly = false
        while (counter != len && !quitEarly) {
            // TODO: System.arraycopy here?
            while (position < count && counter < len) {
                b[off + counter] = buffer!![position]
                counter++
                position++
            }

            // we read in as much as was requested;
            if (counter == len) {
                // don't need to do anything. We'll get bumped out of the loop
            } else if (position == count) {
                // If we didn't fill the buffer last time, we might be blocking on IO, so return
                // what we have and let the magic happen
                if (quitEarly) {
                    continue
                }

                // otherwise, try to fill that buffer
                if (!fillBuffer()) {
                    // Ok, so we didn't fill the whole thing. Either we're at the end of our stream (possible)
                    // or there was an incomplete read.

                    // EOF
                    if (count == -1) {
                        // We're at EOF. Two possible conditions here.

                        // 1) This was actually our first attempt on the end of stream. signal EOF
                        if (counter == 0) {
                            return -1
                        } else {
                            // 2) This was the last pile of bits. Return the ones we read.
                            return counter
                        }
                    }

                    // 0 is always an illegal return from here, so if we haven't read any bits yet, we need to do so now
                    if (counter != 0) {
                        // Incomplete read. Get the bits back. Hopefully the stream won't be blocked next time they try to read.
                        quitEarly = true
                    }
                }
            }
        }
        return counter
    }

    @Throws(PlatformIOException::class)
    private fun fillBuffer(): Boolean {
        if (count == -1) {
            // do nothing
            return false
        }
        position = 0
        count = `in`.read(buffer!!)
        return count == buffer!!.size
    }

    @Throws(PlatformIOException::class)
    override fun read(b: ByteArray): Int {
        return this.read(b, 0, b.size)
    }

    @Throws(PlatformIOException::class)
    override fun skip(len: Long): Long {
        // TODO: Something smarter here?
        val skipped = `in`.skip(len)
        if (skipped > count - position) {
            // need to reset our buffer positions, this buffer
            // is now expired.
            cleanBuffer()
        } else {
            // we skipped some number of bytes that just pushes us further
            // into the existing buffer

            // this has to be an integer-bound size, because it's smaller than
            // count - position, which is an integer size
            val bytesSkipped = skipped.toInt()
            position += bytesSkipped
        }
        return skipped
    }

    @Throws(PlatformIOException::class)
    override fun read(): Int {
        // If we've read all of the available buffer, fill
        // 'er up.
        if (position == count) {
            // This has to return at _least_ 1 byte, unless
            // the stream has ended
            fillBuffer()
        }

        // either this was true when we got here, or it's true
        // now. Either way, signal EOF
        if (count == -1) {
            return -1
        }

        // Otherwise, bump and return
        return buffer!![position++].toInt() and 0xFF
    }
}
