package org.javarosa.core.io

import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Read all bytes from a PlatformInputStream and decode as UTF-8 text,
 * then split into lines. Cross-platform replacement for
 * BufferedReader(InputStreamReader(is, "UTF-8")).
 */
@Throws(PlatformIOException::class)
fun platformReadAllLines(input: PlatformInputStream): List<String> {
    val text = platformReadAllText(input)
    if (text.isEmpty()) return emptyList()
    return text.lines()
}

/**
 * Read all bytes from a PlatformInputStream and decode as UTF-8 string.
 */
@Throws(PlatformIOException::class)
fun platformReadAllText(input: PlatformInputStream): String {
    val buffer = ByteArray(4096)
    val result = mutableListOf<Byte>()
    var bytesRead = input.read(buffer)
    while (bytesRead > 0) {
        for (i in 0 until bytesRead) {
            result.add(buffer[i])
        }
        bytesRead = input.read(buffer)
    }
    return result.toByteArray().decodeToString()
}
