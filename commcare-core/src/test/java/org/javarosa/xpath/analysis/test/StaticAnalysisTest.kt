package org.javarosa.xpath.analysis.test

import org.javarosa.core.model.instance.TreeReference
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.analysis.AnalysisInvalidException
import org.javarosa.xpath.analysis.ContainsUncacheableExpressionAnalyzer
import org.javarosa.xpath.analysis.InstanceNameAccumulatingAnalyzer
import org.javarosa.xpath.analysis.ReferencesMainInstanceAnalyzer
import org.javarosa.xpath.analysis.TopLevelContextTypesAnalyzer
import org.javarosa.xpath.expr.XPathPathExpr
import org.javarosa.xpath.parser.XPathSyntaxException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests for the XPath static analysis infrastructure
 *
 * @author Aliza Stone
 */
class StaticAnalysisTest {

    companion object {
        private val NO_INSTANCES_EXPR =
            "double(now()) > (double(/data/last_viewed) + 10)"
        private val ONE_INSTANCE_EXPR =
            "instance('casedb')/casedb/case[@case_type='case'][@status='open']"
        private val DUPLICATED_INSTANCE_EXPR =
            "count(instance('commcaresession')/session/user/data/role) > 0 and " +
                "instance('commcaresession')/session/user/data/role= 'case_manager'"
        private val EXPR_WITH_INSTANCE_IN_PREDICATE =
            "instance('casedb')/casedb/case[@case_type='commcare-user']" +
                "[hq_user_id=instance('commcaresession')/session/context/userid]/@case_id"
        private val RIDICULOUS_RELEVANCY_CONDITION_FROM_REAL_APP =
            "(instance('casedb')/casedb/case[@case_id=instance('commcaresession')/session/data/case_id_load_ccs_record0]/current_schedule_phase = 2 " +
                "and instance('casedb')/casedb/case[@case_id=instance('commcaresession')/session/data/case_id_load_ccs_record0]/add != '' and " +
                "(today() >= (date(instance('casedb')/casedb/case[@case_id=instance('commcaresession')/session/data/case_id_load_ccs_record0]/add) " +
                "+ int(instance('schedule:m5:p2:f2')/schedule/@starts)) and (instance('schedule:m5:p2:f2')/schedule/@expires = '' " +
                "or today() >= (date(instance('casedb')/casedb/case[@case_id=instance('commcaresession')/session/data/case_id_load_ccs_record0]/add) + " +
                "int(instance('schedule:m5:p2:f2')/schedule/@expires))))) and " +
                "(instance('schedule:m5:p2:f2')/schedule/@allow_unscheduled = 'True' or " +
                "count(instance('schedule:m5:p2:f2')/schedule/visit[instance('casedb')/casedb/case[@case_id=instance('commcaresession')/session/data/case_id_load_ccs_record0]/last_visit_number_cf = '' " +
                "or if(@repeats = 'True', @id >= instance('casedb')/casedb/case[@case_id=instance('commcaresession')/session/data/case_id_load_ccs_record0]/last_visit_number_cf, " +
                "@id > instance('casedb')/casedb/case[@case_id=instance('commcaresession')/session/data/case_id_load_ccs_record0]/last_visit_number_cf)]" +
                "[if(@repeats = 'True', today() >= (date(instance('casedb')/casedb/case[@case_id=instance('commcaresession')/session/data/case_id_load_ccs_record0]/last_visit_date_cf) + " +
                "int(@increment) + int(@starts)) and (@expires = '' or today() <= (date(instance('casedb')/casedb/case[@case_id=instance('commcaresession')/session/data/case_id_load_ccs_record0]/last_visit_date_cf) + " +
                "int(@increment) + int(@expires))), today() >= (date(instance('casedb')/casedb/case[@case_id=instance('commcaresession')/session/data/case_id_load_ccs_record0]/add) + " +
                "int(@due) + int(@starts)) and (@expires = '' or today() <= (date(instance('casedb')/casedb/case[@case_id=instance('commcaresession')/session/data/case_id_load_ccs_record0]/add) + " +
                "int(@due) + int(@expires))))]) > 0)"

        private val BASE_CONTEXT_REF_aCase = "instance('casedb')/casedb/case[651]"
        private val BASE_CONTEXT_REF_aNode = "instance('baseinstance')/base/element"

        private val BASIC_RELATIVE_EXPR = "./@case_name"
        private val EXPR_WITH_CURRENT_AT_TOP_LEVEL =
            "(instance('adherence:calendar')/calendar/year/month/day[@date > (today()-36) and " +
                "@date < (today()-28) and @name='Sunday']/@date) = current()/date_registered"
        private val EXPR_WITH_CURRENT_IN_PREDICATE =
            "if(instance('casedb')/casedb/case[@case_id=current()/index/parent]/date_hh_registration = '', '', " +
                "format_date(date(instance('casedb')/casedb/case[@case_id=current()/index/parent]/date_hh_registration),'short'))"
        private val RELATIVE_EXPR_WITH_PREDICATE =
            "../element[@id=instance('commcaresession')/session/data/case_id_loaded]"
    }

