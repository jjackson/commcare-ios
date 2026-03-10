package org.javarosa.core.model.data

import org.javarosa.core.model.data.helper.Selection
import org.javarosa.core.util.DataUtil
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.Vector

/**
 * A response to a question requesting a selection of
 * any number of items from a list.
 *
 * @author Drew Roos
 */
class SelectMultiData : IAnswerData {
    private var vs: Vector<Selection>? = null // vector of Selection

    /**
     * Empty Constructor, necessary for dynamic construction during deserialization.
     * Shouldn't be used otherwise.
     */
    constructor()

    constructor(vs: Vector<Selection>) {
        setValue(vs)
    }

    override fun clone(): IAnswerData {
        val v = Vector<Selection>()
        for (i in 0 until vs!!.size) {
            v.addElement(vs!!.elementAt(i).clone())
        }
        return SelectMultiData(v)
    }

    @Suppress("UNCHECKED_CAST")
    override fun setValue(o: Any?) {
        if (o == null) {
            throw NullPointerException("Attempt to set an IAnswerData class to null.")
        }

        // ensure elements are all instances of Selection
        for (elem in o as Vector<*>) {
            if (elem !is Selection) {
                throw RuntimeException("$elem is not an instance of Selection")
            }
        }

        vs = Vector(o as Vector<Selection>)
    }

    override fun getValue(): Vector<Selection> {
        return Vector(vs)
    }

    override fun getDisplayText(): String {
        var str = ""

        for (i in 0 until vs!!.size) {
            val s = vs!!.elementAt(i)
            str += s.getValue()
            if (i < vs!!.size - 1)
                str += ", "
        }

        return str
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        vs = ExtUtil.read(`in`, ExtWrapList(Selection::class.java), pf) as Vector<Selection>
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.write(out, ExtWrapList(vs!!))
    }

    override fun uncast(): UncastData {
        val en = vs!!.elements()
        val selectString = StringBuffer()

        while (en.hasMoreElements()) {
            val selection = en.nextElement()
            if (selectString.isNotEmpty())
                selectString.append(" ")
            selectString.append(selection.getValue())
        }
        // As Crazy, and stupid, as it sounds, this is the XForms specification
        // for storing multiple selections.
        return UncastData(selectString.toString())
    }

    override fun cast(data: UncastData): SelectMultiData {
        val v = Vector<Selection>()
        val choices = DataUtil.splitOnSpaces(data.value!!)
        for (s in choices) {
            v.addElement(Selection(s))
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
