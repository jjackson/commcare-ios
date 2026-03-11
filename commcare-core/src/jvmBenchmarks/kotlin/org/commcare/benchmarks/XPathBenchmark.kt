package org.commcare.benchmarks

import org.commcare.test.utilities.MockApp
import org.commcare.test.utilities.MockSessionNavigationResponder
import org.commcare.session.SessionNavigator
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.form.api.FormEntryController
import org.javarosa.form.api.FormEntryModel
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.FunctionUtils
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
open class XPathBenchmark {

    private lateinit var model: FormEntryModel
    private lateinit var evalContext: EvaluationContext
    private lateinit var mainInstance: FormInstance

    @Setup(Level.Trial)
    fun setUp() {
        val mockApp = MockApp("/app_performance/")
        val responder = MockSessionNavigationResponder(mockApp.session)
        val navigator = SessionNavigator(responder)
        val session = mockApp.session

        navigator.startNextSessionStep()
        session.setCommand("m1")
        navigator.startNextSessionStep()
        session.setEntityDatum("case_id", "3b6bff05-b9c3-42d8-9b12-9b27a834d330")
        navigator.startNextSessionStep()
        session.setCommand("m1-f2")
        navigator.startNextSessionStep()
        session.setEntityDatum(
            "case_id_new_imci_visit_0",
            "593ef28a-34ff-421d-a29c-6a0fd975df95"
        )
        navigator.startNextSessionStep()

        val fec: FormEntryController = mockApp.loadAndInitForm("large_tdh_form.xml")
        model = fec.getModel()
        val formDef = model.getForm()
        evalContext = formDef.getEvaluationContext()!!
        mainInstance = formDef.getMainInstance()!!
    }

    @Benchmark
    fun parseSimpleReference(): Any {
        return XPathParseTool.parseXPath("/data/question1")!!
    }

    @Benchmark
    fun parseComplexExpression(): Any {
        return XPathParseTool.parseXPath(
            "if(count(instance('casedb')/casedb/case[@case_type='patient' and @status='open']) > 0, 'yes', 'no')"
        )!!
    }

    @Benchmark
    fun evalSimpleReference(): Any? {
        val expr = XPathParseTool.parseXPath("/data/patient_name")!!
        return FunctionUtils.unpack(expr.eval(mainInstance, evalContext))
    }

    @Benchmark
    fun evalArithmetic(): Any? {
        val expr = XPathParseTool.parseXPath("1 + 2 * 3 div 4")!!
        return FunctionUtils.unpack(expr.eval(mainInstance, evalContext))
    }

    @Benchmark
    fun evalStringFunction(): Any? {
        val expr = XPathParseTool.parseXPath("concat('hello', ' ', 'world')")!!
        return FunctionUtils.unpack(expr.eval(mainInstance, evalContext))
    }

    @Benchmark
    fun evalConditional(): Any? {
        val expr = XPathParseTool.parseXPath("if(true() and not(false()), 'show', 'hide')")!!
        return FunctionUtils.unpack(expr.eval(mainInstance, evalContext))
    }

    @Benchmark
    fun evalBatch20(): Int {
        val expressions = listOf(
            "true()",
            "false()",
            "1 + 1",
            "2 * 3",
            "concat('a', 'b')",
            "string-length('hello')",
            "not(false())",
            "if(true(), 1, 0)",
            "number('42')",
            "string(42)",
            "true() and true()",
            "true() or false()",
            "1 = 1",
            "1 != 2",
            "1 < 2",
            "2 > 1",
            "1 + 2 + 3",
            "concat('a', 'b', 'c')",
            "if(1 > 0, 'pos', 'neg')",
            "not(1 = 2)"
        )
        var count = 0
        for (exprStr in expressions) {
            val expr = XPathParseTool.parseXPath(exprStr)!!
            FunctionUtils.unpack(expr.eval(mainInstance, evalContext))
            count++
        }
        return count
    }
}