    @Test
    @Throws(XPathSyntaxException::class)
    fun testInstanceAccumulatingAnalyzer() {
        testInstanceAccumulate(NO_INSTANCES_EXPR, emptyArray())
        testInstanceAccumulate(ONE_INSTANCE_EXPR, arrayOf("casedb"))
        testInstanceAccumulate(DUPLICATED_INSTANCE_EXPR, arrayOf("commcaresession"))
        testInstanceAccumulate(EXPR_WITH_INSTANCE_IN_PREDICATE, arrayOf("casedb", "commcaresession"))
        testInstanceAccumulate(
            RIDICULOUS_RELEVANCY_CONDITION_FROM_REAL_APP,
            arrayOf("casedb", "commcaresession", "schedule:m5:p2:f2")
        )

        // Test the length of the result with list accumulation, just to ensure it gets them all
        val parsedInstancesList = InstanceNameAccumulatingAnalyzer().accumulateAsList(
            XPathParseTool.parseXPath(RIDICULOUS_RELEVANCY_CONDITION_FROM_REAL_APP)!!
        )
        assertEquals(27, parsedInstancesList!!.size)
    }

    @Test
    @Throws(XPathSyntaxException::class)
    fun testCurrentAndRelativeRefs() {
        testInstanceAccumulate(BASIC_RELATIVE_EXPR, arrayOf("casedb"), BASE_CONTEXT_REF_aCase)
        testInstanceAccumulate(
            EXPR_WITH_CURRENT_AT_TOP_LEVEL,
            arrayOf("adherence:calendar", "casedb"),
            BASE_CONTEXT_REF_aCase
        )

        // expect null because no context ref was provided when it was needed
        testInstanceAccumulate(BASIC_RELATIVE_EXPR, null)
        testInstanceAccumulate(EXPR_WITH_CURRENT_AT_TOP_LEVEL, null)

        // should be OK not to provide a base context ref here because current() is only being
        // used within a predicate, so it should use the sub-context
        testInstanceAccumulate(EXPR_WITH_CURRENT_IN_PREDICATE, arrayOf("casedb"))

        // This analysis should fail because no context ref was provided
        testInstanceAccumulate(RELATIVE_EXPR_WITH_PREDICATE, null)

        testInstanceAccumulate(
            RELATIVE_EXPR_WITH_PREDICATE,
            arrayOf("commcaresession", "baseinstance"),
            BASE_CONTEXT_REF_aNode
        )
    }

    private fun testInstanceAccumulate(
        expressionString: String,
        expectedInstances: Array<String>?,
        baseContextString: String? = null
    ) {
        val analyzer = if (baseContextString != null) {
            val baseContextRef =
                (XPathParseTool.parseXPath(baseContextString) as XPathPathExpr).getReference()
            InstanceNameAccumulatingAnalyzer(baseContextRef)
        } else {
            InstanceNameAccumulatingAnalyzer()
        }

        val expectedInstancesSet = expectedInstances?.toHashSet()

        val parsedInstancesSet = analyzer.accumulate(XPathParseTool.parseXPath(expressionString)!!)
        assertEquals(expectedInstancesSet, parsedInstancesSet)
    }

