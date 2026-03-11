package org.javarosa.core.util.externalizable

import org.javarosa.core.api.ClassNameHasher
import org.javarosa.core.model.data.UncastData
import org.javarosa.core.model.utils.PlatformDate

/**
 * JVM implementation of PrototypeFactory using Class.forName() + newInstance()
 * for reflection-based class instantiation during deserialization.
 */
open class JvmPrototypeFactory : PrototypeFactory {

    private var classes: ArrayList<Class<*>>? = null
    private var hashes: ArrayList<ByteArray>? = null

    // lazy evaluation
    private var classNames: HashSet<String>?

    constructor() : this(null, null)

    constructor(classNames: HashSet<String>?) {
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

    override fun getInstance(hash: ByteArray): Any {
        return getInstance(getClass(hash)!!)
    }

    protected open fun storeHash(c: Class<*>, hash: ByteArray) {
        classes!!.add(c)
        hashes!!.add(hash)
    }

    companion object {
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
        fun setStaticHasher(staticHasher: Hasher) {
            mStaticHasher = staticHasher
            setClassHashSize(staticHasher.getHashSize())
        }
    }
}
