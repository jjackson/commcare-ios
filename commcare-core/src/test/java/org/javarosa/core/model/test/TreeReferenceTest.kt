package org.javarosa.core.model.test

import org.javarosa.core.model.instance.TreeReference
import org.javarosa.model.xform.XPathReference
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.XPathExpression
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TreeReferenceTest {

    private lateinit var root: TreeReference
    private lateinit var aRef: TreeReference
    private lateinit var bRef: TreeReference
    private lateinit var acRef: TreeReference
    private lateinit var ac2Ref: TreeReference
    private lateinit var acdRef: TreeReference
    private lateinit var aceRef: TreeReference
    private lateinit var bcRef: TreeReference
    private lateinit var dotcRef: TreeReference
    private lateinit var parentRef: TreeReference

    private lateinit var a: TreeReference
    private lateinit var aa: TreeReference
    private lateinit var aaa: TreeReference

    private lateinit var dotRef: TreeReference

    private lateinit var floatc: TreeReference
    private lateinit var floatc2: TreeReference
    private lateinit var floatbc: TreeReference
    private lateinit var backc: TreeReference
    private lateinit var back2c: TreeReference

    private lateinit var a2Ref: TreeReference
    private lateinit var a2extRef: TreeReference

    private lateinit var abcRef: TreeReference
    private lateinit var abRef: TreeReference

    private lateinit var acPredRef: TreeReference
    private lateinit var dfPredRef: TreeReference
    private lateinit var fPredRef: TreeReference
    private lateinit var fNoPredRef: TreeReference
    private lateinit var acPredMatchRef: TreeReference
    private lateinit var acPredNotRef: TreeReference
    private lateinit var apreds: ArrayList<XPathExpression>

    private lateinit var currentC: TreeReference

    @Before
    fun initStuff() {
        root = TreeReference.rootRef()
        aRef = root.extendRef("a", TreeReference.DEFAULT_MUTLIPLICITY)
        bRef = root.extendRef("b", TreeReference.DEFAULT_MUTLIPLICITY)
        acRef = aRef.extendRef("c", TreeReference.DEFAULT_MUTLIPLICITY)
        bcRef = bRef.extendRef("c", TreeReference.DEFAULT_MUTLIPLICITY)

        acdRef = acRef.extendRef("d", TreeReference.DEFAULT_MUTLIPLICITY)
        aceRef = acRef.extendRef("e", TreeReference.DEFAULT_MUTLIPLICITY)

        abcRef = XPathReference.getPathExpr("/a/b/c").getReference()
        ac2Ref = XPathReference.getPathExpr("/a/c").getReference()
        abRef = XPathReference.getPathExpr("/a/b").getReference()

        dotRef = TreeReference.selfRef()
        dotcRef = dotRef.extendRef("c", TreeReference.DEFAULT_MUTLIPLICITY)

        // setup /a[2]/a[3]/a[4] reference
        a = root.extendRef("a", 1)
        aa = a.extendRef("a", 2)
        aaa = aa.extendRef("a", 3)

        // some relative references
        floatc = XPathReference.getPathExpr("c").getReference()
        floatc2 = XPathReference.getPathExpr("./c").getReference()

        floatbc = XPathReference.getPathExpr("b/c").getReference()
        backc = XPathReference.getPathExpr("../c").getReference()
        back2c = XPathReference.getPathExpr("../../c").getReference()

        // represent ../
        parentRef = TreeReference.selfRef()
        parentRef.incrementRefLevel()

        a2Ref = root.extendRef("a", 2)
        a2extRef = TreeReference("external", TreeReference.REF_ABSOLUTE)
        a2extRef.add("a", TreeReference.INDEX_UNBOUND)

        acPredRef = acRef.clone()
        acPredMatchRef = acRef.clone()
        acPredNotRef = acRef.clone()

        var testPred: XPathExpression? = null
        var failPred: XPathExpression? = null
        var passPred: XPathExpression? = null
        try {
            testPred = XPathParseTool.parseXPath("../b = 'test'")
            failPred = XPathParseTool.parseXPath("../b = 'fail'")
            passPred = XPathParseTool.parseXPath("true() = true()")
        } catch (e: Exception) {
            fail("Bad tests! Rewrite xpath expressions for predicate tests")
        }

        apreds = ArrayList()
        val amatchpreds = ArrayList<XPathExpression>()
        val anotpreds = ArrayList<XPathExpression>()

        apreds.add(testPred!!)
        amatchpreds.add(testPred)
        anotpreds.add(failPred!!)

        acPredRef.addPredicate(0, apreds)
        acPredMatchRef.addPredicate(0, amatchpreds)
        acPredNotRef.addPredicate(0, anotpreds)

        // For mutation testing.
        val acPredRefClone = acPredRef.clone()

        // We know we have a predicate at the 0 position
        val acPredRefClonePredicates = acPredRefClone.getPredicate(0)

        // Update it to add a new predicate
        acPredRefClonePredicates!!.add(passPred!!)

        // Reset the predicates in our new object
        acPredRefClone.addPredicate(0, acPredRefClonePredicates)

        dfPredRef = XPathReference.getPathExpr("/d/f[2 = (3 + /data/two)]").getReference()
        fPredRef = XPathReference.getPathExpr("f[2 = (3 + /data/two)]").getReference()
        fNoPredRef = XPathReference.getPathExpr("f").getReference()

        currentC = XPathReference.getPathExpr("current()/c").getReference()
    }

    /**
     * Ensures that original references aren't mutated.
     */
    @Test
    fun testMutation() {
        assertTrue("/a/c[] predicate set illegally modified", acPredRef.getPredicate(0)!!.size == 1)
    }

    @Test
    fun testSubreferences() {
        assertTrue("(/a/c/d).subreference(0) should be: /a",
            aRef == acdRef.getSubReference(0))
        assertTrue("(/a/c/d).subreference(1) should be: /a/c",
            acRef == acdRef.getSubReference(1))
        try {
            parentRef.getSubReference(0)
            fail("(../).subreference(0) should throw an exception")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun testParentage() {
        assertTrue("/ is a parent of '/a'", root.isParentOf(aRef, true))
        assertTrue("/a is a parent of '/a/c'", aRef.isParentOf(acRef, true))
        assertTrue("/a is an improper parent of '/a'", aRef.isParentOf(aRef, false))
        assertTrue("a is a parent of 'a/c/d'", aRef.isParentOf(acdRef, true))
        assertFalse("/a is not parent of '/b/c'", aRef.isParentOf(bcRef, true))
        assertFalse("/a is not parent of './c'", aRef.isParentOf(dotcRef, true))
        assertTrue("/a[2]/a[3] is a parent of '/a[2]/a[3]/a[4]'", aa.isParentOf(aaa, true))
    }

    @Test
    fun testClones() {
        assertEquals("/a was unable to clone properly", aRef, aRef.clone())
        assertEquals("/a/c was unable to clone properly", acRef, acRef.clone())
        assertEquals(". was unable to clone properly", dotRef, dotRef.clone())
        assertEquals("./c was unable to clone properly", dotcRef, dotcRef.clone())
        assertEquals("/a[2]/a[3]/a[4] was unable to clone properly", aaa, aaa.clone())
        assertEquals("/a[..b = 'test'] was unable to clone properly", acPredRef, acPredRef.clone())
    }

    @Test
    fun testIntersection() {
        assertEquals("intersect(/a, /a) should result in /a", aRef, aRef.intersect(aRef))
        assertEquals("intersect(/a/c, /a/c) should result in /a/c", acRef, acRef.intersect(acRef))
        assertEquals("intersect(/a, .) should result in /", root, aRef.intersect(dotRef))
        assertEquals("intersect(/a/c, /a) should result in /a", aRef, acRef.intersect(aRef))
        assertEquals("intersect(/a, /a/c) should result in /a", aRef, aRef.intersect(acRef))
        assertEquals("intersect(/a/c/d, /a/c/e) should result in /a/c", acRef, aceRef.intersect(acdRef))
        assertEquals("intersect(/a/c/e, /b) should result in /", root, aceRef.intersect(bRef))
        assertEquals("intersect(.,.) should result in /", root, dotRef.intersect(dotRef))
    }

    @Test
    fun testContextualization() {
        // ('c').contextualize('/a/b') ==> /a/b/c
        var contextualizeEval = floatc.contextualize(abRef)
        assertTrue("context: c didn't evaluate to $abcRef, but rather to $contextualizeEval",
            abcRef == contextualizeEval)

        // ('./c').contextualize('/a/b') ==> /a/b/c
        contextualizeEval = floatc2.contextualize(abRef)
        assertTrue("context: ./c didn't evaluate to $abcRef, but rather to $contextualizeEval",
            abcRef == contextualizeEval)

        // ('../c').contextualize('/a/b') ==> /a/c
        contextualizeEval = backc.contextualize(abRef)
        assertTrue("context: ../c didn't evaluate to $ac2Ref, but rather to $contextualizeEval",
            ac2Ref == contextualizeEval)

        // ('c').contextualize('./c') ==> null
        contextualizeEval = floatc.contextualize(floatc2)
        assertTrue("Was successfully able to contextualize against an ambiguous reference.",
            contextualizeEval == null)

        // ('a[-1]').contextualize('a[2]') ==> something like a[position() != 2]
        contextualizeEval = a2extRef.contextualize(a2Ref)
        assertTrue("Treeref from named instance wrongly accepted multiplicity context from root instance",
            contextualizeEval!!.getMultLast() != 2)

        // Test trying to figure out multiplicity copying during contextualization
        // setup ../../a[6] reference
        val a5 = root.extendRef("a", 5)
        a5.setRefLevel(2)

        // setup expected result /a[2]/a[6] reference
        var expectedAs = a.extendRef("a", 5)

        // ('../../a[6]').contextualize('/a[2]/a[3]/a[4]') ==> /a[2]/a[6]
        contextualizeEval = a5.contextualize(aaa)
        assertTrue("Got $contextualizeEval and expected /a[2]/a[6]" +
            " for test of multiplicity copying when level names are same between refs",
            expectedAs == contextualizeEval)

        // setup expected result /a[2]/a[3] reference
        expectedAs = a.extendRef("a", 2)

        // ('../../a').contextualize('/a[2]/a[3]/a[4]') ==> /a[2]/a[3]
        contextualizeEval = a5.genericize().contextualize(aaa)
        assertTrue("Got $contextualizeEval and expected /a[2]/a[3]" +
            " for test of multiplicity copying when level names are same between refs",
            expectedAs == contextualizeEval)

        // ('c').contextualize('/a/*') ==> /a/*/c
        val wildA = XPathReference.getPathExpr("/a/*").getReference()
        contextualizeEval = floatc.contextualize(wildA)
        assertTrue("Got $contextualizeEval and expected /a/*/c for test of wildcard merging",
            wildA.extendRef("c", TreeReference.INDEX_UNBOUND) == contextualizeEval)

        // ('../*').contextualize('/a/c') ==> /a/c (see note in original test)
        val wildBack = XPathReference.getPathExpr("../*").getReference()
        contextualizeEval = wildBack.contextualize(acRef)
        assertTrue("Got $contextualizeEval and expected /a/c for test of wildcard merging",
            acRef.genericize() == contextualizeEval!!.genericize())

        // ('../a[6]').contextualize('/a/*/a') ==> /a/*/a[6]
        var wildAs = XPathReference.getPathExpr("/a/*").getReference()
        wildAs = wildAs.extendRef("a", TreeReference.DEFAULT_MUTLIPLICITY)
        a5.setRefLevel(1)
        contextualizeEval = a5.contextualize(wildAs)
        expectedAs = XPathReference.getPathExpr("/a/*").getReference().extendRef("a", 5)
        assertTrue("Got $contextualizeEval and expected /a/*/a[6]" +
            " for test of multiplicity copying when level names are same between refs",
            expectedAs == contextualizeEval)

        // ('../../a[6]').contextualize('/a/*/a') ==> /a/a[6]
        wildAs = XPathReference.getPathExpr("/a/*/a").getReference()
        a5.setRefLevel(2)
        contextualizeEval = a5.contextualize(wildAs)
        expectedAs = XPathReference.getPathExpr("/a").getReference().extendRef("a", 5)
        assertTrue("Got $contextualizeEval and expected /a/a[6]" +
            " during specializing wildcard during contextualization.",
            expectedAs == contextualizeEval)
    }

    @Test
    fun testAnchor() {
        // ('../c').anchor('/a/b') ==> a/c
        var anchorEval = backc.anchor(abRef)
        assertTrue("/a/b/ + ../c should anchor to /a/c not $anchorEval",
            ac2Ref == anchorEval)

        // ('/a/c').anchor('./c') ==> a/c
        anchorEval = ac2Ref.anchor(floatc)
        assertTrue("./c + /a/c should just return /a/c not $anchorEval",
            ac2Ref == anchorEval)

        // ('./c').anchor('./c') ==> null
        anchorEval = floatc.anchor(floatc)
        assertTrue("./c + ./c should return null since trying to anchor to a relative ref",
            anchorEval == null)

        // ('../c').anchor('/a') ==> null
        anchorEval = back2c.anchor(aRef)
        assertTrue("/a + ../../c should return null since there are too many ../'s",
            anchorEval == null)

        // ('../*').anchor('/a/z') ==> /a/*/
        val wildBack = XPathReference.getPathExpr("../*").getReference()
        val wildA = XPathReference.getPathExpr("/a/*").getReference()
        val azRef = XPathReference.getPathExpr("/a/z").getReference()
        anchorEval = wildBack.anchor(azRef)
        assertTrue("Got $anchorEval and expected $wildA for anchoring with wildcards",
            wildA == anchorEval)
    }

    @Test
    fun testParent() {
        // ('/a/b').parent('/a/c') ==> '/a/b'
        var parentEval = abRef.parent(acRef)
        assertTrue("taking the parent of an absolute ref should return a copy of that ref",
            abRef == parentEval)

        // ('../c').parent('/a/b') ==> null
        parentEval = backc.parent(abRef)
        assertTrue("you can't take the parent of a relative reference, unless it is also relative",
            parentEval == null)

        // ('../c').parent('../c') ==> null
        parentEval = backc.parent(backc)
        assertTrue("The argument to calling 'parent' on a relative reference " +
            "must be a series of ../'s with no reference level data",
            parentEval == null)

        // ('../c').parent('../') ==> '../../c/'
        parentEval = backc.parent(parentRef)
        assertTrue("calling 'parent' on a relative reference with ../'s should join " +
            "them with those of the level-less relative argument reference.",
            back2c == parentEval)

        // ('./c').parent('/a/b') ==> '/a/b/c'
        parentEval = floatc.parent(abRef)
        assertTrue("standard call to 'parent' returned $parentEval instead of expected /a/b/c",
            abcRef == parentEval)
    }

    @Test
    fun testPredicates() {
        assertFalse("Predicates weren't correctly removed from reference.",
            acPredRef.removePredicates().hasPredicates())

        assertTrue("Predicates weren't correctly detected.",
            acPredRef.hasPredicates())

        assertNull("Found predicates where they shouldn't be.",
            acPredRef.getPredicate(1))

        assertTrue("Didn't find predicates where they should be.",
            acPredRef.getPredicate(0) === apreds)

        assertEquals("/a[..b = 'test'] Did not equal itself!",
            acPredRef, acPredMatchRef)

        assertNotEquals("/a[..b = 'test'] was equal to /a[..b = 'fail']",
            acPredRef, acPredNotRef)
    }

    @Test
    fun testGenericize() {
        // Generic ref to generic attribute
        val attributeRef = XPathReference.getPathExpr("/data/node/@attribute").getReference()

        // re-genericize
        val genericRef = attributeRef.genericize()

        if (attributeRef != genericRef) {
            fail("Genericize improperly converted $attributeRef to $genericRef")
        }

        // (/data/aRef[3]).genericize() ==> /data/aRef (with aRef's multiplicity being -1)
        if (aRef.genericize() != a2Ref.genericize()) {
            fail("Genericize improperly converted removed multiplicities of $a2Ref" +
                ", which should, once genericized, should match${aRef.genericize()}")
        }

        // but 'aRef' in aRef should have the default multiplicity of 0
        if (aRef == a2Ref.genericize()) {
            fail("Genericize improperly converted removed multiplicities of $a2Ref" +
                ", which should, once genericized, should match$aRef")
        }
    }

    @Test
    fun testRelativeGeneration() {
        assertEquals("/a/b/c.relative(2)->./c", floatc2,
            abcRef.getRelativeReferenceAfter(abRef.size()))
        assertEquals("/a/b/c.relative(1)->.b/c", floatbc,
            abcRef.getRelativeReferenceAfter(aRef.size()))
        assertEquals("/a/b/c.relative(0)->/a/b/c", abcRef,
            abcRef.getRelativeReferenceAfter(TreeReference.selfRef().size()))

        assertEquals("predicates not retained with relative generation", fPredRef,
            dfPredRef.getRelativeReferenceAfter(1))
        assertNotEquals("f[preds] should not equal f after relative generaiton", fNoPredRef,
            dfPredRef.getRelativeReferenceAfter(1))

        assertEquals("./b/c.relative(0)->./b/c", floatbc,
            floatbc.getRelativeReferenceAfter(0))
        assertEquals("./b/c.relative(1)-> ./c", floatc,
            floatbc.getRelativeReferenceAfter(1))

        assertEquals("current()/c.relative(0) -> ./c", floatc2,
            currentC.getRelativeReferenceAfter(0))
    }

    @Test
    fun testRelativize() {
        assertEquals(
            r("child/@attribute"),
            r("/data/full/path/to/child/@attribute").relativize(r("/data/full/path/to")))
    }

    private fun r(reference: String): TreeReference {
        return XPathReference.getPathExpr(reference).getReference()
    }
}
