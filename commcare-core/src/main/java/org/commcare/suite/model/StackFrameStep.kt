package org.commcare.suite.model

import org.javarosa.core.util.ListMultimap

import org.commcare.core.interfaces.RemoteInstanceFetcher
import org.commcare.session.SessionFrame
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.ExternalDataInstance
import org.javarosa.core.model.instance.ExternalDataInstanceSource
import org.javarosa.core.model.instance.InstanceInitializationFactory
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.core.util.externalizable.emptyIfNull
import org.javarosa.core.util.externalizable.nullIfEmpty
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * @author ctsims
 */
class StackFrameStep : Externalizable {
    // Share the types with the commands
    private var elementType: String? = null
    private var _id: String? = null
    private var _value: String? = null
    private var valueIsXpath: Boolean = false
    private var extras: ListMultimap<String, Any> = ListMultimap()

    /**
     * XML instances collected during session navigation that is made available
     * in the session's evaluation context. For instance, useful to store
     * results of a query command during case search and claim workflow
     */
    private var dataInstanceSources: HashMap<String, ExternalDataInstanceSource> = HashMap()

    /**
     * Serialization Only
     */
    constructor()

    /**
     * Copy constructor
     */
    constructor(oldStackFrameStep: StackFrameStep) {
        this.elementType = oldStackFrameStep.elementType
        this._id = oldStackFrameStep._id
        this._value = oldStackFrameStep._value
        this.valueIsXpath = oldStackFrameStep.valueIsXpath
        extras.putAll(oldStackFrameStep.getExtras())
        this.dataInstanceSources.putAll(oldStackFrameStep.dataInstanceSources)
    }

    constructor(type: String?, id: String?, value: String?) {
        this.elementType = type
        this._id = id
        this._value = value
    }

    @Throws(XPathSyntaxException::class)
    constructor(type: String?, id: String?, value: String?, valueIsXpath: Boolean) : this(type, id, value) {
        this.valueIsXpath = valueIsXpath
        if (valueIsXpath) {
            // Run the parser to ensure that we will fail fast when _creating_ the step, not when
            // running it
            XPathParseTool.parseXPath(value!!)
        }
    }

    fun getType(): String? = elementType

    fun getId(): String? = _id

    fun getValue(): String? = _value

    fun addDataInstanceSource(source: ExternalDataInstanceSource) {
        requireNotNull(source) {
            "Unable to add null instance data source to stack frame step '${getId()}'"
        }
        val reference = source.getReference()
        if (dataInstanceSources.containsKey(reference)) {
            throw RuntimeException(
                "ArrayDeque frame step '${getId()}' already contains an instance with the reference '$reference'"
            )
        }
        dataInstanceSources[reference!!] = source
    }

    fun getDataInstanceSources(): HashMap<String, ExternalDataInstanceSource> = dataInstanceSources

    fun hasDataInstanceSource(reference: String?): Boolean = dataInstanceSources.containsKey(reference)

    fun getDataInstanceSource(reference: String?): ExternalDataInstanceSource? = dataInstanceSources[reference]

    fun getDataInstanceSourceById(instanceId: String): ExternalDataInstanceSource? {
        for (source in dataInstanceSources.values) {
            if (source.getInstanceId() == instanceId) {
                return source
            }
        }
        return null
    }

    @Throws(RemoteInstanceFetcher.RemoteInstanceException::class)
    fun initDataInstanceSources(remoteInstanceFetcher: RemoteInstanceFetcher?) {
        if (remoteInstanceFetcher == null) return
        for (source in dataInstanceSources.values) {
            if (source.needsInit()) {
                source.remoteInit(remoteInstanceFetcher, getId())
            }
        }
    }

    fun getInstances(iif: InstanceInitializationFactory): Map<String, DataInstance<*>> {
        val instances = HashMap<String, DataInstance<*>>()
        for (source in dataInstanceSources.values) {
            val instance = source.toInstance()
                .initialize(iif, source.getInstanceId()) as ExternalDataInstance
            instances[instance.getInstanceId()!!] = instance
        }
        return instances
    }

    /**
     * @param value Must extend Externalizable or be a basic data type (String, ArrayList, etc)
     */
    fun addExtra(key: String, value: Any?) {
        if (value != null) {
            extras.put(key, value)
        }
    }

    /**
     * Remove all extras for the given key
     *
     * @param key key we want to remove from extras
     */
    fun removeExtra(key: String) {
        extras.removeAll(key)
    }

    fun getExtra(key: String): Any? {
        val values = extras.get(key)
        if (values.size > 1) {
            throw RuntimeException("Multiple extras found with key $key")
        }
        return try {
            values.iterator().next()
        } catch (e: NoSuchElementException) {
            null
        }
    }

