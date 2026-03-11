package org.commcare.suite.model

import io.reactivex.Single
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.IFunctionHandler
import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.services.locale.Localization
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.analysis.AnalysisInvalidException
import org.javarosa.xpath.analysis.XPathAnalyzable
import org.javarosa.xpath.analysis.XPathAnalyzer
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.Calendar

import org.javarosa.core.model.utils.PlatformDate

/**
 * Text objects are a model for holding strings which
 * will be displayed to users. Text's can be defined
 * in a number of ways, static Strings, localized values,
 * even xpath expressions. They are dynamically evaluated
 * at runtime in order to allow for CommCare apps to flexibly
 * provide rich information from a number of sources.
 *
 * There are 4 types of Text sources which can be defined:
 * - Raw Text
 * - Localized String
 * - XPath Expression
 * - Compound Text
 *
 * @author ctsims
 */
class Text : Externalizable, DetailTemplate, XPathAnalyzable {
    private var type: Int = 0
    private var argument: String? = null

    // Will this maintain order? I don't think so....
    private var arguments: HashMap<String, Text>? = null

    private var cacheParse: XPathExpression? = null

    /**
     * For Serialization only;
     */
    constructor()

    /**
     * @return The evaluated string value for this Text object. Note
     * that if this string is expecting a model in order to evaluate
     * (like an XPath text), this will likely fail.
     */
    fun evaluate(): String {
        return evaluate(null) as String
    }

    /**
     * @param context A data model which is compatible with any
     *                xpath functions in the underlying Text
     * @return The evaluated string value for this Text object.
     */
    override fun evaluate(context: EvaluationContext?): String {
        when (type) {
            TEXT_TYPE_FLAT -> return argument!!
            TEXT_TYPE_LOCALE -> {
                var id = argument
                if (argument == "") {
                    id = arguments!!["id"]!!.evaluate(context)
                }

                val params = generateOrderedParameterListForLocalization(arguments, context)
                return Localization.get(id!!, params)
            }
            TEXT_TYPE_XPATH -> {
                try {
                    ensureCacheIsParsed()

                    // We need an EvaluationContext in a specific sense in order to evaluate certain components
                    // like Instance references or relative references to some models, but it's valid to use
                    // XPath expressions for other things like Dates, or simply manipulating other variables,
                    // so if we don't have one, we can make one that doesn't reference any data specifically
                    val temp: EvaluationContext = if (context == null) {
                        EvaluationContext(null)
                    } else {
                        EvaluationContext(context, context.contextRef)
                    }

                    temp.addFunctionHandler(object : IFunctionHandler {
                        override fun eval(args: Array<Any?>?, ec: EvaluationContext?): Any? {
                            val o = FunctionUtils.toDate(args!![0])
                            if (o !is PlatformDate) {
                                // return null, date is null.
                                return ""
                            }

                            val dateType = args[1] as String
                            var format = DateUtils.FORMAT_HUMAN_READABLE_SHORT
                            if (dateType == "short") {
                                format = DateUtils.FORMAT_HUMAN_READABLE_SHORT
                            } else if (dateType == "long") {
                                format = DateUtils.FORMAT_ISO8601
                            }
                            return DateUtils.formatDate(o, format)
                        }

                        override fun getName(): String = "format_date"

                        override fun getPrototypes(): ArrayList<Any> {
                            val format = ArrayList<Any>()
                            val prototypes = arrayOf<Class<*>>(
                                PlatformDate::class.java,
                                String::class.java
                            )
                            format.add(prototypes)
                            return format
                        }

                        override fun rawArgs(): Boolean = false
                    })

                    temp.addFunctionHandler(object : IFunctionHandler {
                        override fun eval(args: Array<Any?>?, ec: EvaluationContext?): Any? {
                            val c = Calendar.getInstance()
                            c.time = PlatformDate()
                            return c.get(Calendar.DAY_OF_WEEK).toString()
                        }

                        override fun getName(): String = "dow"

                        override fun getPrototypes(): ArrayList<Any> {
                            val format = ArrayList<Any>()
                            val prototypes = arrayOf<Class<*>>()
                            format.add(prototypes)
                            return format
                        }

                        override fun rawArgs(): Boolean = false
                    })

                    val en = arguments!!.keys.iterator()
                    while (en.hasNext()) {
                        val key = en.next()
                        val value = arguments!![key]!!.evaluate(context)
                        temp.setVariable(key, value)
                    }

                    return cacheParse!!.eval(temp.getMainInstance(), temp) as String
                } catch (e: XPathSyntaxException) {
                    e.printStackTrace()
                } catch (e: XPathException) {
                    e.source = argument
                    throw e
                }
                // For testing;
                return argument!!
            }
            TEXT_TYPE_COMPOSITE -> {
                var ret = ""
                for (i in 0 until arguments!!.size) {
                    val item = arguments!![i.toString()]
                    ret += item!!.evaluate(context) + ""
                }
                return ret
            }
            else -> return argument!!
        }
    }

