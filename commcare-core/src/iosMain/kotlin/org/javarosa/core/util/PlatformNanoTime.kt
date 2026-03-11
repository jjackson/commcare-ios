package org.javarosa.core.util

import platform.posix.clock_gettime_nsec_np
import platform.posix.CLOCK_UPTIME_RAW

actual fun platformNanoTime(): Long = clock_gettime_nsec_np(CLOCK_UPTIME_RAW).toLong()
