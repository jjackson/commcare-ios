package org.javarosa.core.services.locale

import org.javarosa.core.io.PlatformInputStream
import org.javarosa.core.io.platformReadAllLines
import org.javarosa.core.util.externalizable.PlatformIOException
import kotlin.jvm.JvmStatic

/**
 * @author ctsims
 */
object LocalizationUtils {
    /**
     * @param is A path to a resource file provided in the current environment
     * @return a dictionary of key/value locale pairs from a file in the resource directory
     */
    @JvmStatic
    @Throws(PlatformIOException::class)
    fun parseLocaleInput(`is`: PlatformInputStream): HashMap<String, String> {
        val locale = HashMap<String, String>()
        val lines = platformReadAllLines(`is`)

        var lineCount = 0
        for (line in lines) {
            parseAndAdd(locale, line, lineCount++)
        }

        return locale
    }

    @JvmStatic
    fun parseAndAdd(locale: HashMap<String, String>, line: String, curline: Int) {
        var line = line.trim()

        var i: Int
        var dec = line.length

        //clear comments except if they have backslash before them (markdown '#'s)
        while (lastIndexOf(line.substring(0, dec), "#").also { i = it } != -1) {
            if (i == 0 || line[i - 1] != '\\') {
                line = line.substring(0, i)
                dec = line.length
            } else {
                dec = i
            }
        }

        val equalIndex = line.indexOf('=')
        if (equalIndex == -1) {
            if (line.trim() != "") {
                println("Invalid line (#$curline) read: $line")
            }
        } else {
            //Check to see if there's anything after the '=' first. Otherwise there
            //might be some big problems.
            if (equalIndex != line.length - 1) {
                val value = line.substring(equalIndex + 1, line.length)
                locale.put(line.substring(0, equalIndex), parseValue(value))
            }
        }
    }

    /**
     * Replace markdown encodings
     */
    @JvmStatic
    fun parseValue(value: String): String {
        var ret = replace(value, "\\#", "#")
        ret = replace(ret, "\\n", "\n")
        return ret
    }

    /**
     * http://stackoverflow.com/questions/10626606/replace-string-with-string-in-j2me
     */
    private fun replace(str: String, pattern: String, replace: String): String {
        var s = 0
        var e: Int
        val result = StringBuilder()

        while (str.indexOf(pattern, s).also { e = it } >= 0) {
            result.append(str.substring(s, e))
            result.append(replace)
            s = e + pattern.length
        }
        result.append(str.substring(s))
        return result.toString()
    }

    /**
     * http://www.experts-exchange.com/Programming/Languages/Java/Q_27604323.html
     */
    private fun lastIndexOf(str: String, search: String): Int {
        var i: Int
        var offset = 0
        var found = -1

        while (offset < str.length) {
            i = str.indexOf(search, offset)
            if (i == -1) break

            found = i

            offset = i + 1
        }
        return found
    }
}
