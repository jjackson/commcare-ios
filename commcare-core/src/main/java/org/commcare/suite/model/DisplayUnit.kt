package org.commcare.suite.model

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * A display unit element contains text and a set of potential image/audio
 * references for menus or other UI elements
 *
 * @author ctsims
 */
class DisplayUnit : Externalizable, DetailTemplate {
    private var name: Text? = null
    private var imageReference: Text? = null
    private var audioReference: Text? = null
    private var badgeFunction: Text? = null
    private var hintText: Text? = null

    /**
     * Serialization only!!!
     */
    constructor()

    constructor(name: Text?) : this(name, null, null, null, null)

    constructor(
        name: Text?,
        imageReference: Text?,
        audioReference: Text?,
        badge: Text?,
        hintText: Text?
    ) {
        this.name = name
        this.imageReference = imageReference
        this.audioReference = audioReference
        this.badgeFunction = badge
        this.hintText = hintText
    }

    fun evaluate(): DisplayData {
        return evaluate(null)
    }

    override fun evaluate(ec: EvaluationContext?): DisplayData {
        val imageRef = imageReference?.evaluate(ec)
        val audioRef = audioReference?.evaluate(ec)
        val textForBadge = badgeFunction?.evaluate(ec)
        val textForHint = hintText?.evaluate(ec)
        return DisplayData(name!!.evaluate(ec), imageRef, audioRef, textForBadge, textForHint)
    }

    /**
     * @return A Text which should be displayed to the user as
     * the action which will display this menu.
     */
    fun getText(): Text? = name

    fun getImageURI(): Text? = imageReference

    fun getAudioURI(): Text? = audioReference

    fun getBadgeText(): Text? = badgeFunction

    fun getHintText(): Text? = hintText

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        name = ExtUtil.read(`in`, Text::class.java, pf) as Text
        imageReference = ExtUtil.read(`in`, ExtWrapNullable(Text::class.java), pf) as Text?
        audioReference = ExtUtil.read(`in`, ExtWrapNullable(Text::class.java), pf) as Text?
        badgeFunction = ExtUtil.read(`in`, ExtWrapNullable(Text::class.java), pf) as Text?
        hintText = ExtUtil.read(`in`, ExtWrapNullable(Text::class.java), pf) as Text?
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.write(out, name)
        ExtUtil.write(out, ExtWrapNullable(imageReference))
        ExtUtil.write(out, ExtWrapNullable(audioReference))
        ExtUtil.write(out, ExtWrapNullable(badgeFunction))
        ExtUtil.write(out, ExtWrapNullable(hintText))
    }
}
