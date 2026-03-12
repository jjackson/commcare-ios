package org.javarosa.core.services.transport.payload

/**
 * @author Clayton Sims
 * @date Dec 18, 2008
 */
interface IDataPayloadVisitor<T> {
    fun visit(payload: ByteArrayPayload): T

    fun visit(payload: MultiMessagePayload): T

    fun visit(payload: DataPointerPayload): T
}
