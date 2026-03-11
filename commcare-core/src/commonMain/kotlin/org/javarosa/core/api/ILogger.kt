package org.javarosa.core.api

import org.javarosa.core.log.IFullLogSerializer
import org.javarosa.core.log.StreamLogSerializer
import org.javarosa.core.model.utils.PlatformDate
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * IIncidentLogger's are used for instrumenting applications to identify usage
 * patterns, usability errors, and general trajectories through applications.
 *
 * @author Clayton Sims
 */
interface ILogger {

    fun log(type: String, message: String, logDate: PlatformDate)

    fun clearLogs()

    fun <T> serializeLogs(serializer: IFullLogSerializer<T>): T

    @Throws(PlatformIOException::class)
    fun serializeLogs(serializer: StreamLogSerializer)

    @Throws(PlatformIOException::class)
    fun serializeLogs(serializer: StreamLogSerializer, limit: Int)

    fun panic()

    fun logSize(): Int

    fun halt()

    fun logException(e: Throwable)
}
