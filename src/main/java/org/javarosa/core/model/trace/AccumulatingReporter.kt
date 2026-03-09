package org.javarosa.core.model.trace

import java.util.Vector

/**
 * A simple accumulator which keeps track of expressions which are evaluated
 * upon request.
 *
 * Created by ctsims on 10/19/2016.
 */
class AccumulatingReporter : EvaluationTraceReporter {

    private val traces: Vector<EvaluationTrace> = Vector()

    override fun wereTracesReported(): Boolean {
        return traces.size > 0
    }

    override fun reportTrace(trace: EvaluationTrace?) {
        this.traces.add(trace)
    }

    override fun getCollectedTraces(): Vector<EvaluationTrace> {
        return traces
    }

    override fun reportAsFlat(): Boolean {
        return false
    }

    override fun reset() {
        this.traces.clear()
    }
}
