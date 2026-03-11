package org.javarosa.core.model.trace

import io.opentracing.util.GlobalTracer

actual typealias PlatformTrace = datadog.trace.api.Trace

actual fun setActiveSpanTag(key: String, value: String) {
    val span = GlobalTracer.get().activeSpan()
    span?.setTag(key, value)
}
