package org.commcare.core.network

import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.InputStream

class FakeResponseBody(private val inputStream: InputStream) : ResponseBody() {

    override fun contentType(): MediaType? = null

    override fun contentLength(): Long = -1

    override fun source(): BufferedSource = inputStream.source().buffer()
}
