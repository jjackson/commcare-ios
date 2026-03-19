package org.commcare.app.platform

/**
 * Platform-specific wall-clock time in seconds since the Unix epoch.
 * Used for OAuth token expiry tracking.
 *
 * On JVM: System.currentTimeMillis() / 1000
 * On iOS: NSDate.timeIntervalSince1970.toLong()
 */
internal expect fun currentEpochSeconds(): Long
