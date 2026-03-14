package org.commcare.test

actual object TestResources {
    actual fun loadResource(path: String): ByteArray {
        return TestResources::class.java.getResourceAsStream(path)?.readBytes()
            ?: throw IllegalArgumentException("Test resource not found: $path")
    }
}
