package org.commcare.benchmarks

import org.commcare.modern.session.SessionWrapper
import org.commcare.session.SessionNavigator
import org.commcare.test.utilities.MockApp
import org.commcare.test.utilities.MockSessionNavigationResponder
import org.javarosa.core.test.FormParseInit
import org.javarosa.form.api.FormEntryController
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
open class FormLoadingBenchmark {

    /**
     * Full form load with session navigation — the real-world path.
     * Uses the 328KB large TDH form with ~1,859 fields.
     */
    @Benchmark
    fun loadLargeFormWithSession(): Int {
        val mockApp = MockApp("/app_performance/")
        val responder = MockSessionNavigationResponder(mockApp.session)
        val navigator = SessionNavigator(responder)
        val session: SessionWrapper = mockApp.session

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
        return fec.getModel().getEvent()
    }

    /**
     * Just XForm XML parsing — isolates parser overhead from model initialization.
     */
    @Benchmark
    fun parseLargeFormXml(): Int {
        val fpi = FormParseInit("/app_performance/large_tdh_form.xml")
        return fpi.formDef.getChildren().size
    }
}
