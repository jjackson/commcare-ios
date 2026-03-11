package org.javarosa.xpath.expr
import kotlin.jvm.JvmStatic

import org.javarosa.core.model.utils.PlatformDateUtils
import org.javarosa.core.util.CacheTable
import org.javarosa.core.util.DataUtil
import org.javarosa.core.util.MathUtils
import org.javarosa.xpath.IExprDataType
import org.javarosa.xpath.XPathNodeset
import org.javarosa.xpath.XPathTypeMismatchException

import org.javarosa.core.model.utils.PlatformDate

class FunctionUtils {
    companion object {
        @JvmStatic
        private val funcList = HashMap<String, () -> XPathFuncExpr>()

        init {
            funcList[XPathDateFunc.NAME] = { XPathDateFunc() }
            funcList[XpathCoalesceFunc.NAME] = { XpathCoalesceFunc() }
            funcList[XPathTrueFunc.NAME] = { XPathTrueFunc() }
            funcList[XPathNowFunc.NAME] = { XPathNowFunc() }
            funcList[XPathNumberFunc.NAME] = { XPathNumberFunc() }
            funcList[XPathSelectedFunc.NAME] = { XPathSelectedFunc() }
            funcList[XPathBooleanFunc.NAME] = { XPathBooleanFunc() }
            funcList[XPathLogTenFunc.NAME] = { XPathLogTenFunc() }
            funcList[XPathExpFunc.NAME] = { XPathExpFunc() }
            funcList[XPathChecklistFunc.NAME] = { XPathChecklistFunc() }
            funcList[XPathAtanTwoFunc.NAME] = { XPathAtanTwoFunc() }
            funcList[XPathSubstrFunc.NAME] = { XPathSubstrFunc() }
            funcList[XPathStringFunc.NAME] = { XPathStringFunc() }
            funcList[XPathEndsWithFunc.NAME] = { XPathEndsWithFunc() }
            funcList[XPathDependFunc.NAME] = { XPathDependFunc() }
            funcList[XPathDoubleFunc.NAME] = { XPathDoubleFunc() }
            funcList[XPathTanFunc.NAME] = { XPathTanFunc() }
            funcList[XPathReplaceFunc.NAME] = { XPathReplaceFunc() }
            funcList[XPathJoinFunc.NAME] = { XPathJoinFunc() }
            funcList[XPathFloorFunc.NAME] = { XPathFloorFunc() }
            funcList[XPathPiFunc.NAME] = { XPathPiFunc() }
            funcList[XPathFormatDateFunc.NAME] = { XPathFormatDateFunc() }
            funcList[XPathFormatDateForCalendarFunc.NAME] = { XPathFormatDateForCalendarFunc() }
            funcList[XPathMinFunc.NAME] = { XPathMinFunc() }
            funcList[XPathSinFunc.NAME] = { XPathSinFunc() }
            funcList[XPathBooleanFromStringFunc.NAME] = { XPathBooleanFromStringFunc() }
            funcList[XPathCondFunc.NAME] = { XPathCondFunc() }
            funcList[XPathSubstringBeforeFunc.NAME] = { XPathSubstringBeforeFunc() }
            funcList[XPathCeilingFunc.NAME] = { XPathCeilingFunc() }
            funcList[XPathPositionFunc.NAME] = { XPathPositionFunc() }
            funcList[XPathStringLengthFunc.NAME] = { XPathStringLengthFunc() }
            funcList[XPathRandomFunc.NAME] = { XPathRandomFunc() }
            funcList[XPathMaxFunc.NAME] = { XPathMaxFunc() }
            funcList[XPathAcosFunc.NAME] = { XPathAcosFunc() }
            funcList[XPathAsinFunc.NAME] = { XPathAsinFunc() }
            funcList[XPathIfFunc.NAME] = { XPathIfFunc() }
            funcList[XPathLowerCaseFunc.NAME] = { XPathLowerCaseFunc() }
            funcList[XPathIntFunc.NAME] = { XPathIntFunc() }
            funcList[XPathDistanceFunc.NAME] = { XPathDistanceFunc() }
            funcList[XPathWeightedChecklistFunc.NAME] = { XPathWeightedChecklistFunc() }
            funcList[XPathUpperCaseFunc.NAME] = { XPathUpperCaseFunc() }
            funcList[XPathCosFunc.NAME] = { XPathCosFunc() }
            funcList[XPathFalseFunc.NAME] = { XPathFalseFunc() }
            funcList[XPathLogFunc.NAME] = { XPathLogFunc() }
            funcList[XPathRoundFunc.NAME] = { XPathRoundFunc() }
            funcList[XPathSubstringAfterFunc.NAME] = { XPathSubstringAfterFunc() }
            funcList[XPathAbsFunc.NAME] = { XPathAbsFunc() }
            funcList[XPathTranslateFunc.NAME] = { XPathTranslateFunc() }
            funcList[XPathCountSelectedFunc.NAME] = { XPathCountSelectedFunc() }
            funcList[XPathSelectedAtFunc.NAME] = { XPathSelectedAtFunc() }
            funcList[XPathCountFunc.NAME] = { XPathCountFunc() }
            funcList[XPathPowFunc.NAME] = { XPathPowFunc() }
            funcList[XPathContainsFunc.NAME] = { XPathContainsFunc() }
            funcList[XPathNotFunc.NAME] = { XPathNotFunc() }
            funcList[XPathSumFunc.NAME] = { XPathSumFunc() }
            funcList[XPathRegexFunc.NAME] = { XPathRegexFunc() }
            funcList[XPathAtanFunc.NAME] = { XPathAtanFunc() }
            funcList[XPathStartsWithFunc.NAME] = { XPathStartsWithFunc() }
            funcList[XPathTodayFunc.NAME] = { XPathTodayFunc() }
            funcList[XPathConcatFunc.NAME] = { XPathConcatFunc() }
            funcList[XPathSqrtFunc.NAME] = { XPathSqrtFunc() }
            funcList[XPathUuidFunc.NAME] = { XPathUuidFunc() }
            funcList[XPathIdCompressFunc.NAME] = { XPathIdCompressFunc() }
            funcList[XPathJoinChunkFunc.NAME] = { XPathJoinChunkFunc() }
            funcList[XPathChecksumFunc.NAME] = { XPathChecksumFunc() }
            funcList[XPathSortFunc.NAME] = { XPathSortFunc() }
            funcList[XPathSortByFunc.NAME] = { XPathSortByFunc() }
            funcList[XPathDistinctValuesFunc.NAME] = { XPathDistinctValuesFunc() }
            funcList[XPathSleepFunc.NAME] = { XPathSleepFunc() }
            funcList[XPathIndexOfFunc.NAME] = { XPathIndexOfFunc() }
            funcList[XPathEncryptStringFunc.NAME] = { XPathEncryptStringFunc() }
            funcList[XPathDecryptStringFunc.NAME] = { XPathDecryptStringFunc() }
            funcList[XPathJsonPropertyFunc.NAME] = { XPathJsonPropertyFunc() }
            funcList[XPathClosestPointOnPolygonFunc.NAME] = { XPathClosestPointOnPolygonFunc() }
            funcList[XPathIsPointInsidePolygonFunc.NAME] = { XPathIsPointInsidePolygonFunc() }
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

            val sb = StringBuilder()
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
                    mDoubleParseCache.register(attrValue, Double.NaN)
                    return attrValue
                }
                val ret = attrValue.toDouble()
                mDoubleParseCache.register(attrValue, ret)
                return ret
            } catch (ife: NumberFormatException) {
                //Not a double
                mDoubleParseCache.register(attrValue, Double.NaN)
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
                `val` = kotlin.math.abs(d) > 1.0e-12 && !d.isNaN()
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
                PlatformDateUtils.fractionalDaysSinceEpoch(o)
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
                `val` = if (o) 1.0 else 0.0
            } else if (o is Double) {
                `val` = o
            } else if (o is String) {
                val s = o.trim()
                if (checkForInvalidNumericOrDatestringCharacters(s)) {
                    return Double.NaN
                }
                try {
                    `val` = s.toDouble()
                } catch (nfe: NumberFormatException) {
                    try {
                        `val` = attemptDateConversion(s)
                    } catch (e: XPathTypeMismatchException) {
                        `val` = Double.NaN
                    }
                }
            } else if (o is PlatformDate) {
                `val` = PlatformDateUtils.daysSinceEpoch(o).toDouble()
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
                var dbl = l.toDouble()
                if (l == 0L && (`val` < 0.0 || `val` == -0.0)) {
                    dbl = -0.0
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
                if (d.isNaN()) {
                    `val` = "NaN"
                } else if (kotlin.math.abs(d) < 1.0e-12) {
                    `val` = "0"
                } else if (d.isInfinite()) {
                    `val` = (if (d < 0) "-" else "") + "Infinity"
                } else if (kotlin.math.abs(d - d.toInt()) < 1.0e-12) {
                    `val` = d.toInt().toString()
                } else {
                    `val` = d.toString()
                }
            } else if (o is String) {
                `val` = o
            } else if (o is PlatformDate) {
                `val` = PlatformDateUtils.formatDate(o, PlatformDateUtils.FORMAT_ISO8601)
            } else if (o is IExprDataType) {
                `val` = o.toString()
            }

            if (`val` != null) {
                return `val`
            } else {
                if (o == null) {
                    throw XPathTypeMismatchException("attempt to cast null value to string")
                } else {
                    throw XPathTypeMismatchException("converting object of type ${o::class} to string")
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

                return PlatformDateUtils.dateAdd(PlatformDateUtils.getDate(1970, 1, 1)!!, n.toInt())
            } else if (o is String) {
                if (o.length == 0) {
                    return o
                }

                val d = PlatformDateUtils.parseDateTime(o)
                if (d == null) {
                    throw XPathTypeMismatchException("converting string $o to date")
                } else {
                    return d
                }
            } else if (o is PlatformDate) {
                return PlatformDateUtils.roundDate(o)
            } else {
                val type = if (o == null) "null" else o::class.simpleName ?: ""
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
        fun getXPathFuncListMap(): HashMap<String, () -> XPathFuncExpr> {
            return funcList
        }
    }
}
