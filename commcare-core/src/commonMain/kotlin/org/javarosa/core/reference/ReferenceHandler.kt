package org.javarosa.core.reference

import org.javarosa.core.util.PlatformThreadLocal
import kotlin.jvm.JvmStatic

/**
 * Created by willpride on 2/20/18.
 */
class ReferenceHandler {
    companion object {
        private var staticManager: ReferenceManager? = null

        private val threadLocalManager: PlatformThreadLocal<ReferenceManager?> =
            PlatformThreadLocal { null }

        private var useThreadLocal: Boolean = false

        @JvmStatic
        fun setUseThreadLocalStrategy(useThreadLocal: Boolean) {
            this.useThreadLocal = useThreadLocal
        }

        /**
         * @return Singleton accessor to the global
         * ReferenceManager.
         */
        @JvmStatic
        fun instance(): ReferenceManager {
            return if (useThreadLocal) {
                val manager = threadLocalManager.get()
                if (manager == null) {
                    val newManager = ReferenceManager()
                    threadLocalManager.set(newManager)
                    newManager
                } else {
                    manager
                }
            } else {
                if (staticManager == null) {
                    staticManager = ReferenceManager()
                }
                staticManager!!
            }
        }

        @JvmStatic
        fun clearInstance() {
            threadLocalManager.set(null)
            staticManager = null
        }
    }
}
