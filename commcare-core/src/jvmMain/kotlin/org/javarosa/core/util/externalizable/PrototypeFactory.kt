@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util.externalizable

import org.javarosa.core.api.ClassNameHasher
import org.javarosa.core.model.data.UncastData
import org.javarosa.core.model.utils.PlatformDate
import java.util.HashSet
import kotlin.reflect.KClass

/**
 * ProtoType factory for serializing and deserializing persisted classes using
 * their hash codes. To use a non-default hasher, use one of the overriding constructors
 * or call setStaticHasher().
 */
actual open class PrototypeFactory : Any {

    private var classes: ArrayList<Class<*>>? = null
    private var hashes: ArrayList<ByteArray>? = null

    // lazy evaluation
    private var classNames: HashSet<String>?

    @JvmField
    protected var initialized: Boolean

    actual constructor() {
        this.classNames = null
        initialized = false
        if (mStaticHasher == null) {
            mStaticHasher = ClassNameHasher()
        }
    }

    actual constructor(classNames: HashSet<String>) {
        this.classNames = classNames
        initialized = false
        if (mStaticHasher == null) {
            mStaticHasher = ClassNameHasher()
        }
    }

    constructor(hasher: Hasher?) : this(hasher, null)

    constructor(hasher: Hasher?, classNames: HashSet<String>?) {
        this.classNames = classNames
        initialized = false
        if (mStaticHasher == null) {
            if (hasher == null) {
                mStaticHasher = ClassNameHasher()
            } else {
                setStaticHasher(hasher)
            }
        }
    }

    protected open fun lazyInit() {
        initialized = true

        classes = ArrayList()
        hashes = ArrayList()

        addDefaultClasses()
        addMigratedClasses()

        if (classNames != null) {
            for (name in classNames!!) {
                try {
                    addClass(Class.forName(name))
                } catch (cnfe: ClassNotFoundException) {
                    throw CannotCreateObjectException("$name: not found")
                }
            }
            classNames = null
        }
    }

    /**
     * Override to provide migration logic; needed if classes are renamed,
     * since classes in prototype factory are indexed by classname
     */
    protected open fun addMigratedClasses() {
    }

    private fun addDefaultClasses() {
        val baseTypes = arrayOf<Class<*>>(
            Any::class.java,
            Int::class.javaObjectType,
            Long::class.javaObjectType,
            Short::class.javaObjectType,
            Byte::class.javaObjectType,
            Char::class.javaObjectType,
            Boolean::class.javaObjectType,
            Float::class.javaObjectType,
            Double::class.javaObjectType,
            String::class.java,
            PlatformDate::class.java,
            UncastData::class.java
        )

        for (baseType in baseTypes) {
            addClass(baseType)
        }
    }

    open fun addClass(c: Class<*>) {
        if (!initialized) {
            lazyInit()
        }

        val hash = getClassHash(c)

        if (compareHash(hash, getWrapperTag())) {
            throw Error("Hash collision! " + c.name + " and reserved wrapper tag")
        }

        val d = getClass(hash)
        if (d != null && d != c) {
            throw Error("Hash collision! " + c.name + " and " + d.name)
        }
        storeHash(c, hash)
    }

    open fun getClass(hash: ByteArray): Class<*>? {
        if (!initialized) {
            lazyInit()
        }

        for (i in 0 until classes!!.size) {
            if (compareHash(hash, hashes!![i])) {
                return classes!![i]
            }
        }

        return null
    }

    actual open fun getClassName(hash: ByteArray): String? {
        return getClass(hash)?.name
    }

    actual open fun createInstance(type: KClass<*>): Any {
        return getInstance(type.java)
    }

    actual open fun getInstance(hash: ByteArray): Any {
        return getInstance(getClass(hash)!!)
    }

    protected open fun storeHash(c: Class<*>, hash: ByteArray) {
        classes!!.add(c)
        hashes!!.add(hash)
    }

    actual companion object {
        private var mStaticHasher: Hasher? = null

        @JvmStatic
        fun getInstance(c: Class<*>): Any {
            try {
                return c.newInstance()
            } catch (iae: IllegalAccessException) {
                throw CannotCreateObjectException(c.name + ": not accessible or no empty constructor")
            } catch (e: InstantiationException) {
                throw CannotCreateObjectException(c.name + ": not instantiable")
            }
        }

        @JvmStatic
        fun getClassHash(type: Class<*>): ByteArray {
            return mStaticHasher!!.getClassHashValue(type)
        }

        @JvmStatic
        actual fun compareHash(a: ByteArray, b: ByteArray): Boolean {
            if (a.size != b.size) {
                return false
            }

            for (i in a.indices) {
                if (a[i] != b[i]) {
                    return false
                }
            }

            return true
        }

        @JvmStatic
        fun setStaticHasher(staticHasher: Hasher) {
            mStaticHasher = staticHasher
        }

        @JvmStatic
        actual fun getClassHashSize(): Int {
            return mStaticHasher!!.getHashSize()
        }

        @JvmStatic
        actual fun getClassHashByName(className: String): ByteArray {
            return mStaticHasher!!.getClassHashValueByName(className)
        }

        @JvmStatic
        actual fun getClassHashForType(type: KClass<*>): ByteArray {
            return getClassHash(type.java)
        }

        @JvmStatic
        actual fun getWrapperTag(): ByteArray {
            val bytes = ByteArray(getClassHashSize())
            for (i in bytes.indices) {
                bytes[i] = 0xff.toByte()
            }
            return bytes
        }
    }
}
