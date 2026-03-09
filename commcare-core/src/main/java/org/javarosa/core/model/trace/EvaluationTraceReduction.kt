package org.javarosa.core.model.trace

import org.javarosa.core.util.OrderedHashtable
import java.util.ConcurrentModificationException
import java.util.HashMap
import java.util.Vector

/**
 * A Trace Reduction represents a "folded-in" model of an evaluation trace
 * which aggregates stats about multiple traces which followed the same structure
 *
 * Created by ctsims on 1/24/2017.
 */
class EvaluationTraceReduction(trace: EvaluationTrace) : EvaluationTrace(trace.getExpression() ?: "") {

    private val expression: String = trace.getExpression() ?: ""

    private var countExecuted: Int = 0
    private var countRetrievedFromCache: Int = 0
    private var nanoTime: Long = 0

    // Maps how many times a given value was computed as the result for the expression that this
    // trace represents
    private val valueMap: HashMap<String?, Int> = HashMap()

    // Trace reductions for all of the subtraces of this expression's trace
    private val subTraces: OrderedHashtable<String?, EvaluationTraceReduction> = OrderedHashtable()

    init {
        foldIn(trace)
    }

    /**
     * Add the stats about the provided trace to this reduced trace.
     *
     * Assumes that the provided trace represents the same evaluated expression as this trace.
     */
    fun foldIn(trace: EvaluationTrace) {
        countExecuted++
        if (trace.evaluationUsedExpressionCache()) {
            countRetrievedFromCache++
        }
        nanoTime += trace.getRuntimeInNanoseconds()
        var valueCount = 1
        if (valueMap.containsKey(trace.getValue())) {
            valueCount = valueMap[trace.getValue()]!! + 1
        }
        valueMap[trace.getValue()] = valueCount
        val subTraceVector = trace.getSubTraces()
        @Suppress("UNCHECKED_CAST")
        val copy = subTraceVector.clone() as Vector<EvaluationTrace>
        synchronized(subTraceVector) {
            try {
                for (subTrace in copy) {
                    val subKey = subTrace.getExpression()
                    if (subTraces.containsKey(subKey)) {
                        val reducedSubExpr = subTraces[subTrace.getExpression()]
                        reducedSubExpr!!.foldIn(subTrace)
                    } else {
                        val reducedSubExpr = EvaluationTraceReduction(subTrace)
                        subTraces[subKey] = reducedSubExpr
                    }
                }
            } catch (cme: ConcurrentModificationException) {
                throw RuntimeException(cme)
            }
        }
    }

    override fun getSubTraces(): Vector<EvaluationTrace> {
        return Vector<EvaluationTrace>(subTraces.values)
    }

    override fun getExpression(): String {
        return expression
    }

    /**
     * @return The outcome of the expression's execution.
     */
    override fun getValue(): String {
        return countExecuted.toString()
    }

    override fun getRuntimeInNanoseconds(): Long {
        return nanoTime
    }

    override fun getProfileReport(): String {
        var response = "{\n"
        response += "    time: " + getRuntimeCount(getRuntimeInNanoseconds()) + "\n"
        response += "    time/call: " + getRuntimeCount(getRuntimeInNanoseconds() / countExecuted.toLong()) + "\n"
        var valueResponseCount = 0
        val totalRecords = valueMap.size
        for (key in valueMap.keys) {
            response += "    $key: ${valueMap[key]}\n"
            valueResponseCount++
            if (valueResponseCount >= 10) {
                response += String.format("    ... %s more ...", totalRecords - valueResponseCount)
                break
            }
        }
        response += "}"
        return response
    }

    override fun evaluationUsedExpressionCache(): Boolean {
        return countRetrievedFromCache > 0
    }

    override fun getCacheReport(): String {
        return "{ num times retrieved from cache: $countRetrievedFromCache }"
    }

    fun getRuntimeCount(l: Long): String {
        return if (l / 1000 / 1000 > 0) {
            "${l / 1000 / 1000}ms"
        } else if (l / 1000 > 0) {
            "${l / 1000}us"
        } else {
            "${l}ns"
        }
    }
}
