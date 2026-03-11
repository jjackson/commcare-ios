package org.javarosa.xpath.expr

import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.util.CacheTable
import org.javarosa.core.util.DataUtil
import org.javarosa.core.util.MathUtils
import org.javarosa.xpath.IExprDataType
import org.javarosa.xpath.XPathNodeset
import org.javarosa.xpath.XPathTypeMismatchException

import org.javarosa.core.model.utils.PlatformDate
import java.util.ArrayList
import java.util.HashMap

class FunctionUtils {
    companion object {
        @JvmStatic
        private val funcList = HashMap<String, Class<*>>()

        init {
            funcList[XPathDateFunc.NAME] = XPathDateFunc::class.java
            funcList[XpathCoalesceFunc.NAME] = XpathCoalesceFunc::class.java
            funcList[XPathTrueFunc.NAME] = XPathTrueFunc::class.java
            funcList[XPathNowFunc.NAME] = XPathNowFunc::class.java
            funcList[XPathNumberFunc.NAME] = XPathNumberFunc::class.java
            funcList[XPathSelectedFunc.NAME] = XPathSelectedFunc::class.java
            funcList[XPathBooleanFunc.NAME] = XPathBooleanFunc::class.java
            funcList[XPathLogTenFunc.NAME] = XPathLogTenFunc::class.java
            funcList[XPathExpFunc.NAME] = XPathExpFunc::class.java
            funcList[XPathChecklistFunc.NAME] = XPathChecklistFunc::class.java
            funcList[XPathAtanTwoFunc.NAME] = XPathAtanTwoFunc::class.java
            funcList[XPathSubstrFunc.NAME] = XPathSubstrFunc::class.java
            funcList[XPathStringFunc.NAME] = XPathStringFunc::class.java
            funcList[XPathEndsWithFunc.NAME] = XPathEndsWithFunc::class.java
            funcList[XPathDependFunc.NAME] = XPathDependFunc::class.java
            funcList[XPathDoubleFunc.NAME] = XPathDoubleFunc::class.java
            funcList[XPathTanFunc.NAME] = XPathTanFunc::class.java
            funcList[XPathReplaceFunc.NAME] = XPathReplaceFunc::class.java
            funcList[XPathJoinFunc.NAME] = XPathJoinFunc::class.java
            funcList[XPathFloorFunc.NAME] = XPathFloorFunc::class.java
            funcList[XPathPiFunc.NAME] = XPathPiFunc::class.java
            funcList[XPathFormatDateFunc.NAME] = XPathFormatDateFunc::class.java
            funcList[XPathFormatDateForCalendarFunc.NAME] = XPathFormatDateForCalendarFunc::class.java
            funcList[XPathMinFunc.NAME] = XPathMinFunc::class.java
            funcList[XPathSinFunc.NAME] = XPathSinFunc::class.java
            funcList[XPathBooleanFromStringFunc.NAME] = XPathBooleanFromStringFunc::class.java
            funcList[XPathCondFunc.NAME] = XPathCondFunc::class.java
            funcList[XPathSubstringBeforeFunc.NAME] = XPathSubstringBeforeFunc::class.java
            funcList[XPathCeilingFunc.NAME] = XPathCeilingFunc::class.java
            funcList[XPathPositionFunc.NAME] = XPathPositionFunc::class.java
            funcList[XPathStringLengthFunc.NAME] = XPathStringLengthFunc::class.java
            funcList[XPathRandomFunc.NAME] = XPathRandomFunc::class.java
            funcList[XPathMaxFunc.NAME] = XPathMaxFunc::class.java
            funcList[XPathAcosFunc.NAME] = XPathAcosFunc::class.java
            funcList[XPathAsinFunc.NAME] = XPathAsinFunc::class.java
            funcList[XPathIfFunc.NAME] = XPathIfFunc::class.java
            funcList[XPathLowerCaseFunc.NAME] = XPathLowerCaseFunc::class.java
            funcList[XPathIntFunc.NAME] = XPathIntFunc::class.java
            funcList[XPathDistanceFunc.NAME] = XPathDistanceFunc::class.java
            funcList[XPathWeightedChecklistFunc.NAME] = XPathWeightedChecklistFunc::class.java
            funcList[XPathUpperCaseFunc.NAME] = XPathUpperCaseFunc::class.java
            funcList[XPathCosFunc.NAME] = XPathCosFunc::class.java
            funcList[XPathFalseFunc.NAME] = XPathFalseFunc::class.java
            funcList[XPathLogFunc.NAME] = XPathLogFunc::class.java
            funcList[XPathRoundFunc.NAME] = XPathRoundFunc::class.java
            funcList[XPathSubstringAfterFunc.NAME] = XPathSubstringAfterFunc::class.java
            funcList[XPathAbsFunc.NAME] = XPathAbsFunc::class.java
            funcList[XPathTranslateFunc.NAME] = XPathTranslateFunc::class.java
            funcList[XPathCountSelectedFunc.NAME] = XPathCountSelectedFunc::class.java
            funcList[XPathSelectedAtFunc.NAME] = XPathSelectedAtFunc::class.java
            funcList[XPathCountFunc.NAME] = XPathCountFunc::class.java
            funcList[XPathPowFunc.NAME] = XPathPowFunc::class.java
            funcList[XPathContainsFunc.NAME] = XPathContainsFunc::class.java
            funcList[XPathNotFunc.NAME] = XPathNotFunc::class.java
            funcList[XPathSumFunc.NAME] = XPathSumFunc::class.java
            funcList[XPathRegexFunc.NAME] = XPathRegexFunc::class.java
            funcList[XPathAtanFunc.NAME] = XPathAtanFunc::class.java
            funcList[XPathStartsWithFunc.NAME] = XPathStartsWithFunc::class.java
            funcList[XPathTodayFunc.NAME] = XPathTodayFunc::class.java
            funcList[XPathConcatFunc.NAME] = XPathConcatFunc::class.java
            funcList[XPathSqrtFunc.NAME] = XPathSqrtFunc::class.java
            funcList[XPathUuidFunc.NAME] = XPathUuidFunc::class.java
            funcList[XPathIdCompressFunc.NAME] = XPathIdCompressFunc::class.java
            funcList[XPathJoinChunkFunc.NAME] = XPathJoinChunkFunc::class.java
            funcList[XPathChecksumFunc.NAME] = XPathChecksumFunc::class.java
            funcList[XPathSortFunc.NAME] = XPathSortFunc::class.java
            funcList[XPathSortByFunc.NAME] = XPathSortByFunc::class.java
            funcList[XPathDistinctValuesFunc.NAME] = XPathDistinctValuesFunc::class.java
            funcList[XPathSleepFunc.NAME] = XPathSleepFunc::class.java
            funcList[XPathIndexOfFunc.NAME] = XPathIndexOfFunc::class.java
            funcList[XPathEncryptStringFunc.NAME] = XPathEncryptStringFunc::class.java
            funcList[XPathDecryptStringFunc.NAME] = XPathDecryptStringFunc::class.java
            funcList[XPathJsonPropertyFunc.NAME] = XPathJsonPropertyFunc::class.java
            funcList[XPathClosestPointOnPolygonFunc.NAME] = XPathClosestPointOnPolygonFunc::class.java
            funcList[XPathIsPointInsidePolygonFunc.NAME] = XPathIsPointInsidePolygonFunc::class.java
        }

        @JvmStatic
        private val mDoubleParseCache = CacheTable<String, Double>()

        /**
         * Gets a human readable string representing an xpath nodeset.
         *
         * @param nodeset An xpath nodeset to be visualized
         * @return A string representation of the nodeset's references
         */
        @JvmStatic
        fun getSerializedNodeset(nodeset: XPathNodeset): String {
            if (nodeset.size() == 1) {
                return toString(nodeset)
            }

            val sb = StringBuffer()
            sb.append("{nodeset: ")
            for (i in 0 until nodeset.size()) {
                val ref = nodeset.getRefAt(i).toString(true)
                sb.append(ref)
                if (i != nodeset.size() - 1) {
                    sb.append(", ")
                }
            }
            sb.append("}")
            return sb.toString()
        }

        /**
         * Take in a value (only a string for now, TODO: Extend?) that doesn't
         * have any type information and attempt to infer a more specific type
         * that may assist in equality or comparison operations
         *
         * @param attrValue A typeless data object
         * @return The passed in object in as specific of a type as was able to
         * be identified.
         */
        @JvmStatic
        fun InferType(attrValue: String): Any {
            //Throwing exceptions from parsing doubles is _very_ slow, which is the purpose
            //of this cache. In high performant situations, this prevents a ton of overhead.
            val d = mDoubleParseCache.retrieve(attrValue)
            if (d != null) {
                if (d.isNaN()) {
                    return attrValue
                } else {
                    return d
                }
            }

            try {
                // Don't process strings with scientific notation or +/- Infinity as doubles
                if (checkForInvalidNumericOrDatestringCharacters(attrValue)) {
                    mDoubleParseCache.register(attrValue, java.lang.Double.valueOf(Double.NaN))
                    return attrValue
                }
                val ret = java.lang.Double.parseDouble(attrValue)
                mDoubleParseCache.register(attrValue, ret)
                return ret
            } catch (ife: NumberFormatException) {
                //Not a double
                mDoubleParseCache.register(attrValue, java.lang.Double.valueOf(Double.NaN))
            }
            //TODO: What about dates? That is a _super_ expensive
            //operation to be testing, though...
            return attrValue
        }

        /**
         * convert a value to a boolean using xpath's type conversion rules
         */
        @JvmStatic
        fun toBoolean(o: Any?): Boolean {
            var o = unpack(o)
            var `val`: Boolean? = null

            if (o is Boolean) {
                `val` = o
            } else if (o is Double) {
                val d = o
                `val` = Math.abs(d) > 1.0e-12 && !java.lang.Double.isNaN(d)
            } else if (o is String) {
                `val` = o.length > 0
            } else if (o is PlatformDate) {
                `val` = true
            } else if (o is IExprDataType) {
                `val` = o.toBoolean()
            }

            if (`val` != null) {
                return `val`
            } else {
                throw XPathTypeMismatchException("converting to boolean")
            }
        }

        @JvmStatic
        fun toDouble(o: Any?): Double {
            return if (o is PlatformDate) {
                DateUtils.fractionalDaysSinceEpoch(o)
            } else {
                toNumeric(o)
            }
        }

        /**
         * Convert a value to a number using xpath's type conversion rules (note that xpath itself makes
         * no distinction between integer and floating point numbers)
         */
        @JvmStatic
        fun toNumeric(o: Any?): Double {
            var o = unpack(o)
            var `val`: Double? = null

            if (o is Boolean) {
                `val` = java.lang.Double.valueOf(if (o) 1.0 else 0.0)
            } else if (o is Double) {
                `val` = o
            } else if (o is String) {
                val s = o.trim()
                if (checkForInvalidNumericOrDatestringCharacters(s)) {
                    return java.lang.Double.valueOf(Double.NaN)
                }
                try {
                    `val` = java.lang.Double.valueOf(java.lang.Double.parseDouble(s))
                } catch (nfe: NumberFormatException) {
                    try {
                        `val` = attemptDateConversion(s)
                    } catch (e: XPathTypeMismatchException) {
                        `val` = java.lang.Double.valueOf(Double.NaN)
                    }
                }
            } else if (o is PlatformDate) {
                `val` = java.lang.Double.valueOf(DateUtils.daysSinceEpoch(o).toDouble())
            } else if (o is IExprDataType) {
                `val` = o.toNumeric()
            }

            if (`val` != null) {
                return `val`
            } else {
                throw XPathTypeMismatchException("converting '${if (o == null) "null" else o.toString()}' to numeric")
            }
        }

        /**
         * The xpath spec doesn't recognize scientific notation, or +/-Infinity when converting a
         * string to a number
         */
        @JvmStatic
        internal fun checkForInvalidNumericOrDatestringCharacters(s: String): Boolean {
            for (i in 0 until s.length) {
                val c = s[i]
                if (c != '-' && c != '.' && (c < '0' || c > '9')) {
                    return true
                }
            }
            return false
        }

        private fun attemptDateConversion(s: String): Double {
            val o = toDate(s)
            if (o is PlatformDate) {
                return toNumeric(o)
            } else {
                throw XPathTypeMismatchException()
            }
        }

        /**
         * convert a number to an integer by truncating the fractional part. if non-numeric, coerce the
         * value to a number first. note that the resulting return value is still a Double, as required
         * by the xpath engine
         */
        @JvmStatic
        fun toInt(o: Any?): Double {
            val `val` = toNumeric(o)

            if (`val`.isInfinite() || `val`.isNaN()) {
                return `val`
            } else if (`val` >= Long.MAX_VALUE || `val` <= Long.MIN_VALUE) {
                return `val`
            } else {
                val l = `val`.toLong()
                var dbl = java.lang.Double.valueOf(l.toDouble())
                if (l == 0L && (`val` < 0.0 || `val` == java.lang.Double.valueOf(-0.0))) {
                    dbl = java.lang.Double.valueOf(-0.0)
                }
                return dbl
            }
        }

        /**
         * convert a value to a string using xpath's type conversion rules
         */
        @JvmStatic
        fun toString(o: Any?): String {
            var o = unpack(o)
            var `val`: String? = null

            if (o is Boolean) {
                `val` = if (o) "true" else "false"
            } else if (o is Double) {
                val d = o
                if (java.lang.Double.isNaN(d)) {
                    `val` = "NaN"
                } else if (Math.abs(d) < 1.0e-12) {
                    `val` = "0"
                } else if (java.lang.Double.isInfinite(d)) {
                    `val` = (if (d < 0) "-" else "") + "Infinity"
                } else if (Math.abs(d - d.toInt()) < 1.0e-12) {
                    `val` = d.toInt().toString()
                } else {
                    `val` = d.toString()
                }
            } else if (o is String) {
                `val` = o
            } else if (o is PlatformDate) {
                `val` = DateUtils.formatDate(o, DateUtils.FORMAT_ISO8601)
            } else if (o is IExprDataType) {
                `val` = o.toString()
            }

            if (`val` != null) {
                return `val`
            } else {
                if (o == null) {
                    throw XPathTypeMismatchException("attempt to cast null value to string")
                } else {
                    throw XPathTypeMismatchException("converting object of type ${o.javaClass} to string")
                }
            }
        }

        /**
         * convert a value to a date. note that xpath has no intrinsic representation of dates, so this
         * is off-spec. dates convert to strings as 'yyyy-mm-dd', convert to numbers as # of days since
         * the unix epoch, and convert to booleans always as 'true'
         *
         * string and int conversions are reversable, however:
         * * cannot convert bool to date
         * * empty string and NaN (xpath's 'null values') go unchanged, instead of being converted
         * into a date (which would cause an error, since Date has no null value (other than java
         * null, which the xpath engine can't handle))
         * * note, however, than non-empty strings that aren't valid dates _will_ cause an error
         * during conversion
         */
        @JvmStatic
        fun toDate(o: Any?): Any {
            val o = unpack(o)

            if (o is Double) {
                val n = toInt(o)

                if (n.isNaN()) {
                    return n
                }

                if (n.isInfinite() || n > Int.MAX_VALUE || n < Int.MIN_VALUE) {
                    throw XPathTypeMismatchException("converting out-of-range value to date")
                }

                return DateUtils.dateAdd(DateUtils.getDate(1970, 1, 1)!!, n.toInt())
            } else if (o is String) {
                if (o.length == 0) {
                    return o
                }

                val d = DateUtils.parseDateTime(o)
                if (d == null) {
                    throw XPathTypeMismatchException("converting string $o to date")
                } else {
                    return d
                }
            } else if (o is PlatformDate) {
                return DateUtils.roundDate(o)
            } else {
                val type = if (o == null) "null" else o.javaClass.name
                throw XPathTypeMismatchException("converting unexpected type $type to date")
            }
        }

        @JvmStatic
        internal fun expandDateSafe(dateObject: Any?): PlatformDate? {
            var dateObject = dateObject
            if (dateObject !is PlatformDate) {
                // try to expand this out of a nodeset
                dateObject = toDate(dateObject)
            }
            return if (dateObject is PlatformDate) {
                dateObject
            } else {
                null
            }
        }

        @JvmStatic
        internal fun subsetArgList(args: Array<Any?>, start: Int): Array<Any?> {
            return subsetArgList(args, start, 1)
        }

        /**
         * return a subset of an argument list as a new arguments list
         *
         * @param start index to start at
         * @param skip  sub-list will contain every nth argument, where n == skip (default: 1)
         */
        @JvmStatic
        internal fun subsetArgList(args: Array<Any?>, start: Int, skip: Int): Array<Any?> {
            if (start > args.size || skip < 1) {
                throw RuntimeException("error in subsetting arglist")
            }

            val subargs = arrayOfNulls<Any>(MathUtils.divLongNotSuck((args.size - start - 1).toLong(), skip.toLong()).toInt() + 1)
            var i = start
            var j = 0
            while (i < args.size) {
                subargs[j] = args[i]
                i += skip
                j++
            }

            return subargs
        }

        @JvmStatic
        fun unpack(o: Any?): Any? {
            return if (o is XPathNodeset) {
                o.unpack()
            } else {
                o
            }
        }

        /**
         * Perform toUpperCase or toLowerCase on given object.
         */
        @JvmStatic
        internal fun normalizeCase(o: Any?, toUpper: Boolean): String {
            val s = toString(o)
            return if (toUpper) {
                s.uppercase()
            } else {
                s.lowercase()
            }
        }

        /**
         * @return A sequence representation of the input, whether the input is
         * a nodeset (which will be dereferenced and evaluated), an existing sequence,
         * or a string representation of a sequence (space separated list of strings)
         */
        @JvmStatic
        fun getSequence(input: Any?): Array<Any?> {
            val argList: Array<Any?>
            if (input is XPathNodeset) {
                argList = input.toArgList()
            } else if (input is Array<*>) {
                @Suppress("UNCHECKED_CAST")
                argList = input as Array<Any?>
            } else {
                val selection = unpack(input) as String
                @Suppress("UNCHECKED_CAST")
                argList = DataUtil.splitOnSpaces(selection) as Array<Any?>
            }
            return argList
        }

        /**
         * Get list of base xpath functions
         *
         * (Used in formplayer for function auto-completion)
         */
        @Suppress("unused")
        @JvmStatic
        fun xPathFuncList(): List<String> {
            return ArrayList(funcList.keys)
        }

        @JvmStatic
        fun getXPathFuncListMap(): HashMap<String, Class<*>> {
            return funcList
        }
    }
}
