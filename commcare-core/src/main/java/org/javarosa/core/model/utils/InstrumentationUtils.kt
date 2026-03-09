package org.javarosa.core.model.utils

import org.javarosa.core.model.trace.EvaluationTrace
import org.javarosa.core.model.trace.EvaluationTraceReporter
import org.javarosa.core.model.trace.TraceSerialization

/**
 * Utility functions for instrumentation in the engine
 *
 * Created by ctsims on 7/6/2017.
 */
object InstrumentationUtils {

    @JvmStatic
    fun printAndClearTraces(reporter: EvaluationTraceReporter?, description: String) {
        printAndClearTraces(reporter, description, TraceSerialization.TraceInfoType.FULL_PROFILE)
    }

    /**
     * Prints out traces (if any exist) from the provided reporter with a description into sysout
     */
    @JvmStatic
    fun printAndClearTraces(
        reporter: EvaluationTraceReporter?,
        description: String,
        requestedInfo: TraceSerialization.TraceInfoType
    ) {
        if (reporter != null) {
            if (reporter.wereTracesReported()) {
                println(description)
            }

            val traces: List<EvaluationTrace> = reporter.getCollectedTraces().toList()
            for (trace in traces) {
                println("${trace.getExpression()}: ${trace.getValue()}")
                print(TraceSerialization.serializeEvaluationTrace(trace, requestedInfo, reporter.reportAsFlat()))
            }

            reporter.reset()
        }
    }

    /**
     * Prints out traces (if any exist) from the provided reporter with a description into sysout
     */
    @JvmStatic
    fun collectAndClearTraces(
        reporter: EvaluationTraceReporter?,
        description: String,
        requestedInfo: TraceSerialization.TraceInfoType
    ): String {
        var returnValue = ""
        if (reporter != null) {
            if (reporter.wereTracesReported()) {
                returnValue += description + "\n"
            }

            val traces: List<EvaluationTrace> = reporter.getCollectedTraces().toList()
            for (trace in traces) {
                returnValue += "${trace.getExpression()}: ${trace.getValue()}\n"
                returnValue += TraceSerialization.serializeEvaluationTrace(
                    trace, requestedInfo, reporter.reportAsFlat()
                )
            }

            reporter.reset()
        }
        return returnValue
    }

    @JvmStatic
    fun printExpressionsThatUsedCaching(reporter: EvaluationTraceReporter?, description: String) {
        if (reporter != null) {
            if (reporter.wereTracesReported()) {
                println(description)
            }

            val traces: List<EvaluationTrace> = reporter.getCollectedTraces().toList()
            for (trace in traces) {
                if (trace.evaluationUsedExpressionCache()) {
                    println("${trace.getExpression()}: ${trace.getValue()}")
                    println("    ${trace.getCacheReport()}")
                }
            }
        }
    }

    @JvmStatic
    fun printCachedAndNotCachedExpressions(reporter: EvaluationTraceReporter?, description: String) {
        if (reporter != null) {
            if (reporter.wereTracesReported()) {
                println(description)
            }

            val withCaching = mutableListOf<EvaluationTrace>()
            val withoutCaching = mutableListOf<EvaluationTrace>()
            val traces: List<EvaluationTrace> = reporter.getCollectedTraces().toList()
            for (trace in traces) {
                if (trace.evaluationUsedExpressionCache()) {
                    withCaching.add(trace)
                } else {
                    withoutCaching.add(trace)
                }
            }

            println("EXPRESSIONS NEVER CACHED: ${withoutCaching.size}")
            for (trace in withoutCaching) {
                println("${trace.getExpression()}: ${trace.getValue()}")
            }

            println("EXPRESSIONS CACHED: ${withCaching.size}")
            for (trace in withCaching) {
                println("${trace.getExpression()}: ${trace.getValue()}")
                println("    ${trace.getCacheReport()}")
            }

            reporter.reset()
        }
    }
}
