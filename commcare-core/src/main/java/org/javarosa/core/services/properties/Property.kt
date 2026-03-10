package org.javarosa.core.services.properties

import org.javarosa.core.services.storage.IMetaData
import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.externalizable.PrototypeFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.Vector

/**
 * Property is an encapsulation of a record containing a property in the J2ME
 * RMS persistent storage system. It is responsible for serializing a name
 * value pair.
 *
 * @author ctsims
 */
class Property : Persistable, IMetaData {
    @JvmField
    var name: String = ""

    @JvmField
    var value: Vector<String> = Vector()

    @JvmField
    var recordId: Int = -1

    @Throws(PlatformIOException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        var fullString = ""

        val inputarray = ByteArray(`in`.available())
        `in`.readFully(inputarray)

        for (i in inputarray.indices) {
            val c = inputarray[i].toInt().toChar()
            fullString = fullString + c
        }
        val nameindex = fullString.indexOf(",")
        value = Vector()
        if (nameindex == -1) {
            System.out.println("WARNING: Property in RMS with no value:$fullString")
            name = fullString.substring(0, fullString.length)
        } else {
            name = fullString.substring(0, nameindex)
            // The format of the properties should be each one in a list, comma delimited
            var packedvalue = fullString.substring(fullString.indexOf(",") + 1, fullString.length)
            while (packedvalue.isNotEmpty()) {
                val index = packedvalue.indexOf(",")
                if (index == -1) {
                    value.addElement(packedvalue)
                    packedvalue = ""
                } else {
                    value.addElement(packedvalue.substring(0, index))
                    packedvalue = packedvalue.substring(index + 1, packedvalue.length)
                }
            }
        }
        `in`.close()
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        var outputString = name
        // Note that this enumeration should contain at least one element, otherwise the
        // deserialization is invalid
        val en = value.elements()
        while (en.hasMoreElements()) {
            outputString += "," + en.nextElement()
        }

        for (i in outputString.indices) {
            out.writeByte(outputString[i].code.toByte().toInt())
        }
        out.close()
    }

    override fun setID(recordId: Int) {
        this.recordId = recordId
    }

    override fun getID(): Int {
        return recordId
    }

    override fun getMetaData(fieldName: String): Any {
        if (fieldName == "NAME") {
            return name
        } else {
            throw IllegalArgumentException()
        }
    }

    override fun getMetaDataFields(): Array<String> {
        return arrayOf("NAME")
    }
}
