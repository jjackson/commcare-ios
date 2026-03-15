package org.commcare.benchmarks

import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.test.FormParseInit
import org.javarosa.core.util.externalizable.LivePrototypeFactory
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.openjdk.jmh.annotations.*

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
        formInstance = fpi.getFormDef()!!.getMainInstance()!!

        // Pre-serialize for deserialization benchmark
        val out = PlatformDataOutputStream()
        formInstance.writeExternal(out)
        serializedFormBytes = out.toByteArray()
    }

    /**
     * Serialize a form instance to bytes.
     */
    @Benchmark
    fun serializeFormInstance(): ByteArray {
        val out = PlatformDataOutputStream()
        formInstance.writeExternal(out)
        return out.toByteArray()
    }

    /**
     * Deserialize a form instance from bytes.
     */
    @Benchmark
    fun deserializeFormInstance(): FormInstance {
        val instance = FormInstance()
        instance.readExternal(
            PlatformDataInputStream(serializedFormBytes),
            prototypeFactory
        )
        return instance
    }

    /**
     * Full round-trip: serialize then deserialize.
     */
    @Benchmark
    fun roundTripFormInstance(): FormInstance {
        val out = PlatformDataOutputStream()
        formInstance.writeExternal(out)
        val bytes = out.toByteArray()

        val result = FormInstance()
        result.readExternal(
            PlatformDataInputStream(bytes),
            prototypeFactory
        )
        return result
    }
}
