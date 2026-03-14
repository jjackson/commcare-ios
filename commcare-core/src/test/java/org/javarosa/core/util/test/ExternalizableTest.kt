package org.javarosa.core.util.test

import org.commcare.cases.model.Case
import org.javarosa.core.api.ClassNameHasher
import org.javarosa.core.services.storage.util.DummyIndexedStorageUtility
import org.javarosa.core.util.ListMultimap
import org.javarosa.core.util.OrderedHashtable
import org.javarosa.core.util.externalizable.*
import org.junit.Assert.fail
import org.junit.Test
import java.util.*

class ExternalizableTest {

    companion object {
        @JvmStatic
        fun testExternalizable(orig: Any, template: Any, pf: PrototypeFactory?, failMessage: String?) {
            val msg = failMessage ?: "Serialization Failure"

            print("")
            print("Original: " + printObj(orig))

            try {
                val bytes = ExtUtil.serialize(orig)

                print("Serialized as:")
                print(ExtUtil.printBytes(bytes))

                val deser: Any? = when (template) {
                    is Class<*> -> ExtUtilJvm.deserialize(bytes, template, pf)
                    is ExternalizableWrapper -> ExtUtil.read(PlatformDataInputStream(bytes), template, pf)
                    else -> throw ClassCastException()
                }

                print("Reconstituted: " + printObj(deser))

                if (ExtUtil.equals(orig, deser, true)) {
                    print("SUCCESS")
                } else {
                    print("FAILURE")
                    fail("$msg: Objects do not match")
                }
                print("---------------------------------------------")
            } catch (e: Exception) {
                e.printStackTrace()
                fail("$msg: Exception! ${e.javaClass.name} ${e.message}")
            }
        }

        @JvmStatic
        fun testExternalizable(original: Externalizable, pf: PrototypeFactory, failMessage: String) {
            testExternalizable(original, original.javaClass, pf, failMessage)
        }

        @JvmStatic
        fun printObj(o: Any?): String {
            val unwrapped = ExtUtil.unwrap(o)

            return when {
                unwrapped == null -> "(null)"
                unwrapped is ArrayList<*> -> {
                    val sb = StringBuffer()
                    sb.append("V[")
                    val iter = unwrapped.iterator()
                    while (iter.hasNext()) {
                        sb.append(printObj(iter.next()))
                        if (iter.hasNext()) sb.append(", ")
                    }
                    sb.append("]")
                    sb.toString()
                }
                unwrapped is HashMap<*, *> -> {
                    val sb = StringBuffer()
                    sb.append(if (unwrapped is OrderedHashtable<*, *>) "oH" else "H").append("[")
                    val iter = unwrapped.keys.iterator()
                    while (iter.hasNext()) {
                        val key = iter.next()
                        sb.append(printObj(key))
                        sb.append("=>")
                        sb.append(printObj(unwrapped[key]))
                        if (iter.hasNext()) sb.append(", ")
                    }
                    sb.append("]")
                    sb.toString()
                }
                else -> "{${unwrapped.javaClass.name}:$unwrapped}"
            }
        }

        private fun print(s: String) {
            //#if javarosa.dev.serializationtest.verbose
            println(s)
            //#endif
        }

        private fun buildLargeString(): String {
            val sb = StringBuilder()
            val overMax = (Short.MAX_VALUE.toInt() * 2) + 100
            for (i in 0 until overMax) {
                sb.append("z")
            }
            return sb.toString()
        }
    }

    // for use inside this test suite
    fun testExternalizable(orig: Any, template: Any) {
        testExternalizable(orig, template, null)
    }

    fun testExternalizable(orig: Any, template: Any, pf: PrototypeFactory?) {
        Companion.testExternalizable(orig, template, pf, null)
    }

