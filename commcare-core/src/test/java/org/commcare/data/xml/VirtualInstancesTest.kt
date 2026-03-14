package org.commcare.data.xml

import com.google.common.collect.ImmutableMap
import org.javarosa.core.model.instance.ExternalDataInstance
import org.javarosa.core.model.instance.utils.TreeUtilities
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

class VirtualInstancesTest {

    @Test
    @Throws(UnfullfilledRequirementsException::class, XmlPullParserException::class,
            InvalidStructureException::class, IOException::class)
    fun testBuildSearchInputRoot() {
        val ref = "results"
        val instanceId = VirtualInstances.makeSearchInputInstanceID(ref)
        val instance = VirtualInstances.buildSearchInputInstance(ref, ImmutableMap.of(
                "key0", "val0",
                "key1", "val1",
                "key2", "val2"
        ))
        val expectedXml = listOf(
                "<input id=\"search-input:results\">",
                "<field name=\"key0\">val0</field>",
                "<field name=\"key1\">val1</field>",
                "<field name=\"key2\">val2</field>",
                "</input>"
        ).joinToString("")

        val expected = TreeUtilities.xmlStreamToTreeElement(
                ByteArrayInputStream(expectedXml.toByteArray(StandardCharsets.UTF_8)),
                instanceId
        )
        assertEquals(expected, instance.getRoot())
    }

    @Test
    @Throws(UnfullfilledRequirementsException::class, XmlPullParserException::class,
            InvalidStructureException::class, IOException::class)
    fun testBuildSelectedValuesInstance() {
        val instanceId = "selected-cases"
        val selectedValues = arrayOf("case1", "case2")
        val instance = VirtualInstances.buildSelectedValuesInstance(instanceId, selectedValues)
        val expectedXml = listOf(
                "<results id=\"selected-cases\">",
                "<value>case1</value>",
                "<value>case2</value>",
                "</results>"
        ).joinToString("")

        val expected = TreeUtilities.xmlStreamToTreeElement(
                ByteArrayInputStream(expectedXml.toByteArray(StandardCharsets.UTF_8)),
                instanceId
        )
        assertEquals(expected, instance.getRoot())
    }

    @Test
    fun testGetReferenceId() {
        val instanceReference = VirtualInstances.getRemoteReference("instanceId")
        assertEquals("instanceId", VirtualInstances.getReferenceId(instanceReference))
    }

    @Test
    fun testGetReferenceScheme() {
        val instanceReference = VirtualInstances.getRemoteReference("instanceId")
        assertEquals(ExternalDataInstance.JR_REMOTE_REFERENCE,
                VirtualInstances.getReferenceScheme(instanceReference))
    }
}