    private fun generateOrderedParameterListForLocalization(
        arguments: HashMap<String, Text>?,
        context: EvaluationContext?
    ): Array<String> {
        if (arguments == null) {
            return arrayOf()
        }

        val keys = getOrderedKeys(arguments)

        if (keys.isEmpty()) {
            return arrayOf()
        }

        val parameters = Array(keys.size) { "" }
        for (i in keys.indices) {
            parameters[i] = arguments[keys[i]]!!.evaluate(context)
        }
        return parameters
    }

    private fun getOrderedKeys(arguments: HashMap<String, Text>): List<String> {
        val keys = ArrayList<String>()
        for (key in arguments.keys) {
            if (key == "id") {
                continue
            }
            keys.add(key)
        }

        // This code uses a hacky shortcut to need to prevent type coercing the keys into integers,
        // and just sorts them alphanumerically, which will fail if there are more than 10 keys.
        // This check should keep us honest should we ever need to fix that.
        if (keys.size > 10) {
            throw RuntimeException("Too many arguments - Text params only support 10")
        }

        keys.sortWith { s1, s2 -> s1.compareTo(s2) }
        return keys
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        type = SerializationHelpers.readInt(`in`)
        argument = SerializationHelpers.readString(`in`)
        arguments = SerializationHelpers.readStringExtMap(`in`, pf) { Text() }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeNumeric(out, type.toLong())
        SerializationHelpers.writeString(out, argument!!)
        SerializationHelpers.writeMap(out, arguments ?: HashMap<String, Text>())
    }

    fun getArgument(): String? = argument

    @Throws(AnalysisInvalidException::class)
    override fun applyAndPropagateAnalyzer(analyzer: XPathAnalyzer) {
        if (analyzer.shortCircuit()) {
            return
        }
        if (this.type == TEXT_TYPE_XPATH) {
            try {
                ensureCacheIsParsed()
            } catch (e: XPathSyntaxException) {
                throw AnalysisInvalidException.INSTANCE_TEXT_PARSE_FAILURE
            }
            cacheParse!!.applyAndPropagateAnalyzer(analyzer)
        } else if (arguments != null) {
            for (t in arguments!!.values) {
                t.applyAndPropagateAnalyzer(analyzer)
            }
        }
    }

    @Throws(XPathSyntaxException::class)
    fun ensureCacheIsParsed() {
        if (cacheParse == null) {
            // Do an XPath cast to a string as part of the operation.
            cacheParse = XPathParseTool.parseXPath("string(" + argument + ")")
        }
    }

