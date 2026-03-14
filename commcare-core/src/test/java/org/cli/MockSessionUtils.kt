package org.cli

import org.javarosa.core.util.ListMultimap
import org.commcare.core.interfaces.UserSandbox
import org.commcare.modern.session.SessionWrapper
import org.commcare.suite.model.PostRequest
import org.commcare.util.CommCarePlatform
import org.commcare.util.screen.SessionUtils
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PrintStream
import java.net.URL

/**
 * Mock version of SessionUtils that does not do requests
 */
class MockSessionUtils : SessionUtils {

    private var mockQueryResponse: InputStream? = null

    constructor()

    constructor(mockResponse: String) {
        this.mockQueryResponse = ByteArrayInputStream(mockResponse.toByteArray())
    }

    constructor(response: InputStream) {
        this.mockQueryResponse = response
    }

    override fun restoreUserToSandbox(
        sandbox: UserSandbox, session: SessionWrapper, platform: CommCarePlatform,
        username: String, password: String, printStream: PrintStream
    ) {
    }

    @Throws(IOException::class)
    override fun doPostRequest(
        syncPost: PostRequest, session: SessionWrapper, username: String,
        password: String, printStream: PrintStream
    ): Int {
        return 201
    }

    override fun makeQueryRequest(
        url: URL, requestData: ListMultimap<String, String>, username: String,
        password: String
    ): InputStream? {
        return this.mockQueryResponse
    }
}
