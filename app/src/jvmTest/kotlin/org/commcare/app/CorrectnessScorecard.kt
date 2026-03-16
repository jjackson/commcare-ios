package org.commcare.app

import org.junit.Test

/**
 * Correctness Scorecard — aggregates test counts across all test layers.
 *
 * Run with: ./gradlew :app:jvmTest --tests "*.CorrectnessScorecard"
 *
 * Produces a 4-layer summary:
 *   Unit Tests (storage, viewmodel, engine)
 *   Oracle Tests (golden file comparisons)
 *   Cross-Platform Tests (commonTest — counted separately)
 *   E2E Tests (full journey, integration)
 */
class CorrectnessScorecard {

    // Categorize test classes by layer
    private val unitTestClasses = listOf(
        "org.commcare.app.storage.SqlDelightUserSandboxTest",
        "org.commcare.app.viewmodel.AsyncViewModelTest",
        "org.commcare.app.viewmodel.CaseTileViewModelTest",
        "org.commcare.app.engine.FormLoadingTest"
    )

    private val oracleTestClasses = listOf(
        "org.commcare.app.oracle.AllQuestionTypesOracleTest",
        "org.commcare.app.oracle.FormStructureOracleTest",
        "org.commcare.app.oracle.ComprehensiveOracleTest",
        "org.commcare.app.oracle.OracleComparisonTest",
        "org.commcare.app.oracle.GoldenFileGenerator",
        "org.commcare.app.oracle.GoldenFileStalenessTest",
        "org.commcare.app.oracle.AdvancedNavigationOracleTest",
        "org.commcare.app.oracle.CaseSearchOracleTest",
        "org.commcare.app.oracle.LocalizationSupportOracleTest",
        "org.commcare.app.oracle.PlatformFeatureOracleTest",
        "org.commcare.app.oracle.ReportingOracleTest",
        "org.commcare.app.oracle.SessionStackOracleTest",
        "org.commcare.app.oracle.AppLifecycleOracleTest"
    )

    private val e2eTestClasses = listOf(
        "org.commcare.app.e2e.FullJourneyTest",
        "org.commcare.app.e2e.ViewModelIntegrationTest"
    )

    private fun countTestMethods(className: String): Int {
        return try {
            val clazz = Class.forName(className)
            clazz.methods.count { method ->
                method.annotations.any { annotation ->
                    annotation.annotationClass.qualifiedName == "org.junit.Test" ||
                    annotation.annotationClass.qualifiedName == "kotlin.test.Test"
                }
            }
        } catch (e: Exception) {
            println("  WARNING: Could not load $className: ${e.message}")
            0
        }
    }

    @Test
    fun printCorrectnessScorecard() {
        println("\n" + "=".repeat(60))
        println("  COMMCARE iOS — CORRECTNESS SCORECARD")
        println("=".repeat(60))

        // Layer 1: Unit Tests
        var unitTotal = 0
        println("\nLayer 1: Unit Tests")
        for (cls in unitTestClasses) {
            val count = countTestMethods(cls)
            val shortName = cls.substringAfterLast('.')
            println("  $shortName: $count tests")
            unitTotal += count
        }

        // Layer 2: Oracle Tests
        var oracleTotal = 0
        println("\nLayer 2: Oracle Tests")
        for (cls in oracleTestClasses) {
            val count = countTestMethods(cls)
            val shortName = cls.substringAfterLast('.')
            println("  $shortName: $count tests")
            oracleTotal += count
        }

        // Layer 3: Cross-Platform Tests (counted from commcare-core commonTest, not loadable here)
        // Report known count from project docs
        val crossPlatformTotal = 100 // documented as 100+ cross-platform tests
        println("\nLayer 3: Cross-Platform Tests")
        println("  (commcare-core commonTest — run via iosSimulatorArm64Test)")
        println("  Documented: $crossPlatformTotal+ tests")

        // Layer 4: E2E Tests
        var e2eTotal = 0
        println("\nLayer 4: E2E / Integration Tests")
        for (cls in e2eTestClasses) {
            val count = countTestMethods(cls)
            val shortName = cls.substringAfterLast('.')
            println("  $shortName: $count tests")
            e2eTotal += count
        }

        // Summary
        val appTotal = unitTotal + oracleTotal + e2eTotal
        println("\n" + "-".repeat(60))
        println("  SUMMARY")
        println("-".repeat(60))
        println("  Unit Tests:           $unitTotal")
        println("  Oracle Tests:         $oracleTotal")
        println("  Cross-Platform Tests: ${crossPlatformTotal}+ (separate runner)")
        println("  E2E Tests:            $e2eTotal")
        println("  App-Level Total:      $appTotal")
        println("  Overall (with XP):    ${appTotal + crossPlatformTotal}+")
        println("=".repeat(60))
        println()

        // Assert minimums to catch regressions
        assert(unitTotal > 0) { "Expected at least 1 unit test" }
        assert(oracleTotal > 0) { "Expected at least 1 oracle test" }
        assert(e2eTotal > 0) { "Expected at least 1 E2E test" }
        assert(appTotal >= 50) { "Expected at least 50 total app tests, got $appTotal" }
    }
}
