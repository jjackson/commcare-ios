package org.javarosa.core.model.trace

import org.javarosa.core.util.OrderedHashtable
import kotlin.jvm.JvmField

/**
 * A Cumulative trace reporter collects and "folds" traces which execute over multiple elements.
 * It is helpful for identifying how many times different expressions are evaluated, and aggregating
 * elements of each execution
 *
 * Created by ctsims on 1/27/2017.
 */
class ReducingTraceReporter(private val flat: Boolean) : EvaluationTraceReporter {

    @JvmField
    var traceMap: OrderedHashtable<String?, EvaluationTraceReduction> = OrderedHashtable()

    override fun wereTracesReported(): Boolean {
        return !traceMap.isEmpty()
    }

    override fun reportTrace(trace: EvaluationTrace?) {
        val key = trace?.getExpression() ?: return
        if (traceMap.containsKey(key)) {
            traceMap[trace.getExpression()]!!.foldIn(trace)
        } else {
            traceMap[key] = EvaluationTraceReduction(trace)
        }
    }

    override fun reset() {
        this.traceMap.clear()
    }

    override fun getCollectedTraces(): ArrayList<EvaluationTrace> {
        return ArrayList<EvaluationTrace>().also { it.addAll(traceMap.values) }
    }

    override fun reportAsFlat(): Boolean {
        return this.flat
    }
}
