package org.commcare.test

actual object TestResources {
    actual fun loadResource(path: String): ByteArray {
        throw UnsupportedOperationException(
            "Test resource loading on iOS is not yet implemented. " +
            "Requires XFormParser port to commonMain before iOS golden file tests can run."
        )
    }
}
