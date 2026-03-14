package org.commcare.util.reference.test

import org.commcare.test.utilities.TestHelpers
import org.javarosa.core.reference.ReferenceManager
import org.javarosa.core.reference.ResourceReference
import org.javarosa.core.reference.ResourceReferenceFactory
import org.junit.Assert
import org.junit.Test

/**
 * Created by ctsims on 8/14/2015.
 */
class JavaResourceReferenceTest {
    @Test
    fun testReferences() {
        ReferenceManager.instance().addReferenceFactory(ResourceReferenceFactory())

        val referenceName = "jr://resource/reference/resource_reference_test.txt"

        val r = ReferenceManager.instance().DeriveReference(referenceName)

        if (r !is ResourceReference) {
            Assert.fail("Incorrect reference type: $r")
        }

        Assert.assertEquals(referenceName, r.getURI())

        val stream = r.getStream()

        if (stream == null) {
            Assert.fail("Couldn't find resource at: ${r.getURI()}")
        }

        TestHelpers.assertStreamContentsEqual(stream, "SUCCESS")
    }
}
