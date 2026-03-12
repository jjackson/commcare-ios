package org.commcare.suite.model

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.utils.InstanceUtils
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import kotlin.jvm.JvmField

/**
 * Describes a user-initiated action, what information needs to be collected
 * before that action can begin, and what the UI should present to the user
 * regarding this action.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
abstract class Entry : Externalizable, MenuDisplayable {
    @JvmField
    internal var data: ArrayList<SessionDatum>? = null
    @JvmField
    internal var display: DisplayUnit? = null
    // internal for same-package Kotlin callers (SuiteParser uses .commandId)
    internal var commandId: String? = null
        private set
    @JvmField
    internal var instances: MutableMap<String, DataInstance<*>>? = null
    @JvmField
    internal var stackOperations: ArrayList<StackOperation>? = null
    @JvmField
    internal var assertions: AssertionSet? = null

    /**
     * Serialization only!
     */
    constructor()

    constructor(
        commandId: String?, display: DisplayUnit?,
        data: ArrayList<SessionDatum>?,
        instances: HashMap<String, DataInstance<*>>?,
        stackOperations: ArrayList<StackOperation>?,
        assertions: AssertionSet?
    ) {
        this.commandId = if (commandId == null) "" else commandId
        this.display = display
        this.data = data
        this.instances = instances
        this.stackOperations = stackOperations
        this.assertions = assertions
    }

    open fun isView(): Boolean {
        return getXFormNamespace() == null && getPostRequest() == null && stackOperations!!.size == 0
    }

    fun isRemoteRequest(): Boolean {
        return getXFormNamespace() == null && getPostRequest() != null
    }

    open fun getXFormNamespace(): String? = null

    open fun getPostRequest(): PostRequest? = null

    /**
     * @return the ID of this entry command. Used by Menus to determine
     * where the command should be located.
     */
    fun getCommandId(): String? = commandId

    /**
     * @return A text whose evaluated string should be presented to the
     * user as the entry point for this operation
     */
    fun getText(): Text? = display?.getText()

    fun getSessionDataReqs(): ArrayList<SessionDatum>? = data

    fun getInstances(instancesToInclude: Set<String>?): MutableMap<String, DataInstance<*>> {
        return InstanceUtils.getLimitedInstances(instancesToInclude, instances)
    }

    fun getAssertions(): AssertionSet {
        return if (assertions == null) AssertionSet(ArrayList<String>(), ArrayList<Text>()) else assertions!!
    }

    /**
     * Retrieve the stack operations that should be processed after this entry
     * session has successfully completed.
     *
     * @return a ArrayList of ArrayDeque Operation models.
     */
    fun getPostEntrySessionOperations(): ArrayList<StackOperation>? = stackOperations

    override fun getImageURI(): String? {
        val imageUri = display?.getImageURI() ?: return null
        return imageUri.evaluate()
    }

    override fun getAudioURI(): String? {
        val audioUri = display?.getAudioURI() ?: return null
        return audioUri.evaluate()
    }

    override fun getDisplayText(ec: EvaluationContext?): String? {
        val text = display?.getText() ?: return null
        return text.evaluate(ec)
    }

    override fun getTextForBadge(ec: EvaluationContext?): PlatformSingle<String> {
        val badgeText = display?.getBadgeText() ?: return platformSingleJust("")
        return badgeText.getDisposableSingleForEvaluation(ec)
    }

    override fun getRawBadgeTextObject(): Text? = display?.getBadgeText()

    override fun getRawText(): Text? = display?.getText()

    override fun getCommandID(): String? = commandId

    @Suppress("UNCHECKED_CAST")
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        this.commandId = SerializationHelpers.readString(`in`)
        this.display = SerializationHelpers.readExternalizable(`in`, pf) { DisplayUnit() }

        data = SerializationHelpers.readListPoly(`in`, pf) as ArrayList<SessionDatum>
        instances = SerializationHelpers.readStringTaggedMap(`in`, pf) as MutableMap<String, DataInstance<*>>
        stackOperations = SerializationHelpers.readList(`in`, pf) { StackOperation() }
        assertions = SerializationHelpers.readNullableExternalizable(`in`, pf) { AssertionSet() }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeString(out, commandId ?: "")
        SerializationHelpers.write(out, display!!)
        SerializationHelpers.writeListPoly(out, data!!)
        SerializationHelpers.writeTaggedMap(out, instances!! as MutableMap<*, *>)
        SerializationHelpers.writeList(out, stackOperations!!)
        SerializationHelpers.writeNullable(out, assertions)
    }

    override fun toString(): String {
        return "Entry with id " + this.getCommandId()
    }
}
