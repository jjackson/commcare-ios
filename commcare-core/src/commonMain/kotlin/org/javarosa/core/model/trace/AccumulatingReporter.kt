package org.javarosa.core.model.trace


/**
 * A simple accumulator which keeps track of expressions which are evaluated
 * upon request.
 *
 * Created by ctsims on 10/19/2016.
 */
class AccumulatingReporter : EvaluationTraceReporter {

    private val traces: ArrayList<EvaluationTrace> = ArrayList()

    override fun wereTracesReported(): Boolean {
        return traces.size > 0
    }

    override fun reportTrace(trace: EvaluationTrace?) {
        this.traces.add(trace!!)
    }

    override fun getCollectedTraces(): ArrayList<EvaluationTrace> {
        return traces
    }

    override fun reportAsFlat(): Boolean {
        return false
    }

    override fun reset() {
        this.traces.clear()
    }
}
