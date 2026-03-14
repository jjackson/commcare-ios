package org.commcare.data.xml

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.javarosa.core.model.instance.utils.TreeUtilities
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Collections

class TreeBuilderTest {

    @Test
    @Throws(UnfullfilledRequirementsException::class, XmlPullParserException::class,
            InvalidStructureException::class, IOException::class)
    fun testBuildTree() {
        val nodes = ImmutableList.of(
                SimpleNode.textNode("node", ImmutableMap.of("a1", "x1", "b1", "y1"), "text1"),
                SimpleNode.textNode("node", ImmutableMap.of("a2", "x2"), "text2"),
                SimpleNode.parentNode("node", Collections.emptyMap(), ImmutableList.of(
                        SimpleNode.parentNode("child", Collections.emptyMap(), ImmutableList.of(
                                SimpleNode.textNode("grandchild", Collections.emptyMap(), "text3"),
                                SimpleNode.textNode("grandchild", Collections.emptyMap(), "text4")
                        ))
                ))
        )
        val test = TreeBuilder.buildTree("test-instance", "test", nodes)

        val expectedXml = listOf(
                "<test id=\"test-instance\">",
                "<node a1=\"x1\" b1=\"y1\">text1</node>",
                "<node a2=\"x2\">text2</node>",
                "<node>",
                "<child>",
                "<grandchild>text3</grandchild>",
                "<grandchild>text4</grandchild>",
                "</child>",
                "</node>",
                "</test>"
        ).joinToString("")

        val expected = TreeUtilities.xmlStreamToTreeElement(
                ByteArrayInputStream(expectedXml.toByteArray(StandardCharsets.UTF_8)),
                "test-instance"
        )
        assertEquals(expected, test)
    }
}
