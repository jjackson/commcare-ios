package org.javarosa.core.util.externalizable

import org.javarosa.core.model.utils.PlatformDate

/**
 * iOS implementation of SerializationHelpers.
 * Implements the serialization protocol directly, compatible with JVM wire format.
 */
actual object SerializationHelpers {

    actual fun readNumeric(`in`: PlatformDataInputStream): Long {
        val w = ExtWrapIntEncodingUniform()
        w.readExternal(`in`, PrototypeFactory())
        return w.`val` as Long
    }

    actual fun writeNumeric(out: PlatformDataOutputStream, v: Long) {
        val w = ExtWrapIntEncodingUniform(v)
        w.writeExternal(out)
    }

    actual fun readInt(`in`: PlatformDataInputStream): Int {
        return readNumeric(`in`).toInt()
    }

    actual fun readString(`in`: PlatformDataInputStream): String {
        return `in`.readUTF()
    }

    actual fun writeString(out: PlatformDataOutputStream, s: String) {
        out.writeUTF(s)
    }

    actual fun readBool(`in`: PlatformDataInputStream): Boolean {
        return `in`.readBoolean()
    }

    actual fun writeBool(out: PlatformDataOutputStream, b: Boolean) {
        out.writeBoolean(b)
    }

    actual fun readDecimal(`in`: PlatformDataInputStream): Double {
        return `in`.readDouble()
    }

    actual fun writeDecimal(out: PlatformDataOutputStream, d: Double) {
        out.writeDouble(d)
    }

    actual fun write(out: PlatformDataOutputStream, data: Any) {
        when (data) {
            is Externalizable -> data.writeExternal(out)
            is Byte -> writeNumeric(out, data.toLong())
            is Short -> writeNumeric(out, data.toLong())
            is Int -> writeNumeric(out, data.toLong())
            is Long -> writeNumeric(out, data)
            is Char -> out.writeChar(data.code)
            is Float -> writeDecimal(out, data.toDouble())
            is Double -> writeDecimal(out, data)
            is Boolean -> writeBool(out, data)
            is String -> writeString(out, data)
            is PlatformDate -> writeNumeric(out, data.getTime())
            is ByteArray -> {
                writeNumeric(out, data.size.toLong())
                if (data.isNotEmpty()) out.write(data)
            }
            else -> throw IllegalArgumentException("Not a serializable datatype: ${data::class.simpleName}")
        }
    }

    actual fun <T : Externalizable> readExternalizable(
        `in`: PlatformDataInputStream,
        pf: PrototypeFactory,
        creator: () -> T
    ): T {
        val instance = creator()
        instance.readExternal(`in`, pf)
        return instance
    }

    actual fun readTagged(`in`: PlatformDataInputStream, pf: PrototypeFactory): Any {
        return ExtWrapTagged.readTag(`in`, pf).let { type ->
            ExtUtil.read(`in`, type, pf)!!
        }
    }

    actual fun writeTagged(out: PlatformDataOutputStream, obj: Any) {
        ExtWrapTagged.writeTag(out, obj)
        ExtUtil.write(out, obj)
    }

    actual fun readListPoly(`in`: PlatformDataInputStream, pf: PrototypeFactory): ArrayList<Any?> {
        val size = readNumeric(`in`).toInt()
        val list = ArrayList<Any?>(size)
        for (i in 0 until size) {
            list.add(readTagged(`in`, pf))
        }
        return list
    }

    actual fun writeListPoly(out: PlatformDataOutputStream, list: List<*>) {
        writeNumeric(out, list.size.toLong())
        for (item in list) {
            writeTagged(out, item!!)
        }
    }

    actual fun <T : Externalizable> readList(
        `in`: PlatformDataInputStream,
        pf: PrototypeFactory,
        creator: () -> T
    ): ArrayList<T> {
        val size = readNumeric(`in`).toInt()
        val list = ArrayList<T>(size)
        for (i in 0 until size) {
            val item = creator()
            item.readExternal(`in`, pf)
            list.add(item)
        }
        return list
    }

    actual fun writeList(out: PlatformDataOutputStream, list: List<*>) {
        writeNumeric(out, list.size.toLong())
        for (item in list) {
            write(out, item!!)
        }
    }

    actual fun readDate(`in`: PlatformDataInputStream): PlatformDate {
        return PlatformDate(readNumeric(`in`))
    }

    actual fun writeDate(out: PlatformDataOutputStream, date: PlatformDate) {
        writeNumeric(out, date.getTime())
    }

    actual fun readStringList(`in`: PlatformDataInputStream): ArrayList<String> {
        val size = readNumeric(`in`).toInt()
        val list = ArrayList<String>(size)
        for (i in 0 until size) {
            list.add(readString(`in`))
        }
        return list
    }

    actual fun readStringStringMap(`in`: PlatformDataInputStream): MutableMap<String, String> {
        val size = readNumeric(`in`).toInt()
        val map = HashMap<String, String>(size)
        for (i in 0 until size) {
            val key = readString(`in`)
            val value = readString(`in`)
            map[key] = value
        }
        return map
    }

    actual fun writeMap(out: PlatformDataOutputStream, map: Map<*, *>) {
        writeNumeric(out, map.size.toLong())
        for ((key, value) in map) {
            write(out, key!!)
            write(out, value!!)
        }
    }

    actual fun readNullableString(`in`: PlatformDataInputStream, pf: PrototypeFactory): String? {
        return if (`in`.readBoolean()) readString(`in`) else null
    }

    actual fun <T : Externalizable> readNullableExternalizable(
        `in`: PlatformDataInputStream,
        pf: PrototypeFactory,
        creator: () -> T
    ): T? {
        if (!`in`.readBoolean()) return null
        val instance = creator()
        instance.readExternal(`in`, pf)
        return instance
    }

    actual fun readNullableTagged(`in`: PlatformDataInputStream, pf: PrototypeFactory): Any? {
        if (!`in`.readBoolean()) return null
        return readTagged(`in`, pf)
    }

    actual fun readNullableDate(`in`: PlatformDataInputStream): PlatformDate? {
        if (!`in`.readBoolean()) return null
        return readDate(`in`)
    }

    actual fun writeNullable(out: PlatformDataOutputStream, value: Any?) {
        if (value != null) {
            out.writeBoolean(true)
            write(out, value)
        } else {
            out.writeBoolean(false)
        }
    }

    actual fun writeNullableTagged(out: PlatformDataOutputStream, value: Any?) {
        if (value != null) {
            out.writeBoolean(true)
            writeTagged(out, value)
        } else {
            out.writeBoolean(false)
        }
    }

    actual fun <T : Externalizable> readStringExtMap(
        `in`: PlatformDataInputStream,
        pf: PrototypeFactory,
        creator: () -> T
    ): MutableMap<String, T> {
        val size = readNumeric(`in`).toInt()
        val map = HashMap<String, T>(size)
        for (i in 0 until size) {
            val key = readString(`in`)
            val value = creator()
            value.readExternal(`in`, pf)
            map[key] = value
        }
        return map
    }

    actual fun readStringTaggedMap(
        `in`: PlatformDataInputStream,
        pf: PrototypeFactory
    ): MutableMap<String, Any> {
        val size = readNumeric(`in`).toInt()
        val map = HashMap<String, Any>(size)
        for (i in 0 until size) {
            val key = readString(`in`)
            val value = readTagged(`in`, pf)
            map[key] = value
        }
        return map
    }

    actual fun writeTaggedMap(out: PlatformDataOutputStream, map: Map<*, *>) {
        writeNumeric(out, map.size.toLong())
        for ((key, value) in map) {
            write(out, key!!)
            writeTagged(out, value!!)
        }
    }

    actual fun <T : Externalizable> readOrderedStringExtMap(
        `in`: PlatformDataInputStream,
        pf: PrototypeFactory,
        creator: () -> T
    ): LinkedHashMap<String, T> {
        val size = readNumeric(`in`).toInt()
        val map = LinkedHashMap<String, T>(size)
        for (i in 0 until size) {
            val key = readString(`in`)
            val value = creator()
            value.readExternal(`in`, pf)
            map[key] = value
        }
        return map
    }

    actual fun readOrderedStringStringMap(
        `in`: PlatformDataInputStream
    ): LinkedHashMap<String, String> {
        val size = readNumeric(`in`).toInt()
        val map = LinkedHashMap<String, String>(size)
        for (i in 0 until size) {
            val key = readString(`in`)
            val value = readString(`in`)
            map[key] = value
        }
        return map
    }

    actual fun readStringMapPoly(
        `in`: PlatformDataInputStream,
        pf: PrototypeFactory
    ): MutableMap<String, Any> {
        val size = readNumeric(`in`).toInt()
        val map = HashMap<String, Any>(size)
        for (i in 0 until size) {
            val key = readString(`in`)
            val value = readTagged(`in`, pf)
            map[key] = value
        }
        return map
    }

    actual fun writeMapPoly(out: PlatformDataOutputStream, map: Map<*, *>) {
        writeNumeric(out, map.size.toLong())
        for ((key, value) in map) {
            write(out, key!!)
            writeTagged(out, value!!)
        }
    }

    actual fun readStringMultiMap(
        `in`: PlatformDataInputStream,
        pf: PrototypeFactory
    ): org.javarosa.core.util.ListMultimap<String, Any> {
        val size = readNumeric(`in`).toInt()
        val map = org.javarosa.core.util.ListMultimap<String, Any>()
        for (i in 0 until size) {
            val key = readString(`in`)
            val value = readTagged(`in`, pf)
            map.put(key, value)
        }
        return map
    }

    actual fun writeMultiMap(out: PlatformDataOutputStream, map: org.javarosa.core.util.ListMultimap<*, *>) {
        writeNumeric(out, map.size().toLong())
        map.forEach { key, value ->
            write(out, key!!)
            writeTagged(out, value!!)
        }
    }

    actual fun readStringListPolyMap(
        `in`: PlatformDataInputStream,
        pf: PrototypeFactory
    ): MutableMap<String, ArrayList<Any?>> {
        val size = readNumeric(`in`).toInt()
        val map = HashMap<String, ArrayList<Any?>>(size)
        for (i in 0 until size) {
            val key = readString(`in`)
            val value = readListPoly(`in`, pf)
            map[key] = value
        }
        return map
    }

    actual fun writeStringListPolyMap(out: PlatformDataOutputStream, map: Map<*, *>) {
        writeNumeric(out, map.size.toLong())
        for ((key, value) in map) {
            write(out, key!!)
            writeListPoly(out, value as List<*>)
        }
    }

    actual fun readStringBooleanMap(`in`: PlatformDataInputStream): MutableMap<String, Boolean> {
        val size = readNumeric(`in`).toInt()
        val map = HashMap<String, Boolean>(size)
        for (i in 0 until size) {
            val key = readString(`in`)
            val value = readBool(`in`)
            map[key] = value
        }
        return map
    }

    actual fun readBytes(`in`: PlatformDataInputStream): ByteArray {
        val size = readNumeric(`in`).toInt()
        val bytes = ByteArray(size)
        var totalRead = 0
        while (totalRead < size) {
            val read = `in`.read(bytes, totalRead, size - totalRead)
            if (read == -1) throw PlatformIOException("Unexpected EOF: expected $size bytes, got $totalRead")
            totalRead += read
        }
        return bytes
    }

    actual fun writeBytes(out: PlatformDataOutputStream, bytes: ByteArray) {
        writeNumeric(out, bytes.size.toLong())
        if (bytes.isNotEmpty()) out.write(bytes)
    }

    actual fun arrayEquals(a: Array<Any?>, b: Array<Any?>, unwrap: Boolean): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (!nullEquals(a[i], b[i], unwrap)) return false
        }
        return true
    }

    actual fun nullEquals(a: Any?, b: Any?, unwrap: Boolean): Boolean {
        val av = if (unwrap) unwrapValue(a) else a
        val bv = if (unwrap) unwrapValue(b) else b
        if (av == null) return bv == null
        return av == bv
    }

    private fun unwrapValue(o: Any?): Any? {
        return if (o is ExternalizableWrapper) o.baseValue() else o
    }
}
