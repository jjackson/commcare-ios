package org.javarosa.core.model.trace

/**
 * No-op tracing annotation for iOS.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
actual annotation class PlatformTrace

/**
 * No-op span tag setter for iOS.
 */
actual fun setActiveSpanTag(key: String, value: String) {
    // No-op on iOS
}
