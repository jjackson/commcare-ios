package org.javarosa.test_utils

import org.javarosa.core.model.actions.FormSendCalloutHandler
import org.javarosa.core.util.ListMultimap

/**
 * Mocks the responses needed for form <submission/> responses in tests
 */
class MockFormSendCalloutHandler private constructor(
    private val payload: String?,
    private val throwException: Boolean
) : FormSendCalloutHandler {

    private var argreturn: String? = null

    override fun performHttpCalloutForResponse(url: String, paramMap: ListMultimap<String, String>?): String? {
        if (throwException) {
            throw RuntimeException("Expected Http Callout Exception")
        } else if (argreturn != null) {
            return paramMap!![argreturn!!].first()
        } else {
            return payload
        }
    }

    companion object {
        @JvmStatic
        fun forSuccess(payload: String?): MockFormSendCalloutHandler {
            return MockFormSendCalloutHandler(payload, false)
        }

        @JvmStatic
        fun succeedWithArgAtKey(argreturn: String): MockFormSendCalloutHandler {
            val handler = MockFormSendCalloutHandler(null, false)
            handler.argreturn = argreturn
            return handler
        }

        @JvmStatic
        fun withException(): MockFormSendCalloutHandler {
            return MockFormSendCalloutHandler(null, true)
        }

        @JvmStatic
        fun nullResponse(): MockFormSendCalloutHandler {
            return MockFormSendCalloutHandler(null, false)
        }
    }
}
