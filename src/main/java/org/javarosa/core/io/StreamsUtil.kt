package org.javarosa.core.io

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class StreamsUtil {

    // Unify the functional aspects here
    abstract inner class DirectionalIOException(
        @JvmField val internal: IOException
    ) : IOException(internal.message) {

        fun getWrapped(): IOException {
            return internal
        }

        override fun printStackTrace() {
            internal.printStackTrace()
        }

        // TODO: Override all common methods
    }

    inner class InputIOException(internal: IOException) : DirectionalIOException(internal)

    inner class OutputIOException(internal: IOException) : DirectionalIOException(internal)

    interface StreamReadObserver {
        fun notifyCurrentCount(bytesRead: Long)
    }

    companion object {
        /**
         * Write everything from input stream to output stream, byte by byte then
         * close the streams
         */
        @JvmStatic
        @Throws(InputIOException::class, OutputIOException::class)
        private fun writeFromInputToOutputInner(`in`: InputStream, out: OutputStream) {
            // TODO: God this is naive
            var value: Int
            try {
                value = `in`.read()
            } catch (e: IOException) {
                throw StreamsUtil().InputIOException(e)
            }
            while (value != -1) {
                try {
                    out.write(value)
                } catch (e: IOException) {
                    throw StreamsUtil().OutputIOException(e)
                }
                try {
                    value = `in`.read()
                } catch (e: IOException) {
                    throw StreamsUtil().InputIOException(e)
                }
            }
        }

        @JvmStatic
        @Throws(InputIOException::class, OutputIOException::class)
        fun writeFromInputToOutputSpecific(`in`: InputStream, out: OutputStream) {
            writeFromInputToOutputInner(`in`, out)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun writeFromInputToOutput(`in`: InputStream, out: OutputStream) {
            try {
                writeFromInputToOutputInner(`in`, out)
            } catch (e: InputIOException) {
                throw e.internal
            } catch (e: OutputIOException) {
                throw e.internal
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun inputStreamToByteArray(input: InputStream): ByteArray {
            val buffer = ByteArray(8192)
            var bytesRead: Int
            val output = ByteArrayOutputStream()
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
            return output.toByteArray()
        }

        /**
         * Writes input stream to output stream in a buffered fashion, but doesn't
         * close either stream.
         */
        @JvmStatic
        @Throws(InputIOException::class, OutputIOException::class)
        fun writeFromInputToOutputUnmanaged(`is`: InputStream, os: OutputStream) {
            var count: Int
            val buffer = ByteArray(8192)
            try {
                count = `is`.read(buffer)
            } catch (e: IOException) {
                throw StreamsUtil().InputIOException(e)
            }
            while (count != -1) {
                try {
                    os.write(buffer, 0, count)
                } catch (e: IOException) {
                    throw StreamsUtil().OutputIOException(e)
                }
                try {
                    count = `is`.read(buffer)
                } catch (e: IOException) {
                    throw StreamsUtil().InputIOException(e)
                }
            }
        }

        /**
         * Write is to os and close both
         */
        @JvmStatic
        @Throws(InputIOException::class, OutputIOException::class)
        fun writeFromInputToOutputNew(`is`: InputStream, os: OutputStream) {
            writeFromInputToOutputNewInner(`is`, os, null)
        }

        /**
         * Write is to os and close both
         */
        @JvmStatic
        @Throws(InputIOException::class, OutputIOException::class)
        fun writeFromInputToOutputNew(`is`: InputStream, os: OutputStream, observer: StreamReadObserver) {
            writeFromInputToOutputNewInner(`is`, os, observer)
        }

        /**
         * Write is to os and close both
         */
        @JvmStatic
        @Throws(InputIOException::class, OutputIOException::class)
        private fun writeFromInputToOutputNewInner(
            `is`: InputStream,
            os: OutputStream,
            observer: StreamReadObserver?
        ) {
            val buffer = ByteArray(8192)
            var counter = 0L

            try {
                var count = readIntoBuffer(`is`, buffer)
                while (count != -1) {
                    counter += count
                    observer?.notifyCurrentCount(counter)
                    writeFromBuffer(os, buffer, count)
                    count = readIntoBuffer(`is`, buffer)
                }
            } finally {
                closeStream(`is`)
                closeStream(os)
            }
        }

        @Throws(OutputIOException::class)
        private fun writeFromBuffer(os: OutputStream, buffer: ByteArray, count: Int) {
            try {
                os.write(buffer, 0, count)
            } catch (e: IOException) {
                throw StreamsUtil().OutputIOException(e)
            }
        }

        @Throws(InputIOException::class)
        private fun readIntoBuffer(`is`: InputStream, buffer: ByteArray): Int {
            try {
                return `is`.read(buffer)
            } catch (e: IOException) {
                throw StreamsUtil().InputIOException(e)
            }
        }

        @JvmStatic
        fun closeStream(stream: Closeable?) {
            try {
                stream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
