package org.commcare.benchmarks

import org.commcare.modern.session.SessionWrapper
import org.commcare.session.SessionNavigator
import org.commcare.test.utilities.MockApp
import org.commcare.test.utilities.MockSessionNavigationResponder
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
open class SessionNavigationBenchmark {

    /**
     * Full session navigation from app entry to form ready.
     * This is the end-to-end latency users experience when opening a form.
     */
    @Benchmark
    fun navigateToForm(): Int {
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

        return responder.lastResultCode
    }

    /**
     * Just app initialization — how long to set up MockApp (profile install + restore).
     * This is the "app launch" cost.
     */
    @Benchmark
    fun initializeApp(): Int {
        val mockApp = MockApp("/app_performance/")
        return mockApp.session.hashCode()
    }
}
