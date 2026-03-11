package org.javarosa.core.model.data

import org.javarosa.core.model.data.helper.Selection
import org.javarosa.core.util.DataUtil
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * A response to a question requesting a selection of
 * any number of items from a list.
 *
 * @author Drew Roos
 */
class SelectMultiData : IAnswerData {
    private var vs: ArrayList<Selection>? = null // vector of Selection

    /**
     * Empty Constructor, necessary for dynamic construction during deserialization.
     * Shouldn't be used otherwise.
     */
    constructor()

    constructor(vs: ArrayList<Selection>) {
        setValue(vs)
    }

    override fun clone(): IAnswerData {
        val v = ArrayList<Selection>()
        for (i in 0 until vs!!.size) {
            v.add(vs!![i].clone())
        }
        return SelectMultiData(v)
    }

    @Suppress("UNCHECKED_CAST")
    override fun setValue(o: Any?) {
        if (o == null) {
            throw NullPointerException("Attempt to set an IAnswerData class to null.")
        }

        // ensure elements are all instances of Selection
        for (elem in o as ArrayList<*>) {
            if (elem !is Selection) {
                throw RuntimeException("$elem is not an instance of Selection")
            }
        }

        vs = ArrayList(o as ArrayList<Selection>)
    }

    override fun getValue(): ArrayList<Selection> {
        return ArrayList(vs)
    }

    override fun getDisplayText(): String {
        var str = ""

        for (i in 0 until vs!!.size) {
            val s = vs!![i]
            str += s.getValue()
            if (i < vs!!.size - 1)
                str += ", "
        }

        return str
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        vs = SerializationHelpers.readList(`in`, pf) { Selection() } as ArrayList<Selection>
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeList(out, vs!!)
    }

    override fun uncast(): UncastData {
        val en = vs!!.iterator()
        val selectString = StringBuilder()

        while (en.hasNext()) {
            val selection = en.next()
            if (selectString.isNotEmpty())
                selectString.append(" ")
            selectString.append(selection.getValue())
        }
        // As Crazy, and stupid, as it sounds, this is the XForms specification
        // for storing multiple selections.
        return UncastData(selectString.toString())
    }

    override fun cast(data: UncastData): SelectMultiData {
        val v = ArrayList<Selection>()
        val choices = DataUtil.splitOnSpaces(data.value!!)
        for (s in choices) {
            v.add(Selection(s))
        }
        return SelectMultiData(v)
    }

    fun isInSelection(value: String): Boolean {
        for (s in vs!!) {
            if (s.getValue() == value) {
                return true
            }
        }
        return false
    }
}
