package org.commcare.benchmarks

import org.commcare.test.utilities.MockApp
import org.commcare.test.utilities.CaseTestUtils
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.FunctionUtils
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
open class CaseQueryBenchmark {

    private lateinit var evalContext: EvaluationContext

    @Setup(Level.Trial)
    fun setUp() {
        val mockApp = MockApp("/app_performance/")
        val session = mockApp.session
        evalContext = session.evaluationContext
    }

    /**
     * Query all cases — baseline for case database traversal.
     */
    @Benchmark
    fun queryAllCases(): Any? {
        val expr = XPathParseTool.parseXPath(
            "count(instance('casedb')/casedb/case)"
        )
        return FunctionUtils.unpack(expr.eval(evalContext))
    }

    /**
     * Query cases with a simple type filter.
     */
    @Benchmark
    fun queryCasesByType(): Any? {
        val expr = XPathParseTool.parseXPath(
            "count(instance('casedb')/casedb/case[@case_type='case'])"
        )
        return FunctionUtils.unpack(expr.eval(evalContext))
    }

    /**
     * Query cases with compound filter (type + status).
     */
    @Benchmark
    fun queryCasesCompoundFilter(): Any? {
        val expr = XPathParseTool.parseXPath(
            "count(instance('casedb')/casedb/case[@case_type='case' and @status='open'])"
        )
        return FunctionUtils.unpack(expr.eval(evalContext))
    }

    /**
     * Query a specific case by ID.
     */
    @Benchmark
    fun queryCaseById(): Any? {
        val expr = XPathParseTool.parseXPath(
            "instance('casedb')/casedb/case[@case_id='3b6bff05-b9c3-42d8-9b12-9b27a834d330']/case_name"
        )
        return FunctionUtils.unpack(expr.eval(evalContext))
    }
}
