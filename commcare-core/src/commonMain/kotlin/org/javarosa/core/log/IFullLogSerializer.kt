package org.javarosa.core.log

interface IFullLogSerializer<T> {
    fun serializeLogs(logs: Array<LogEntry>): T
}
