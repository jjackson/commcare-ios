package org.commcare.benchmarks

import org.commcare.cases.model.Case
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.test.FormParseInit
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.LivePrototypeFactory
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.openjdk.jmh.annotations.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

@State(Scope.Benchmark)
open class SerializationBenchmark {

    private lateinit var formInstance: FormInstance
    private lateinit var serializedFormBytes: ByteArray
    private lateinit var prototypeFactory: PrototypeFactory

    @Setup(Level.Trial)
    fun setUp() {
        prototypeFactory = LivePrototypeFactory()

        // Load a form and get its instance for serialization benchmarking
        val fpi = FormParseInit("/app_performance/large_tdh_form.xml")
        formInstance = fpi.formDef.mainInstance

        // Pre-serialize for deserialization benchmark
        val baos = ByteArrayOutputStream()
        formInstance.writeExternal(DataOutputStream(baos))
        serializedFormBytes = baos.toByteArray()
    }

    /**
     * Serialize a form instance to bytes.
     */
    @Benchmark
    fun serializeFormInstance(): ByteArray {
        val baos = ByteArrayOutputStream()
        formInstance.writeExternal(DataOutputStream(baos))
        return baos.toByteArray()
    }

    /**
     * Deserialize a form instance from bytes.
     */
    @Benchmark
    fun deserializeFormInstance(): FormInstance {
        val instance = FormInstance()
        instance.readExternal(
            DataInputStream(ByteArrayInputStream(serializedFormBytes)),
            prototypeFactory
        )
        return instance
    }

    /**
     * Full round-trip: serialize then deserialize.
     */
    @Benchmark
    fun roundTripFormInstance(): FormInstance {
        val baos = ByteArrayOutputStream()
        formInstance.writeExternal(DataOutputStream(baos))
        val bytes = baos.toByteArray()

        val result = FormInstance()
        result.readExternal(
            DataInputStream(ByteArrayInputStream(bytes)),
            prototypeFactory
        )
        return result
    }
}
