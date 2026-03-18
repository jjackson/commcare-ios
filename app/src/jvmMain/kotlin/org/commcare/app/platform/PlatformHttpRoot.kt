package org.commcare.app.platform

import org.commcare.modern.reference.JavaHttpRoot
import org.javarosa.core.reference.ReferenceFactory

actual fun createHttpReferenceFactory(): ReferenceFactory = JavaHttpRoot()