    @Test
    @Throws(XPathSyntaxException::class)
    fun testReferencesMainInstanceAnalysis() {
        testReferencesMainInstance("/unicorn/color[@name='fred']", true)
        testReferencesMainInstance("date(/data/refill/next_refill_due_date)", true)

        val longExpressionWithMainInstanceRef =
            "instance('adherence_schedules')/adherence_schedules_list/adherence_schedules[" +
                "id = /data/schedule_id][/data/user/user_level = 'dev' or user_level = 'real']/doses_per_week"
        testReferencesMainInstance(longExpressionWithMainInstanceRef, true)

        val evenLongerExpressionWithMainInstanceRef =
            "date(coalesce(instance('casedb')/casedb/case[@case_id = instance('commcaresession')" +
                "/session/blah/case_id_load_episode_case]/refill_next_date, " +
                "(date(coalesce(instance('casedb')/casedb/case[@case_id = " +
                "instance('commcaresession')/session/blah/case_id_load_episode_case]/adherence_schedule_date_start, " +
                "/data/treatment_initiation_date)) + 30)))"
        testReferencesMainInstance(evenLongerExpressionWithMainInstanceRef, true)

        testReferencesMainInstance("instance('commcaresession')/session/data/case_id_load_test", false)
        testReferencesMainInstance("date('1996-02-29')", false)
        testReferencesMainInstance("selected('apple baby crimson', '  baby  ')", false)
    }

    private fun testReferencesMainInstance(expressionString: String, expectedResult: Boolean) {
        val analyzer = ReferencesMainInstanceAnalyzer()
        try {
            assertEquals(expectedResult, analyzer.computeResult(XPathParseTool.parseXPath(expressionString)!!))
        } catch (e: AnalysisInvalidException) {
            fail("Encountered Analysis Invalid exception: ${e.message}")
        }
    }

    @Test
    @Throws(XPathSyntaxException::class)
    fun testContainsUncacheableExpressionAnalysis() {
        testContainsUncacheable("now()", true)
        testContainsUncacheable("uuid()", true)
        testContainsUncacheable("random()", true)
        testContainsUncacheable("depend(/data/val1, /data/val2)", true)
        testContainsUncacheable("sleep(1000, -1)", true)
        testContainsUncacheable("date(/data/refill/next_refill_due_date) <= today()", true)
        testContainsUncacheable(
            "concat(format-date(today(), '%e/%n/%y'), ': ', /data/ql_weight_and_height/weight, ' ', jr:itext('localization/kg-label'))",
            true
        )
        testContainsUncacheable("/data/val1", false)
    }

    private fun testContainsUncacheable(expressionString: String, expectedResult: Boolean) {
        val analyzer = ContainsUncacheableExpressionAnalyzer()
        try {
            assertEquals(expectedResult, analyzer.computeResult(XPathParseTool.parseXPath(expressionString)!!))
        } catch (e: AnalysisInvalidException) {
            fail("Encountered Analysis Invalid exception: ${e.message}")
        }
    }

    @Test
    @Throws(XPathSyntaxException::class)
    fun testContextTypesAnalyzer() {
        testContextTypesAccumulate("true()", intArrayOf())
        testContextTypesAccumulate("/data/q1", intArrayOf(TreeReference.CONTEXT_ABSOLUTE))
        testContextTypesAccumulate(
            "true() and ./case_name = 'Aliza'",
            intArrayOf(TreeReference.CONTEXT_INHERITED)
        )
        testContextTypesAccumulate(
            "instance('commcaresession')/session/data/case_id_load_test",
            intArrayOf(TreeReference.CONTEXT_INSTANCE)
        )
        testContextTypesAccumulate(
            "instance('casedb')/casedb/case[@case_type='case'][@status='open']",
            intArrayOf(TreeReference.CONTEXT_INSTANCE)
        )
        testContextTypesAccumulate(
            "@case_type='case'",
            intArrayOf(TreeReference.CONTEXT_INHERITED)
        )
        testContextTypesAccumulate(
            "@case_id=current()/index/parent",
            intArrayOf(TreeReference.CONTEXT_INHERITED, TreeReference.CONTEXT_ORIGINAL)
        )
    }

    private fun testContextTypesAccumulate(expressionString: String, expectedTypes: IntArray) {
        val analyzer = TopLevelContextTypesAnalyzer()
        val expectedTypesSet = expectedTypes.toHashSet()
        val parsedTypesSet = analyzer.accumulate(XPathParseTool.parseXPath(expressionString)!!)
        assertEquals(expectedTypesSet, parsedTypesSet)
    }
}
