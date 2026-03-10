package org.commcare.core.graph.suite

import org.commcare.suite.model.Text
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Definition of an annotation, which is text drawn at a specified x, y coordinate on a graph.
 *
 * @author jschweers
 */
class Annotation : Externalizable {
    private var mX: Text? = null
    private var mY: Text? = null
    private var mAnnotation: Text? = null

    constructor()

    constructor(x: Text, y: Text, annotation: Text) {
        mX = x
        mY = y
        mAnnotation = annotation
    }

    fun getX(): Text? = mX
    fun getY(): Text? = mY
    fun getAnnotation(): Text? = mAnnotation

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        mX = ExtUtil.read(`in`, Text::class.java, pf) as Text
        mY = ExtUtil.read(`in`, Text::class.java, pf) as Text
        mAnnotation = ExtUtil.read(`in`, Text::class.java, pf) as Text
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.write(out, mX)
        ExtUtil.write(out, mY)
        ExtUtil.write(out, mAnnotation)
    }
}
