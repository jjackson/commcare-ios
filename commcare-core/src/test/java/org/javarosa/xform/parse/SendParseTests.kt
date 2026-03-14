package org.javarosa.xform.parse

import org.javarosa.core.test.FormParseInit
import org.junit.Test

/**
 * Created by ctsims on 9/27/2017.
 */
class SendParseTests {

    @Test
    fun successfulParses() {
        FormParseInit("/send_action/succesful_parse.xml")
    }

    @Test(expected = XFormParseException::class)
    fun missingEvent() {
        FormParseInit("/send_action/missing_event.xml")
    }

    @Test(expected = XFormParseException::class)
    fun missingSubmission() {
        FormParseInit("/send_action/missing_submission.xml")
    }
}
