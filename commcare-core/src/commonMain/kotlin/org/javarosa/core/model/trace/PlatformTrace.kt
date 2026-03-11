package org.javarosa.core.model.trace

/**
 * Platform-specific method tracing annotation.
 * On JVM: maps to datadog.trace.api.Trace for Datadog APM.
 * On iOS: no-op annotation.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
expect annotation class PlatformTrace()

/**
 * Set a tag on the active tracing span, if one exists.
 * On JVM: uses io.opentracing.util.GlobalTracer to set span tags.
 * On iOS: no-op.
 */
expect fun setActiveSpanTag(key: String, value: String)
