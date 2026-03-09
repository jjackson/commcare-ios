package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.DataUtil
import org.javarosa.xpath.XPathArityException
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.parser.XPathSyntaxException
import java.util.Collections

/**
 * Created by amstone326 on 6/28/17.
 */
open class XPathSortFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    @Throws(XPathSyntaxException::class)
    override fun validateArgCount() {
        if (args.size < 1 || args.size > 2) {
            throw XPathArityException(name, "1 or 2 arguments", args.size)
        }
    }

    override fun evalBody(model: DataInstance<*>, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        val sortedList: List<String>
        if (evaluatedArgs.size == 1) {
            sortedList = sortSingleList(FunctionUtils.toString(evaluatedArgs[0]), true)
        } else {
            sortedList = sortSingleList(
                FunctionUtils.toString(evaluatedArgs[0]),
                FunctionUtils.toBoolean(evaluatedArgs[1])
            )
        }
        if (sortedList.isEmpty()) {
            throw XPathException(String.format("Called sort() on empty list with args %s", evaluatedArgs))
        }
        return DataUtil.listToString(sortedList)
    }

    companion object {
        const val NAME: String = "sort"

        // since we accept 1-2 arguments
        private const val EXPECTED_ARG_COUNT: Int = -1

        @JvmStatic
        fun sortSingleList(spaceSeparatedString: String, ascending: Boolean): List<String> {
            val items = DataUtil.stringToList(spaceSeparatedString)
            sortSingleList(items, ascending)
            return items
        }

        @JvmStatic
        fun sortSingleList(items: MutableList<String>, ascending: Boolean) {
            Collections.sort(items) { s1, s2 -> (if (ascending) 1 else -1) * s1.compareTo(s2) }
        }
    }
}