    fun getExtras(): ListMultimap<String, Any> = extras

    /**
     * Get a performed step to pass on to an actual frame
     *
     * @param ec          Context to evaluate any parameters with
     * @param neededDatum The current datum needed by the session, used by
     *                    'mark' to know what datum to set in a 'rewind'
     * @return A step that can be added to a session frame
     */
    fun defineStep(ec: EvaluationContext, neededDatum: SessionDatum?): StackFrameStep {
        return when (elementType) {
            SessionFrame.STATE_DATUM_VAL ->
                StackFrameStep(SessionFrame.STATE_DATUM_VAL, _id, evaluateValue(ec))
            SessionFrame.STATE_MULTIPLE_DATUM_VAL ->
                StackFrameStep(SessionFrame.STATE_MULTIPLE_DATUM_VAL, _id, evaluateValue(ec))
            SessionFrame.STATE_COMMAND_ID ->
                StackFrameStep(SessionFrame.STATE_COMMAND_ID, evaluateValue(ec), null)
            SessionFrame.STATE_UNKNOWN ->
                StackFrameStep(SessionFrame.STATE_UNKNOWN, _id, evaluateValue(ec))
            SessionFrame.STATE_REWIND ->
                StackFrameStep(SessionFrame.STATE_REWIND, null, evaluateValue(ec))
            SessionFrame.STATE_MARK -> {
                if (neededDatum == null) {
                    throw RuntimeException("Can't add a mark in a place where there is no needed datum")
                }
                StackFrameStep(SessionFrame.STATE_MARK, neededDatum.getDataId(), null)
            }
            SessionFrame.STATE_FORM_XMLNS ->
                throw RuntimeException("Form Definitions in Steps are not yet supported!")
            SessionFrame.STATE_QUERY_REQUEST, SessionFrame.STATE_SMART_LINK -> {
                val defined = StackFrameStep(elementType, _id, evaluateValue(ec))
                extras.forEach { key, value ->
                    if (value is QueryData) {
                        defined.getExtras().putAll(key, value.getValues(ec))
                    } else if (value is XPathExpression) {
                        // only to maintain backward compatibility with old serialised app db state, can be removed in
                        // subsequent deploys
                        defined.addExtra(key, FunctionUtils.toString(value.eval(ec)))
                    } else {
                        throw RuntimeException("Invalid data type for step extra $key")
                    }
                }
                defined
            }
            else -> throw RuntimeException("Invalid step [$elementType] declared when constructing a new frame step")
        }
    }

    fun evaluateValue(ec: EvaluationContext): String? {
        return if (!valueIsXpath) {
            _value
        } else {
            try {
                FunctionUtils.toString(XPathParseTool.parseXPath(_value!!)!!.eval(ec))
            } catch (e: XPathSyntaxException) {
                // This error makes no sense, since we parse the input for
                // validation when we create it!
                throw XPathException(e.message)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        this.elementType = SerializationHelpers.readString(`in`)
        this._id = nullIfEmpty(SerializationHelpers.readString(`in`))
        this._value = nullIfEmpty(SerializationHelpers.readString(`in`))
        this.valueIsXpath = SerializationHelpers.readBool(`in`)
        this.extras = SerializationHelpers.readStringMultiMap(`in`, pf) as ListMultimap<String, Any>
        this.dataInstanceSources = SerializationHelpers.readStringExtMap(`in`, pf) { ExternalDataInstanceSource() }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeString(out, elementType ?: "")
        SerializationHelpers.writeString(out, emptyIfNull(_id))
        SerializationHelpers.writeString(out, emptyIfNull(_value))
        SerializationHelpers.writeBool(out, valueIsXpath)
        SerializationHelpers.writeMultiMap(out, extras)
        SerializationHelpers.writeMap(out, dataInstanceSources)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is StackFrameStep) {
            return false
        }

        return (propertiesEqual(this.elementType, other.elementType) &&
                propertiesEqual(this._id, other._id) &&
                propertiesEqual(this._value, other._value) &&
                this.valueIsXpath == other.valueIsXpath)
    }

    override fun hashCode(): Int {
        val valueIsXPathHash = if (valueIsXpath) 1231 else 1237
        return (elementType.hashCode() xor _id.hashCode() xor
                _value.hashCode() xor valueIsXPathHash)
    }

    private fun propertiesEqual(a: String?, b: String?): Boolean {
        return if (a == null) {
            b == null
        } else {
            a == b
        }
    }

    override fun toString(): String {
        return if (_value == null) {
            "($elementType $_id)"
        } else {
            "($elementType $_id : $_value)"
        }
    }

    fun setType(elementType: String?) {
        this.elementType = elementType
    }

    // Used by Formplayer
    @Suppress("unused")
    fun getElementType(): String? = elementType
}
