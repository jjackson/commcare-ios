package org.commcare.cases.query

import org.javarosa.model.xform.XPathReference
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.XPathPathExpr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Created by ctsims on 2/6/2017.
 */
class ModelSetTests {

    @Test
    @Throws(Exception::class)
    fun testCaseParentMatch() {
        val caseRootRef =
            (XPathParseTool.parseXPath("instance('casedb')/casedb/case") as XPathPathExpr).getReference()

        val querySetOptimizedLookup =
            (XPathParseTool.parseXPath("instance('casedb')/casedb/case[@case_id = current()/index/host]/value") as XPathPathExpr).getReference()

        assertTrue("Parent Reference isn't identified", caseRootRef.isParentOf(querySetOptimizedLookup, false))
    }

    @Test
    @Throws(Exception::class)
    fun testCaseIndexMatch() {
        val root = XPathReference.getPathExpr("instance('casedb')/casedb/case").getReference()

        val member = XPathReference.getPathExpr("instance('casedb')/casedb/case").getReference()
        member.setMultiplicity(member.size() - 1, 10)

        assertEquals("Contextualized reference", member.genericizeAfter(member.size() - 1), root)
    }
}
