package org.javarosa.core.services.locale

/**
 * (Yet another) Manager class, this one for determining which localization strategy to use.
 * The options are:
 *
 *  1. staticLocalizer: Static variable for platforms where the same Localizer can be safely shared across
 *     all threads on the JVM (Android)
 *  2. threadLocalLocalizer: ThreadLocal variable for platforms where different threads are potentially
 *     running separate applications (Web Apps)
 *
 *  Defaults to the static Localizer. Web Apps should set the strategy to useThreadLocal = true immediately
 *  on startup.
 *
 *  @author wpride
 */
object LocalizerManager {

    private var staticLocalizer: Localizer? = null

    private val threadLocalLocalizer: ThreadLocal<Localizer?> = object : ThreadLocal<Localizer?>() {
        override fun initialValue(): Localizer {
            return Localizer(true, false)
        }
    }

    private var useThreadLocal: Boolean = false

    fun getGlobalLocalizer(): Localizer? {
        return if (useThreadLocal) {
            threadLocalLocalizer.get()
        } else {
            staticLocalizer
        }
    }

    fun init(force: Boolean) {
        if (useThreadLocal) {
            if (threadLocalLocalizer.get() == null || force) {
                threadLocalLocalizer.set(Localizer(true, false))
            }
        } else {
            if (staticLocalizer == null || force) {
                staticLocalizer = Localizer(true, false)
            }
        }
    }

    fun setUseThreadLocalStrategy(useThreadLocal: Boolean) {
        this.useThreadLocal = useThreadLocal
    }

    fun clearInstance() {
        threadLocalLocalizer.set(null)
        staticLocalizer = null
    }
}