    /**
     * Get back a single disposable which can be executed to calculate the value of this Text.
     *
     * The query evaluation will be abandoned if disposed.
     */
    fun getDisposableSingleForEvaluation(ec: EvaluationContext?): Single<String> {
        val abandonableContext = ec!!.spawnWithCleanLifecycle()

        val toCancel = arrayOfNulls<Thread>(1)
        return Single.fromCallable {
            toCancel[0] = Thread.currentThread()
            evaluate(abandonableContext)
        }.doOnDispose {
            if (toCancel[0] != null) {
                toCancel[0]!!.interrupt()
                toCancel[0] = null
            }
        }
    }

    companion object {
        const val TEXT_TYPE_FLAT = 1
        const val TEXT_TYPE_LOCALE = 2
        const val TEXT_TYPE_XPATH = 4
        const val TEXT_TYPE_COMPOSITE = 8

        /**
         * @return An empty text object
         */
        private fun TextFactory(): Text {
            val t = Text()
            t.type = -1
            t.argument = ""
            t.arguments = HashMap()
            return t
        }

        /**
         * @param id The locale key.
         * @return A Text object that evaluates to the
         * localized value of the ID provided.
         */
        @JvmStatic
        fun LocaleText(id: String?): Text {
            return LocaleText(id, null)
        }

        /**
         * @param id The locale key.
         * @param arguments arguments to the localizer
         * @return A Text object that evaluates to the
         * localized value of the ID provided.
         */
        @JvmStatic
        fun LocaleText(id: String?, arguments: HashMap<String, Text>?): Text {
            val t = TextFactory()
            t.argument = id
            t.type = TEXT_TYPE_LOCALE
            t.arguments = arguments
            return t
        }

        /**
         * @param localeText A Text object which evaluates
         *                   to a locale key.
         * @return A Text object that evaluates to the
         * localized value of the id returned by evaluating
         * localeText
         */
        @JvmStatic
        fun LocaleText(localeText: Text): Text {
            val arguments = HashMap<String, Text>()
            arguments["id"] = localeText
            return LocaleText(arguments)
        }

        /**
         * @return A Text object that evaluates to the
         * localized value of the id returned by evaluating
         * localeText
         */
        @JvmStatic
        fun LocaleText(arguments: HashMap<String, Text>): Text {
            val t = TextFactory()

            // ensure there is an id text argument
            if (!arguments.containsKey("id")) {
                throw RuntimeException("Locale text constructor requires 'id' key in arguments")
            }

            t.arguments = arguments
            t.argument = ""
            t.type = TEXT_TYPE_LOCALE
            return t
        }

        /**
         * @param text A text string.
         * @return A Text object that evaluates to the
         * string provided.
         */
        @JvmStatic
        fun PlainText(text: String?): Text {
            val t = TextFactory()
            t.argument = text
            t.type = TEXT_TYPE_FLAT
            return t
        }

        /**
         * @param function  A valid XPath function.
         * @param arguments A key/value set defining arguments
         *                  which, when evaluated, will provide a value for variables
         *                  in the provided function.
         * @return A Text object that evaluates to the
         * resulting value of the xpath expression defined
         * by function when presented with a compatible data
         * model.
         * @throws XPathSyntaxException If the provided xpath function does
         *                              not have valid syntax.
         */
        @JvmStatic
        @Throws(XPathSyntaxException::class)
        fun XPathText(function: String?, arguments: HashMap<String, Text>?): Text {
            val t = TextFactory()
            t.argument = function
            // Test parse real fast to make sure it's valid text.
            val expression = XPathParseTool.parseXPath("string(" + t.argument + ")")
            t.arguments = arguments
            t.type = TEXT_TYPE_XPATH
            return t
        }

        /**
         * @param text A vector of Text objects.
         * @return A Text object that evaluates to the
         * value of each member of the text vector.
         */
        @JvmStatic
        fun CompositeText(text: ArrayList<Text>): Text {
            val t = TextFactory()
            var i = 0
            for (txt in text) {
                // TODO: Probably a more efficient way to do this...
                t.arguments!![Integer.toHexString(i)] = txt
                i++
            }
            t.type = TEXT_TYPE_COMPOSITE
            return t
        }
    }
}
