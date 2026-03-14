package org.commcare.suite.model

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * A display unit element contains text and a set of potential image/audio
 * references for menus or other UI elements
 *
 * @author ctsims
 */
class DisplayUnit : Externalizable, DetailTemplate {
    /**
     * A Text which should be displayed to the user as
     * the action which will display this menu.
     */
    var text: Text? = null
        private set
    var imageURI: Text? = null
        private set
    var audioURI: Text? = null
        private set
    var badgeText: Text? = null
        private set
    var hintText: Text? = null
        private set

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
        this.text = name
        this.imageURI = imageReference
        this.audioURI = audioReference
        this.badgeText = badge
        this.hintText = hintText
    }

    fun evaluate(): DisplayData {
        return evaluate(null)
    }

    override fun evaluate(ec: EvaluationContext?): DisplayData {
        val imageRef = imageURI?.evaluate(ec)
        val audioRef = audioURI?.evaluate(ec)
        val textForBadge = badgeText?.evaluate(ec)
        val textForHint = hintText?.evaluate(ec)
        return DisplayData(text!!.evaluate(ec), imageRef, audioRef, textForBadge, textForHint)
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        text = SerializationHelpers.readExternalizable(`in`, pf) { Text() }
        imageURI = SerializationHelpers.readNullableExternalizable(`in`, pf) { Text() }
        audioURI = SerializationHelpers.readNullableExternalizable(`in`, pf) { Text() }
        badgeText = SerializationHelpers.readNullableExternalizable(`in`, pf) { Text() }
        hintText = SerializationHelpers.readNullableExternalizable(`in`, pf) { Text() }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.write(out, text!!)
        SerializationHelpers.writeNullable(out, imageURI)
        SerializationHelpers.writeNullable(out, audioURI)
        SerializationHelpers.writeNullable(out, badgeText)
        SerializationHelpers.writeNullable(out, hintText)
    }
}
