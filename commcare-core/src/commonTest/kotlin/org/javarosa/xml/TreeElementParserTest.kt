package org.javarosa.xml

import org.javarosa.core.io.createByteArrayInputStream
import org.javarosa.core.model.instance.utils.TreeUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cross-platform tests for [TreeElementParser] that run on both JVM and iOS.
 *
 * These cover the interaction between the underlying pull-parser
 * ([PlatformXmlParser]) and [TreeElementParser]'s tree-building loop. The
 * specific shape they guard against is: a parent element that contains XML
 * comments (or other whitespace-producing events) between child elements.
 *
 * Historical context: the iOS pull parser ([PlatformXmlParserIos]) does not
 * coalesce whitespace around comments at `depth > 0` the way kxml2 does on
 * JVM, so between two sibling elements separated by a comment it emits:
 *     TEXT("\n    ")  -- whitespace before the comment
 *     (comment hidden by skipDeclaration)
 *     TEXT("\n    ")  -- whitespace after the comment
 * The single-advance `nextNonWhitespace` helper left the second whitespace
 * event live, which caused [TreeElementParser] to call `setValue("")` on the
 * parent, making it non-childable and crashing the next `addChild()` with
 * "Can't add children to node that has data value!".
 *
 * The fix hardens `nextNonWhitespace()` to loop over all consecutive
 * whitespace events, and defensively skips empty-after-trim text in
 * [TreeElementParser] so a single rogue whitespace event never corrupts tree
 * shape. Both fixes live in commonMain, and both are exercised by running
 * these tests on iOS.
 */
class TreeElementParserTest {

    /**
     * Direct reproduction of the iOS Visit form failure: the static
     * `casedb_instance_structure.xml` schema has whitespace + comments
     * between every child element. Before the fix, loading this through
     * [TreeElementParser] crashed on iOS with "Can't add children to node
     * that has data value!" on the second `<case_name/>`-or-later sibling.
     */
    @Test
    fun parsesSchemaWithCommentsBetweenSiblings() {
        val xml = """<wrapper>
    <case case_id="" case_type="" owner_id="" status="">
        <!-- case_id: The unique GUID of this case -->
        <!-- case_type: The id of this case's type -->
        <!-- owner_id: The GUID of the case or group which owns this case -->
        <case_name/>
        <!-- The name of the case-->
        <date_opened/>
        <!-- The date this case was opened -->
        <last_modified/>
        <!-- The date of the case's last transaction -->
        <CASEDB_WILD_CARD/>
        <!-- An arbitrary data value set in this case -->
        <index>
            <CASEDB_WILD_CARD case_type="" relationship=""/>
        </index>
    </case>
</wrapper>
"""

        val stream = createByteArrayInputStream(xml.encodeToByteArray())
        val root = TreeUtilities.xmlStreamToTreeElement(stream, "casedb")

        assertEquals("wrapper", root.getName())

        val case = root.getChildAt(0)
        assertNotNull(case, "wrapper must contain a <case> child")
        assertEquals("case", case.getName())

        // The case element must have kept all its static-schema children.
        // Before the fix, iOS crashed before adding the second child.
        val childNames = (0 until case.getNumChildren()).map { case.getChildAt(it)?.getName() }
        assertTrue(
            childNames.contains("case_name"),
            "expected case_name child, got $childNames"
        )
        assertTrue(
            childNames.contains("date_opened"),
            "expected date_opened child, got $childNames"
        )
        assertTrue(
            childNames.contains("last_modified"),
            "expected last_modified child, got $childNames"
        )
        assertTrue(
            childNames.contains("CASEDB_WILD_CARD"),
            "expected CASEDB_WILD_CARD child, got $childNames"
        )
        assertTrue(
            childNames.contains("index"),
            "expected index child, got $childNames"
        )

        // The <index> element also has a comment inside it — verify its
        // single child was added correctly as well.
        val indexElement = case.getChildAt(childNames.indexOf("index"))
        assertNotNull(indexElement, "index must be resolvable")
        assertEquals(1, indexElement.getNumChildren())
        assertEquals("CASEDB_WILD_CARD", indexElement.getChildAt(0)?.getName())
    }

    /**
     * Minimal repro: whitespace + a single comment between two sibling
     * self-closing elements. This is the smallest input that triggered
     * the crash on iOS. Useful as a unit-level regression guard.
     */
    @Test
    fun parsesTwoSiblingsSeparatedByComment() {
        val xml = """<parent>
    <first/>
    <!-- separator comment -->
    <second/>
</parent>
"""

        val stream = createByteArrayInputStream(xml.encodeToByteArray())
        val root = TreeUtilities.xmlStreamToTreeElement(stream, "test")

        assertEquals("parent", root.getName())
        assertEquals(2, root.getNumChildren())
        assertEquals("first", root.getChildAt(0)?.getName())
        assertEquals("second", root.getChildAt(1)?.getName())
    }

    /**
     * Whitespace-only text that isn't adjacent to a comment should also be
     * silently absorbed rather than turning the parent into a leaf. This
     * guards the defensive fix in [TreeElementParser] where empty-after-trim
     * text is no longer passed to `setValue`.
     */
    @Test
    fun parsesChildrenSeparatedByOnlyWhitespace() {
        val xml = """<parent>
    <a/>
    <b/>
    <c/>
</parent>
"""

        val stream = createByteArrayInputStream(xml.encodeToByteArray())
        val root = TreeUtilities.xmlStreamToTreeElement(stream, "test")

        assertEquals(3, root.getNumChildren())
    }

    /**
     * Text content with leading and trailing whitespace should still be
     * retained (trimmed) rather than dropped. This guards against an
     * over-eager whitespace filter.
     */
    @Test
    fun preservesNonEmptyTextAfterTrim() {
        val xml = "<parent>   hello   </parent>"

        val stream = createByteArrayInputStream(xml.encodeToByteArray())
        val root = TreeUtilities.xmlStreamToTreeElement(stream, "test")

        assertEquals("parent", root.getName())
        assertEquals(0, root.getNumChildren())
        assertEquals("hello", root.getValue()?.uncast()?.getString())
    }
}
