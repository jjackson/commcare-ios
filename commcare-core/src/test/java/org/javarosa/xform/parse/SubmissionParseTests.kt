package org.javarosa.xform.parse

import org.javarosa.core.test.FormParseInit
import org.javarosa.model.xform.XPathReference
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Created by ctsims on 9/27/2017.
 */
class SubmissionParseTests {

    @Test
    fun minimalParse() {
        assertNotNull(FormParseInit("/submission_profiles/submission_minimum.xml").getFormDef()!!.getSubmissionProfile("submitid"))
    }

    @Test
    fun successfulParses() {
        val profile = FormParseInit("/submission_profiles/submission_full.xml").getFormDef()!!.getSubmissionProfile("submitid")
        assertNotNull(profile)

        // Note: original Java had these as bare expressions (not assertions)
        "http://test.test" == profile!!.resource
        XPathReference.getPathExpr("/data/item") == profile.ref
        XPathReference.getPathExpr("/data/params") == profile.targetRef
    }

    @Test(expected = XFormParseException::class)
    fun missingResource() {
        FormParseInit("/submission_profiles/no_resource.xml")
    }

    @Test(expected = XFormParseException::class)
    fun missingTarget() {
        FormParseInit("/submission_profiles/missing_target.xml")
    }

    @Test(expected = XFormParseException::class)
    fun invalidTarget() {
        FormParseInit("/submission_profiles/invalid_target.xml")
    }

    @Test(expected = XFormParseException::class)
    fun invalidSourceRef() {
        FormParseInit("/submission_profiles/invalid_read_ref.xml")
    }
}
