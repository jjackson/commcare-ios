package org.javarosa.core.io

import org.javarosa.core.util.externalizable.PlatformIOException
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

class StreamsUtil {

    // Unify the functional aspects here
    abstract inner class DirectionalIOException(
        @JvmField val internal: PlatformIOException
    ) : PlatformIOException(internal.message) {

        fun getWrapped(): PlatformIOException {
            return internal
        }

        // Note: printStackTrace not available in commonMain
    }

    inner class InputIOException(internal: PlatformIOException) : DirectionalIOException(internal)

    inner class OutputIOException(internal: PlatformIOException) : DirectionalIOException(internal)

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
        private fun writeFromInputToOutputInner(`in`: PlatformInputStream, out: PlatformOutputStream) {
            // TODO: God this is naive
            var value: Int
            try {
                value = `in`.read()
            } catch (e: PlatformIOException) {
                throw StreamsUtil().InputIOException(e)
            }
            while (value != -1) {
                try {
                    out.write(value)
                } catch (e: PlatformIOException) {
                    throw StreamsUtil().OutputIOException(e)
                }
                try {
                    value = `in`.read()
                } catch (e: PlatformIOException) {
                    throw StreamsUtil().InputIOException(e)
                }
            }
        }

        @JvmStatic
        @Throws(InputIOException::class, OutputIOException::class)
        fun writeFromInputToOutputSpecific(`in`: PlatformInputStream, out: PlatformOutputStream) {
            writeFromInputToOutputInner(`in`, out)
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun writeFromInputToOutput(`in`: PlatformInputStream, out: PlatformOutputStream) {
            try {
                writeFromInputToOutputInner(`in`, out)
            } catch (e: InputIOException) {
                throw e.internal
            } catch (e: OutputIOException) {
                throw e.internal
            }
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun inputStreamToByteArray(input: PlatformInputStream): ByteArray {
            val buffer = ByteArray(8192)
            var bytesRead: Int
            val output = createByteArrayOutputStream()
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
            return byteArrayOutputStreamToBytes(output)
        }

        /**
         * Writes input stream to output stream in a buffered fashion, but doesn't
         * close either stream.
         */
        @JvmStatic
        @Throws(InputIOException::class, OutputIOException::class)
        fun writeFromInputToOutputUnmanaged(`is`: PlatformInputStream, os: PlatformOutputStream) {
            var count: Int
            val buffer = ByteArray(8192)
            try {
                count = `is`.read(buffer)
            } catch (e: PlatformIOException) {
                throw StreamsUtil().InputIOException(e)
            }
            while (count != -1) {
                try {
                    os.write(buffer, 0, count)
                } catch (e: PlatformIOException) {
                    throw StreamsUtil().OutputIOException(e)
                }
                try {
                    count = `is`.read(buffer)
                } catch (e: PlatformIOException) {
                    throw StreamsUtil().InputIOException(e)
                }
            }
        }

        /**
         * Write is to os and close both
         */
        @JvmStatic
        @Throws(InputIOException::class, OutputIOException::class)
        fun writeFromInputToOutputNew(`is`: PlatformInputStream, os: PlatformOutputStream) {
            writeFromInputToOutputNewInner(`is`, os, null)
        }

        /**
         * Write is to os and close both
         */
        @JvmStatic
        @Throws(InputIOException::class, OutputIOException::class)
        fun writeFromInputToOutputNew(`is`: PlatformInputStream, os: PlatformOutputStream, observer: StreamReadObserver) {
            writeFromInputToOutputNewInner(`is`, os, observer)
        }

        /**
         * Write is to os and close both
         */
        @JvmStatic
        @Throws(InputIOException::class, OutputIOException::class)
        private fun writeFromInputToOutputNewInner(
            `is`: PlatformInputStream,
            os: PlatformOutputStream,
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
        private fun writeFromBuffer(os: PlatformOutputStream, buffer: ByteArray, count: Int) {
            try {
                os.write(buffer, 0, count)
            } catch (e: PlatformIOException) {
                throw StreamsUtil().OutputIOException(e)
            }
        }

        @Throws(InputIOException::class)
        private fun readIntoBuffer(`is`: PlatformInputStream, buffer: ByteArray): Int {
            try {
                return `is`.read(buffer)
            } catch (e: PlatformIOException) {
                throw StreamsUtil().InputIOException(e)
            }
        }

        @JvmStatic
        fun closeStream(stream: PlatformInputStream?) {
            try {
                stream?.close()
            } catch (e: PlatformIOException) {
                e.printStackTrace()
            }
        }

        @JvmStatic
        fun closeStream(stream: PlatformOutputStream?) {
            try {
                stream?.close()
            } catch (e: PlatformIOException) {
                e.printStackTrace()
            }
        }
    }
}
