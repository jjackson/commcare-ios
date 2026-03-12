package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xform.util.CalendarUtils
import org.javarosa.xpath.XPathArityException
import org.javarosa.xpath.XPathUnsupportedException
import org.javarosa.xpath.parser.XPathSyntaxException
import org.javarosa.core.model.utils.PlatformDate

open class XPathFormatDateForCalendarFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    @Throws(XPathSyntaxException::class)
    override fun validateArgCount() {
        if (args.size < 2 || args.size > 3) {
            throw XPathArityException(name, "2 or 3 arguments", args.size)
        }
    }

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        var formatString: String? = null
        if (evaluatedArgs.size > 2) {
            formatString = FunctionUtils.toString(evaluatedArgs[2])
        }
        return formatDateForCalendar(evaluatedArgs[0], evaluatedArgs[1], formatString)
    }

    companion object {
        const val NAME: String = "format-date-for-calendar"
        private const val EXPECTED_ARG_COUNT: Int = -1

        /**
         * Given a date and format, return that date as a string formatted for that calendar
         * Accepted calendars are Ethiopian and Nepali
         *
         * @param dateObject The Object (String, Date, or XPath) to be evaluated into a date
         * @param calendar     The calendar system to use (nepali or ethiopian)
         * @param format     An optional format string as used in format-date()
         */
        private fun formatDateForCalendar(dateObject: Any?, calendar: Any?, format: String?): String {
            val date: PlatformDate? = FunctionUtils.expandDateSafe(dateObject)
            if (date == null) {
                return ""
            }
            if ("ethiopian" == calendar) {
                return CalendarUtils.ConvertToEthiopian(date, format)
            } else if ("nepali" == calendar) {
                return CalendarUtils.convertToNepaliString(date, format)
            } else {
                throw XPathUnsupportedException("Unsupported calendar type: $calendar")
            }
        }
    }
}
