package org.javarosa.core.util.externalizable

/**
 * JVM implementation of SerializationHelpers that delegates to the existing
 * ExtUtil/ExtWrap framework for binary compatibility.
 */
actual object SerializationHelpers {

    @JvmStatic
    actual fun readNumeric(`in`: PlatformDataInputStream): Long {
        return ExtUtil.readNumeric(`in`)
    }

    @JvmStatic
    actual fun writeNumeric(out: PlatformDataOutputStream, v: Long) {
        ExtUtil.writeNumeric(out, v)
    }

    @JvmStatic
    actual fun readInt(`in`: PlatformDataInputStream): Int {
        return ExtUtil.readInt(`in`)
    }

    @JvmStatic
    actual fun readString(`in`: PlatformDataInputStream): String {
        return ExtUtil.readString(`in`)
    }

    @JvmStatic
    actual fun writeString(out: PlatformDataOutputStream, s: String) {
        ExtUtil.writeString(out, s)
    }

    @JvmStatic
    actual fun readBool(`in`: PlatformDataInputStream): Boolean {
        return ExtUtil.readBool(`in`)
    }

    @JvmStatic
    actual fun writeBool(out: PlatformDataOutputStream, b: Boolean) {
        ExtUtil.writeBool(out, b)
    }

    @JvmStatic
    actual fun readDecimal(`in`: PlatformDataInputStream): Double {
        return ExtUtil.readDecimal(`in`)
    }

    @JvmStatic
    actual fun writeDecimal(out: PlatformDataOutputStream, d: Double) {
        ExtUtil.writeDecimal(out, d)
    }

    @JvmStatic
    actual fun write(out: PlatformDataOutputStream, data: Any) {
        ExtUtil.write(out, data)
    }

    @JvmStatic
    actual fun <T : Externalizable> readExternalizable(
        `in`: PlatformDataInputStream,
        pf: PrototypeFactory,
        creator: () -> T
    ): T {
        val instance = creator()
        instance.readExternal(`in`, pf)
        return instance
    }

    @JvmStatic
    actual fun readTagged(`in`: PlatformDataInputStream, pf: PrototypeFactory): Any {
        return ExtUtil.read(`in`, ExtWrapTagged(), pf)!!
    }

    @JvmStatic
    actual fun writeTagged(out: PlatformDataOutputStream, obj: Any) {
        ExtUtil.write(out, ExtWrapTagged(obj))
    }

    @JvmStatic
    actual fun readListPoly(`in`: PlatformDataInputStream, pf: PrototypeFactory): ArrayList<Any?> {
        @Suppress("UNCHECKED_CAST")
        return ExtUtil.read(`in`, ExtWrapListPoly(), pf) as ArrayList<Any?>
    }

    @JvmStatic
    actual fun writeListPoly(out: PlatformDataOutputStream, list: List<*>) {
        ExtUtil.write(out, ExtWrapListPoly(ArrayList(list)))
    }

    @JvmStatic
    actual fun <T : Externalizable> readList(
        `in`: PlatformDataInputStream,
        pf: PrototypeFactory,
        creator: () -> T
    ): ArrayList<T> {
        val size = ExtUtil.readNumeric(`in`).toInt()
        val list = ArrayList<T>(size)
        for (i in 0 until size) {
            val item = creator()
            item.readExternal(`in`, pf)
            list.add(item)
        }
        return list
    }

    @JvmStatic
    actual fun writeList(out: PlatformDataOutputStream, list: List<*>) {
        ExtUtil.write(out, ExtWrapList(list))
    }

    @JvmStatic
    actual fun readNullableString(`in`: PlatformDataInputStream, pf: PrototypeFactory): String? {
        @Suppress("UNCHECKED_CAST")
        return ExtUtil.read(`in`, ExtWrapNullable(String::class.java), pf) as String?
    }

    @JvmStatic
    actual fun writeNullable(out: PlatformDataOutputStream, value: Any?) {
        ExtUtil.write(out, ExtWrapNullable(value))
    }

    @JvmStatic
    actual fun arrayEquals(a: Array<Any?>, b: Array<Any?>, unwrap: Boolean): Boolean {
        return ExtUtil.arrayEquals(a, b, unwrap)
    }

    @JvmStatic
    actual fun nullEquals(a: Any?, b: Any?, unwrap: Boolean): Boolean {
        return ExtUtil.equals(a, b, unwrap)
    }
}
