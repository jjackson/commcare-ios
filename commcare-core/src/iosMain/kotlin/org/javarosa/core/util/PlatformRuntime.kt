package org.javarosa.core.util

import platform.posix.sysconf
import platform.posix._SC_PHYS_PAGES
import platform.posix._SC_PAGESIZE

actual fun platformMaxMemory(): Long {
    val pages = sysconf(_SC_PHYS_PAGES)
    val pageSize = sysconf(_SC_PAGESIZE)
    return if (pages > 0 && pageSize > 0) pages * pageSize else -1L
}
