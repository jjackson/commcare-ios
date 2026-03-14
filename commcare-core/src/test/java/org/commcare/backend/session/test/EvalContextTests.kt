package org.commcare.backend.session.test

import org.commcare.suite.model.Suite
import org.commcare.test.utilities.MockApp
import org.javarosa.xpath.analysis.InstanceNameAccumulatingAnalyzer
import org.javarosa.xpath.expr.FunctionUtils
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Created by amstone326 on 8/14/17.
 */
class EvalContextTests {

    lateinit var appWithMenuDisplayConditions: MockApp
    lateinit var suite: Suite

    @Before
    fun setup() {
        appWithMenuDisplayConditions = MockApp("/app_with_menu_display_conditions/")
        suite = appWithMenuDisplayConditions.getSession().getPlatform()!!.getInstalledSuites()[0]
    }

    @Test
    fun testRestrictedEvalContextGeneration1() {
        // Get the "restricted" eval context for m0
        val m0 = suite.getMenusWithId("m0")!![0]
        val relevancyCondition = m0.getMenuRelevance()!!
        val instancesNeededByRelevancyCondition =
            InstanceNameAccumulatingAnalyzer().accumulate(relevancyCondition)!!

        // Get the eval context for a command ID that has 3 instances in scope, but restrict it
        // to just those needed by the relevancy condition (only "casedb" in this case)
        val ec = appWithMenuDisplayConditions.getSession()
            .getRestrictedEvaluationContext(m0.getId()!!, instancesNeededByRelevancyCondition)

        // 1) Confirm that the eval context was restricted properly
        val instancesThatShouldBeIncluded = listOf("casedb")
        assertEquals(instancesThatShouldBeIncluded, ec.getInstanceIds())

        // 2) Confirm the display condition was evaluated properly with the restricted context
        assertTrue(FunctionUtils.toBoolean(relevancyCondition.eval(ec)))
    }

    @Test
    fun testRestrictedEvalContextGeneration2() {
        // Get the "restricted" eval context for m1
        val m1 = suite.getMenusWithId("m1")!![0]
        val relevancyCondition = m1.getMenuRelevance()!!
        val instancesNeededByRelevancyCondition =
            InstanceNameAccumulatingAnalyzer().accumulate(relevancyCondition)!!

        // Get the eval context for a command ID that has 3 instances in scope, but restrict it
        // to just those needed by the relevancy condition (only "commcaresession" in this case)
        val ec = appWithMenuDisplayConditions.getSession()
            .getRestrictedEvaluationContext(m1.getId()!!, instancesNeededByRelevancyCondition)

        // 1) Confirm that the eval context was restricted properly
        val instancesThatShouldBeIncluded = listOf("commcaresession")
        assertEquals(instancesThatShouldBeIncluded, ec.getInstanceIds())

        // 2) Confirm the display condition was evaluated properly with the restricted context
        assertFalse(FunctionUtils.toBoolean(relevancyCondition.eval(ec)))
    }
}
