package org.commcare.benchmarks

import org.commcare.test.utilities.MockApp
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
open class SyncRestoreBenchmark {

    /**
     * Full app initialization including user restore parsing.
     * MockApp constructor parses the user_restore.xml and populates the sandbox.
     * This is the closest proxy to real sync/restore performance.
     */
    @Benchmark
    fun fullRestoreAndInit(): Int {
        val mockApp = MockApp("/app_performance/")
        val session = mockApp.session
        // Force evaluation of the session to ensure all data is loaded
        return session.evaluationContext.hashCode()
    }
}
