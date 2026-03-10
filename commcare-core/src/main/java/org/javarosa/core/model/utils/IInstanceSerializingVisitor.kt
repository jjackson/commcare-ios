package org.javarosa.core.model.utils

import org.javarosa.core.model.IAnswerDataSerializer
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.services.transport.payload.IDataPayload
import org.javarosa.model.xform.XPathReference
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * An IInstanceSerializingVisitor serializes a DataModel
 *
 * @author Clayton Sims
 */
interface IInstanceSerializingVisitor : IInstanceVisitor {

    @Throws(PlatformIOException::class)
    fun serializeInstance(model: FormInstance, ref: XPathReference): ByteArray

    @Throws(PlatformIOException::class)
    fun serializeInstance(model: FormInstance): ByteArray

    @Throws(PlatformIOException::class)
    fun createSerializedPayload(model: FormInstance, ref: XPathReference): IDataPayload

    @Throws(PlatformIOException::class)
    fun createSerializedPayload(model: FormInstance): IDataPayload

    fun setAnswerDataSerializer(ads: IAnswerDataSerializer)

    fun newInstance(): IInstanceSerializingVisitor
}
