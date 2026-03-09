package org.javarosa.core.model.trace

/**
 * Serializes an evaluation trace into a raw string for command line or other
 * debugging purposes.
 *
 * @author ctsims
 */
object TraceSerialization {

    private const val ONE_INDENT = "    "

    enum class TraceInfoType {
        FULL_PROFILE,
        CACHE_INFO_ONLY
    }

    @JvmStatic
    fun serializeEvaluationTrace(
        input: EvaluationTrace?,
        requestedInfo: TraceInfoType?,
        serializeFlat: Boolean
    ): String {
        return dumpExprOutput(input!!, 1, requestedInfo!!, serializeFlat)
    }

    private fun dumpExprOutput(
        level: EvaluationTrace,
        refLevel: Int,
        requestedInfo: TraceInfoType,
        serializeFlat: Boolean
    ): String {
        var output = if (serializeFlat) {
            addDesiredData(level, requestedInfo, "", ONE_INDENT)
        } else {
            indentExprAndAddData(level, refLevel, requestedInfo)
        }

        if (!serializeFlat) {
            for (child in level.getSubTraces()) {
                output += dumpExprOutput(child, refLevel + 1, requestedInfo, false) + "\n"
            }
        }

        return output
    }

    private fun indentExprAndAddData(
        level: EvaluationTrace,
        indentLevel: Int,
        requestedInfo: TraceInfoType
    ): String {
        val expr = level.getExpression()
        val value = level.getValue()

        val sb = StringBuilder()
        for (i in 0 until indentLevel) {
            sb.append(ONE_INDENT)
        }
        val indent = sb.toString()

        return addDesiredData(level, requestedInfo, "$indent$expr: $value\n", indent)
    }

    private fun addDesiredData(
        level: EvaluationTrace,
        requestedInfo: TraceInfoType,
        coreString: String,
        indent: String
    ): String {
        var newResult = coreString
        when (requestedInfo) {
            TraceInfoType.FULL_PROFILE -> {
                val profile = level.getProfileReport()
                if (profile != null) {
                    for (profileLine in profile.split("\\n".toRegex())) {
                        newResult += indent + profileLine + "\n"
                    }
                }
            }
            TraceInfoType.CACHE_INFO_ONLY -> {
                newResult += indent + level.getCacheReport() + "\n"
            }
        }
        return newResult
    }
}
