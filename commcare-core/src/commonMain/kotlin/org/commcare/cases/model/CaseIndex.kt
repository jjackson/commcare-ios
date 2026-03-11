package org.commcare.cases.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.SerializationHelpers
import kotlin.jvm.JvmField

/**
 * A case index represents a link between one case and another. Depending
 * on the case's index relationship it affects which cases will be purged
 * from the phone when the user's scope is updated.
 *
 * @author ctsims
 */
open class CaseIndex : Externalizable {

    @JvmField
    internal var mName: String? = null

    @JvmField
    internal var mTargetId: String? = null

    @JvmField
    internal var mTargetCaseType: String? = null

    @JvmField
    internal var mRelationship: String? = null

    /*
     * serialization only!
     */
    constructor()

    constructor(name: String?, targetCaseType: String?, targetId: String?)
            : this(name, targetCaseType, targetId, RELATIONSHIP_CHILD)

    /**
     * Creates a case index
     *
     * @param name           The name of this index. Used for reference and lookup. A case may only have one
     *                       index with a given name
     * @param targetCaseType The case type of the target case
     * @param targetId       The ID value of the index. Should refer to another case.
     * @param relationship   The relationship between the indexing case and the indexed case. See
     *                       the RELATIONSHIP_* parameters of CaseIndex for more details.
     */
    constructor(name: String?, targetCaseType: String?, targetId: String?, relationship: String?) {
        this.mName = name
        this.mTargetId = targetId
        this.mTargetCaseType = targetCaseType
        this.mRelationship = relationship
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        mName = SerializationHelpers.readString(`in`)
        mTargetId = SerializationHelpers.readString(`in`)
        mTargetCaseType = SerializationHelpers.readString(`in`)
        mRelationship = SerializationHelpers.readString(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeString(out, mName ?: "")
        SerializationHelpers.writeString(out, mTargetId ?: "")
        SerializationHelpers.writeString(out, mTargetCaseType ?: "")
        SerializationHelpers.writeString(out, mRelationship ?: "")
    }

    fun getName(): String? = mName

    fun getTargetType(): String? = mTargetCaseType

    fun getTarget(): String? = mTargetId

    fun getRelationship(): String? = mRelationship

    companion object {
        /**
         * A Child index indicates that this case should ensure
         * that the indexed case is retained in the local scope
         * even if it is closed
         */
        const val RELATIONSHIP_CHILD = "child"

        /**
         * An extension case indicates that if the cases's parent is
         * closed out of the local scope, this case should be released
         * regardless of its status.
         */
        const val RELATIONSHIP_EXTENSION = "extension"
    }
}
