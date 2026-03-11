package org.commcare.suite.model

import org.commcare.xml.SessionDatumParser.Companion.DEFAULT_MAX_SELECT_VAL
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.SerializationHelpers

/**
 * Special kind of EntityDatum that allows for selection of multiple entities in the session
 */
class MultiSelectEntityDatum : EntityDatum {
    private var maxSelectValue: Int = DEFAULT_MAX_SELECT_VAL

    constructor()

    constructor(
        id: String?, nodeset: String?, shortDetail: String?, longDetail: String?,
        inlineDetail: String?, persistentDetail: String?, value: String?, autoselect: String?,
        maxSelectValue: Int
    ) : super(id, nodeset, shortDetail, longDetail, inlineDetail, persistentDetail, value, autoselect) {
        this.maxSelectValue = maxSelectValue
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        super.readExternal(`in`, pf)
        maxSelectValue = SerializationHelpers.readInt(`in`)

        // Set the correct default here in case the serialised state has
        // the incorrect older default of -1. This is a temporary work-around
        // and should be safe to removed ~1 month after it gets deployed
        if (maxSelectValue == -1) {
            maxSelectValue = DEFAULT_MAX_SELECT_VAL
        }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        super.writeExternal(out)
        SerializationHelpers.writeNumeric(out, maxSelectValue.toLong())
    }

    fun getMaxSelectValue(): Int = maxSelectValue
}
