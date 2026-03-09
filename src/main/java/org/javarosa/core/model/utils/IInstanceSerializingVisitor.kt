package org.javarosa.core.model.utils

import org.javarosa.core.model.IAnswerDataSerializer
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.services.transport.payload.IDataPayload
import org.javarosa.model.xform.XPathReference
import java.io.IOException

/**
 * An IInstanceSerializingVisitor serializes a DataModel
 *
 * @author Clayton Sims
 */
interface IInstanceSerializingVisitor : IInstanceVisitor {

    @Throws(IOException::class)
    fun serializeInstance(model: FormInstance, ref: XPathReference): ByteArray

    @Throws(IOException::class)
    fun serializeInstance(model: FormInstance): ByteArray

    @Throws(IOException::class)
    fun createSerializedPayload(model: FormInstance, ref: XPathReference): IDataPayload

    @Throws(IOException::class)
    fun createSerializedPayload(model: FormInstance): IDataPayload

    fun setAnswerDataSerializer(ads: IAnswerDataSerializer)

    fun newInstance(): IInstanceSerializingVisitor
}
