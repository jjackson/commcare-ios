package org.javarosa.core.services

import org.commcare.util.LogTypes
import org.javarosa.core.api.ILogger
import org.javarosa.core.log.FatalException
import org.javarosa.core.log.WrappedException
import org.javarosa.core.model.utils.PlatformDate
import org.javarosa.core.util.PlatformThread
import org.javarosa.core.util.platformStdErrPrintln
import kotlin.jvm.JvmStatic
import kotlin.math.min

object Logger {
    private const val MAX_MSG_LENGTH = 2048

    @JvmStatic
    var logger: ILogger? = null
        private set

    @JvmStatic
    fun registerLogger(theLogger: ILogger?) {
        logger = theLogger
    }

    @JvmStatic
    fun instance(): ILogger? {
        return logger
    }

    @JvmStatic
    fun log(type: String, message: String?) {
        var msg = message
        platformStdErrPrintln("logger> $type: $msg")
        if (msg == null) {
            msg = ""
        }
        if (msg.length > MAX_MSG_LENGTH) {
            platformStdErrPrintln("  (message truncated)")
        }

        msg = msg.substring(0, min(msg.length, MAX_MSG_LENGTH))
        if (logger != null) {
            try {
                logger!!.log(type, msg, PlatformDate())
            } catch (e: RuntimeException) {
                platformStdErrPrintln("exception when trying to write log message! " + WrappedException.printException(e))
                logger!!.panic()
            }
        }
    }

    @JvmStatic
    fun exception(info: String?, e: Throwable) {
        e.printStackTrace()
        val prefix = if (info != null) "$info: " else ""
        log(LogTypes.TYPE_EXCEPTION, prefix + WrappedException.printException(e))
        if (logger != null) {
            try {
                var message = e.message
                if (message == null) {
                    message = ""
                }
                logger!!.logException(Exception(prefix + message, e))
            } catch (ex: RuntimeException) {
                logger!!.panic()
            }
        }
    }

    @JvmStatic
    fun die(thread: String, e: Exception) {
        exception("unhandled exception at top level", e)
        e.printStackTrace()

        val crashException = FatalException("unhandled exception in $thread", e)

        PlatformThread.startThread {
            throw crashException
        }

        PlatformThread.sleep(3000)
        throw crashException
    }

    @JvmStatic
    fun detachLogger() {
        logger = null
    }
}