    @Test
    fun doTests() {
        val pf = PrototypeFactory()
        PrototypeFactory.setStaticHasher(ClassNameHasher())

        testExternalizable("string", String::class.java)
        testExternalizable(java.lang.Byte.valueOf(0.toByte()), java.lang.Byte::class.java)
        testExternalizable(java.lang.Byte.valueOf(0x55.toByte()), java.lang.Byte::class.java)
        testExternalizable(java.lang.Byte.valueOf(0xe9.toByte()), java.lang.Byte::class.java)
        testExternalizable(java.lang.Short.valueOf(0.toShort()), java.lang.Short::class.java)
        testExternalizable(java.lang.Short.valueOf((-12345).toShort()), java.lang.Short::class.java)
        testExternalizable(java.lang.Short.valueOf(12345.toShort()), java.lang.Short::class.java)
        testExternalizable(Integer.valueOf(0), Integer::class.java)
        testExternalizable(Integer.valueOf(1234567890), Integer::class.java)
        testExternalizable(Integer.valueOf(-1234567890), Integer::class.java)
        testExternalizable(java.lang.Long.valueOf(0), java.lang.Long::class.java)
        testExternalizable(java.lang.Long.valueOf(1234567890123456789L), java.lang.Long::class.java)
        testExternalizable(java.lang.Long.valueOf(-1234567890123456789L), java.lang.Long::class.java)
        testExternalizable(java.lang.Boolean.TRUE, java.lang.Boolean::class.java)
        testExternalizable(java.lang.Boolean.FALSE, java.lang.Boolean::class.java)
        testExternalizable(Character.valueOf('e'), Character::class.java)
        testExternalizable(java.lang.Float.valueOf(123.45e6f), java.lang.Float::class.java)
        testExternalizable(java.lang.Double.valueOf(123.45e6), java.lang.Double::class.java)
        testExternalizable(Date(), Date::class.java)
        testExternalizable(SampleExtz("your", "mom"), SampleExtz::class.java)

        testExternalizable("string", ExtWrapBase(String::class.java))
        testExternalizable(ExtWrapBase("string"), String::class.java)

        testExternalizable(ExtWrapNullable(null as String?), ExtWrapNullable(String::class))
        testExternalizable(ExtWrapNullable("string"), ExtWrapNullable(String::class))
        testExternalizable(ExtWrapNullable(null as Integer?), ExtWrapNullable(Integer::class))
        testExternalizable(ExtWrapNullable(Integer.valueOf(17)), ExtWrapNullable(Integer::class))
        testExternalizable(ExtWrapNullable(null as SampleExtz?), ExtWrapNullable(SampleExtz::class))
        testExternalizable(ExtWrapNullable(SampleExtz("hi", "there")), ExtWrapNullable(SampleExtz::class))

        val v = ArrayList<Any>()
        v.add(Integer.valueOf(27))
        v.add(Integer.valueOf(-73))
        v.add(Integer.valueOf(1024))
        v.add(Integer.valueOf(66066066))
        testExternalizable(ExtWrapList(v), ExtWrapList(Integer::class.java))

        val vs = ArrayList<Any>()
        vs.add("alpha")
        vs.add("beta")
        vs.add("gamma")
        testExternalizable(ExtWrapList(vs), ExtWrapList(String::class.java))

        val w = ArrayList<Any>()
        w.add(SampleExtz("where", "is"))
        w.add(SampleExtz("the", "beef"))
        testExternalizable(ExtWrapList(w), ExtWrapList(SampleExtz::class.java))

        testExternalizable(ExtWrapNullable(ExtWrapList(v)), ExtWrapNullable(ExtWrapList(Integer::class) as ExternalizableWrapper?))
        testExternalizable(ExtWrapNullable(null as ExtWrapList?), ExtWrapNullable(ExtWrapList(Integer::class) as ExternalizableWrapper?))
        testExternalizable(ExtWrapList(v, ExtWrapNullable()), ExtWrapList(ExtWrapNullable(Integer::class) as ExternalizableWrapper))

        testExternalizable(ExtWrapList(ArrayList<Any>()), ExtWrapList(String::class.java))
        testExternalizable(ExtWrapList(ArrayList<Any>(), ExtWrapBase(Integer::class.java)), ExtWrapList(String::class.java))

        val x = ArrayList<Any>()
        x.add(Integer.valueOf(-35))
        x.add(Integer.valueOf(-31415926))
        val y = ArrayList<Any>()
        y.add(v)
        y.add(x)
        y.add(ArrayList<Any>())
        testExternalizable(ExtWrapList(y, ExtWrapList()), ExtWrapList(ExtWrapList(Integer::class.java)))
        testExternalizable(ExtWrapList(ArrayList<Any>(), ExtWrapList()), ExtWrapList(ExtWrapList(Integer::class.java)))

        testExternalizable(ExtWrapTagged("string"), ExtWrapTagged())
        testExternalizable(ExtWrapTagged(Integer.valueOf(5000)), ExtWrapTagged())
        pf.addClass(SampleExtz::class.java)
        testExternalizable(ExtWrapTagged(SampleExtz("bon", "jovi")), ExtWrapTagged(), pf)
        testExternalizable(ExtWrapTagged(ExtWrapList(v)), ExtWrapTagged(), pf)
        testExternalizable(ExtWrapTagged(ExtWrapList(w)), ExtWrapTagged(), pf)
        testExternalizable(ExtWrapTagged(ExtWrapNullable("string")), ExtWrapTagged())
        testExternalizable(ExtWrapTagged(ExtWrapNullable(null as String?)), ExtWrapTagged())
        testExternalizable(ExtWrapTagged(ExtWrapList(y, ExtWrapList(Integer::class.java))), ExtWrapTagged())
        testExternalizable(ExtWrapTagged(ExtWrapList(ArrayList<Any>(), ExtWrapList(Integer::class.java))), ExtWrapTagged())

        val a = ArrayList<Any>()
        a.add(Integer.valueOf(47))
        a.add("string")
        a.add(java.lang.Boolean.FALSE)
        a.add(SampleExtz("hello", "dolly"))
        testExternalizable(ExtWrapListPoly(a), ExtWrapListPoly(), pf)
        testExternalizable(ExtWrapTagged(ExtWrapListPoly(a)), ExtWrapTagged(), pf)
        a.add(ExtWrapList(y, ExtWrapList(Integer::class.java)))
        testExternalizable(ExtWrapListPoly(a), ExtWrapListPoly(), pf)
        testExternalizable(ExtWrapListPoly(ArrayList<Any>()), ExtWrapListPoly())

        val oh = OrderedHashtable<Any, Any>()
        testExternalizable(ExtWrapMap(oh), ExtWrapMap(String::class.java, Integer::class.java, ExtWrapMap.TYPE_ORDERED))
        testExternalizable(ExtWrapMapPoly(oh), ExtWrapMapPoly(Date::class.java, true))
        testExternalizable(ExtWrapTagged(ExtWrapMap(oh)), ExtWrapTagged())
        testExternalizable(ExtWrapTagged(ExtWrapMapPoly(oh)), ExtWrapTagged())
        oh.put("key1", SampleExtz("a", "b"))
        oh.put("key2", SampleExtz("c", "d"))
        oh.put("key3", SampleExtz("e", "f"))
        testExternalizable(ExtWrapMap(oh), ExtWrapMap(String::class.java, SampleExtz::class.java, ExtWrapMap.TYPE_ORDERED), pf)
        testExternalizable(ExtWrapTagged(ExtWrapMap(oh)), ExtWrapTagged(), pf)

        val h = HashMap<Any, Any>()
        testExternalizable(ExtWrapMap(h), ExtWrapMap(String::class.java, Integer::class.java))
        testExternalizable(ExtWrapMapPoly(h), ExtWrapMapPoly(Date::class.java))
        testExternalizable(ExtWrapTagged(ExtWrapMap(h)), ExtWrapTagged())
        testExternalizable(ExtWrapTagged(ExtWrapMapPoly(h)), ExtWrapTagged())
        h["key1"] = SampleExtz("e", "f")
        h["key2"] = SampleExtz("c", "d")
        h["key3"] = SampleExtz("a", "b")
        testExternalizable(ExtWrapMap(h), ExtWrapMap(String::class.java, SampleExtz::class.java), pf)
        testExternalizable(ExtWrapTagged(ExtWrapMap(h)), ExtWrapTagged(), pf)

        val j = HashMap<Any, Any>()
        j[Integer.valueOf(17)] = h
        j[Integer.valueOf(-3)] = h
        val k = HashMap<Any, Any>()
        k["key"] = j
        testExternalizable(ExtWrapMap(k, ExtWrapMap(Integer::class.java, ExtWrapMap(String::class.java, SampleExtz::class.java))),
                ExtWrapMap(String::class.java, ExtWrapMap(Integer::class.java, ExtWrapMap(String::class.java, SampleExtz::class.java))), pf)

        val m = OrderedHashtable<Any, Any>()
        m.put("a", "b")
        m.put("b", Integer.valueOf(17))
        m.put("c", java.lang.Short.valueOf((-443).toShort()))
        m.put("d", SampleExtz("boris", "yeltsin"))
        m.put("e", ExtWrapList(vs))
        testExternalizable(ExtWrapMapPoly(m), ExtWrapMapPoly(String::class.java, true), pf)

        val multimap = ListMultimap.create<String, SampleExtz>()
        testExternalizable(ExtWrapMultiMap(multimap), ExtWrapMultiMap(String::class.java))
        testExternalizable(ExtWrapTagged(ExtWrapMultiMap(multimap)), ExtWrapTagged())
        multimap.put("key1", SampleExtz("a", "b"))
        multimap.put("key1", SampleExtz("c", "d"))
        multimap.put("key2", SampleExtz("e", "f"))
        testExternalizable(ExtWrapMultiMap(multimap), ExtWrapMultiMap(String::class.java), pf)

        val hashMultimap = ListMultimap.create<String, SampleExtz>()
        testExternalizable(ExtWrapMultiMap(hashMultimap), ExtWrapMultiMap(String::class.java))
        testExternalizable(ExtWrapTagged(ExtWrapMultiMap(hashMultimap)), ExtWrapTagged())
        hashMultimap.put("key1", SampleExtz("a", "b"))
        hashMultimap.put("key1", SampleExtz("c", "d"))
        hashMultimap.put("key2", SampleExtz("e", "f"))
        testExternalizable(ExtWrapMultiMap(hashMultimap), ExtWrapMultiMap(String::class.java), pf)
    }

    @Test(expected = SerializationLimitationException::class)
    fun stringSerializationLimitationTest() {
        val storage = DummyIndexedStorageUtility<Case>(Case::class.java, LivePrototypeFactory())

        val caseWithLongStringProp = Case("123", "a")
        caseWithLongStringProp.setCaseId("foo")
        val longString = buildLargeString()
        caseWithLongStringProp.setProperty("too_long", longString)

        storage.write(caseWithLongStringProp)
        storage.read(0)
    }

}
