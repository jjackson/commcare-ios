package org.commcare.app.platform

import org.commcare.modern.reference.IosHttpRoot
import org.javarosa.core.reference.ReferenceFactory

actual fun createHttpReferenceFactory(): ReferenceFactory = IosHttpRoot()
