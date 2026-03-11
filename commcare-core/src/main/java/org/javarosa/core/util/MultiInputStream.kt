package org.javarosa.core.util

import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.io.PlatformInputStream

/**
 * MultiInputStream allows for concatenating multiple
 * input streams together to be read serially in the
 * order that they were added.
 *
 * A MultiInputStream must have all of its component
 * streams added to it before it can be read from. Once
 * the stream is ready, it should be prepare()d before
 * the first read.
 *
 * @author Clayton Sims
 */
class MultiInputStream : PlatformInputStream() {

    private val streams: ArrayList<PlatformInputStream> = ArrayList()

    private var currentStream: Int = -1

    fun addStream(stream: PlatformInputStream) {
        streams.add(stream)
    }

    /**
     * Finalize the stream and allow it to be read
     * from.
     *
     * @return True if the stream is ready to be read
     * from. False if the stream couldn't be prepared
     * because it was empty.
     */
    fun prepare(): Boolean {
        return if (streams.size == 0) {
            false
        } else {
            currentStream = 0
            true
        }
    }

    @Throws(PlatformIOException::class)
    override fun read(): Int {
        if (currentStream == -1) {
            throw PlatformIOException("Cannot read from unprepared MultiInputStream!")
        }
        var cur = streams[currentStream]
        var next = cur.read()

        if (next != -1) {
            return next
        }

        // Otherwise, end of Stream

        // Loop through the available streams until we read something that isn't
        // an end of stream
        while (next == -1 && currentStream + 1 < streams.size) {
            currentStream++
            cur = streams[currentStream]
            next = cur.read()
        }

        // Will be either a valid value or -1 if we've run out of streams.
        return next
    }

    @Throws(PlatformIOException::class)
    override fun available(): Int {
        if (currentStream == -1) {
            throw PlatformIOException("Cannot read from unprepared MultiInputStream!")
        }
        return streams[currentStream].available()
    }

    @Throws(PlatformIOException::class)
    override fun close() {
        if (currentStream == -1) {
            throw PlatformIOException("Cannot read from unprepared MultiInputStream!")
        }
        val en = streams.iterator()
        while (en.hasNext()) {
            en.next().close()
        }
    }
}
