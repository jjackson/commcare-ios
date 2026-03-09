package org.javarosa.core.services

import org.commcare.util.LogTypes
import org.javarosa.core.api.ILogger
import org.javarosa.core.log.FatalException
import org.javarosa.core.log.WrappedException
import java.util.Date

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

    /**
     * Posts the given data to an existing Incident Log, if one has
     * been registered and if logging is enabled on the device.
     *
     * NOTE: This method makes a best faith attempt to log the given
     * data, but will not produce any output if such attempts fail.
     *
     * @param type    The type of incident to be logged.
     * @param message A message describing the incident.
     */
    @JvmStatic
    fun log(type: String, message: String?) {
        var msg = message
        System.err.println("logger> $type: $msg")
        if (msg == null) {
            msg = ""
        }
        if (msg.length > MAX_MSG_LENGTH) {
            System.err.println("  (message truncated)")
        }

        msg = msg.substring(0, Math.min(msg.length, MAX_MSG_LENGTH))
        if (logger != null) {
            try {
                logger!!.log(type, msg, Date())
            } catch (e: RuntimeException) {
                //do not catch exceptions here; if this fails, we want the exception to propogate
                System.err.println("exception when trying to write log message! " + WrappedException.printException(e))
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
        //log exception
        exception("unhandled exception at top level", e)

        //print stacktrace
        e.printStackTrace()

        //crash
        val crashException = FatalException("unhandled exception in $thread", e)

        //depending on how the code was invoked, a straight 'throw' won't always reliably crash the app
        //throwing in a thread should work (at least on our nokias)
        Thread {
            throw crashException
        }.start()

        //still do plain throw as a fallback
        try {
            Thread.sleep(3000)
        } catch (ie: InterruptedException) {
        }
        throw crashException
    }

    @JvmStatic
    fun detachLogger() {
        logger = null
    }
}
