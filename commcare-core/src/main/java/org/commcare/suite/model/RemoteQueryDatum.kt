package org.commcare.suite.model

import org.javarosa.core.util.OrderedHashtable
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers

import org.javarosa.core.util.PlatformMalformedUrlException
import org.javarosa.core.util.PlatformUrl
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Entry config for querying a remote server with user and session provided
 * parameters and storing the xml data response in an instance.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
class RemoteQueryDatum : SessionDatum {
    private var hiddenQueryValues: List<QueryData>? = null
    private var userQueryPrompts: OrderedHashtable<String, QueryPrompt>? = null
    private var userQueryGroupHeaders: MutableMap<String, QueryGroup>? = null
    private var useCaseTemplate: Boolean = false
    private var defaultSearch: Boolean = false
    private var dynamicSearch: Boolean = false
    private var searchOnClear: Boolean = false
    private var title: Text? = null
    private var description: Text? = null

    constructor()

    /**
     * @param useCaseTemplate True if query results respect the casedb
     *                        template structure. Permits flexibility (path
     *                        heterogeneity) in case data lookups
     */
    constructor(
        url: PlatformUrl, storageInstance: String?,
        hiddenQueryValues: List<QueryData>?,
        userQueryPrompts: OrderedHashtable<String, QueryPrompt>?, useCaseTemplate: Boolean,
        defaultSearch: Boolean, dynamicSearch: Boolean, title: Text?, description: Text?,
        userQueryGroupHeaders: HashMap<String, QueryGroup>?, searchOnClear: Boolean
    ) : super(storageInstance, url.toString()) {
        this.hiddenQueryValues = hiddenQueryValues
        this.userQueryPrompts = userQueryPrompts
        this.userQueryGroupHeaders = userQueryGroupHeaders
        this.useCaseTemplate = useCaseTemplate
        this.defaultSearch = defaultSearch
        this.dynamicSearch = dynamicSearch
        this.searchOnClear = searchOnClear
        this.title = title
        this.description = description
    }

    fun getUserQueryPrompts(): OrderedHashtable<String, QueryPrompt>? = userQueryPrompts

    fun getUserQueryGroupHeaders(): MutableMap<String, QueryGroup>? = userQueryGroupHeaders

    fun getHiddenQueryValues(): List<QueryData>? = hiddenQueryValues

    fun getUrl(): PlatformUrl? {
        return try {
            PlatformUrl(getValue()!!)
        } catch (e: PlatformMalformedUrlException) {
            // Not possible given constructor passes in a valid URL
            e.printStackTrace()
            null
        }
    }

    fun useCaseTemplate(): Boolean = useCaseTemplate

    fun doDefaultSearch(): Boolean = defaultSearch

    fun getDynamicSearch(): Boolean = dynamicSearch

    fun isSearchOnClear(): Boolean = searchOnClear

    fun getTitleText(): Text? = title

    fun getDescriptionText(): Text? = description

    @Suppress("UNCHECKED_CAST")
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        super.readExternal(`in`, pf)
        @Suppress("UNCHECKED_CAST")
        hiddenQueryValues =
            SerializationHelpers.readListPoly(`in`, pf) as List<QueryData>
        userQueryPrompts =
            OrderedHashtable<String, QueryPrompt>().also { it.putAll(SerializationHelpers.readOrderedStringExtMap(`in`, pf) { QueryPrompt() }) }
        userQueryGroupHeaders =
            SerializationHelpers.readOrderedStringExtMap(`in`, pf) { QueryGroup() } as MutableMap<String, QueryGroup>
        title = SerializationHelpers.readNullableExternalizable(`in`, pf) { Text() }
        description = SerializationHelpers.readNullableExternalizable(`in`, pf) { Text() }
        useCaseTemplate = SerializationHelpers.readBool(`in`)
        defaultSearch = SerializationHelpers.readBool(`in`)
        dynamicSearch = SerializationHelpers.readBool(`in`)
        searchOnClear = SerializationHelpers.readBool(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        super.writeExternal(out)
        SerializationHelpers.writeListPoly(out, hiddenQueryValues!!)
        SerializationHelpers.writeMap(out, userQueryPrompts!!)
        SerializationHelpers.writeMap(out, userQueryGroupHeaders!!)
        SerializationHelpers.writeNullable(out, title)
        SerializationHelpers.writeNullable(out, description)
        SerializationHelpers.writeBool(out, useCaseTemplate)
        SerializationHelpers.writeBool(out, defaultSearch)
        SerializationHelpers.writeBool(out, dynamicSearch)
        SerializationHelpers.writeBool(out, searchOnClear)
    }
}
