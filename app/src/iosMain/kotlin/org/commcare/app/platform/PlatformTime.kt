package org.commcare.app.platform

import platform.Foundation.*

internal actual fun currentEpochSeconds(): Long = (NSDate().timeIntervalSince1970).toLong()
