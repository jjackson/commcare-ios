package org.commcare.app.platform

internal actual fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1000L
