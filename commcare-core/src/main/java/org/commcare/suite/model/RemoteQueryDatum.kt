package org.commcare.suite.model

import org.javarosa.core.util.OrderedHashtable
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.ExtWrapMap
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream

import org.javarosa.core.util.externalizable.PlatformIOException
import java.net.MalformedURLException
import java.net.URL

/**
 * Entry config for querying a remote server with user and session provided
 * parameters and storing the xml data response in an instance.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
class RemoteQueryDatum : SessionDatum {
    private var hiddenQueryValues: List<QueryData>? = null
    private var userQueryPrompts: OrderedHashtable<String, QueryPrompt>? = null
    private var userQueryGroupHeaders: HashMap<String, QueryGroup>? = null
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
        url: URL, storageInstance: String?,
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

    fun getUserQueryGroupHeaders(): HashMap<String, QueryGroup>? = userQueryGroupHeaders

    fun getHiddenQueryValues(): List<QueryData>? = hiddenQueryValues

    fun getUrl(): URL? {
        return try {
            URL(getValue())
        } catch (e: MalformedURLException) {
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
        hiddenQueryValues =
            ExtUtil.read(`in`, ExtWrapList(ExtWrapTagged()), pf) as List<QueryData>
        userQueryPrompts =
            ExtUtil.read(
                `in`,
                ExtWrapMap(String::class.java, QueryPrompt::class.java, ExtWrapMap.TYPE_ORDERED), pf
            ) as OrderedHashtable<String, QueryPrompt>
        userQueryGroupHeaders =
            ExtUtil.read(
                `in`,
                ExtWrapMap(String::class.java, QueryGroup::class.java, ExtWrapMap.TYPE_ORDERED), pf
            ) as HashMap<String, QueryGroup>
        title = ExtUtil.read(`in`, ExtWrapNullable(Text::class.java), pf) as Text?
        description = ExtUtil.read(`in`, ExtWrapNullable(Text::class.java), pf) as Text?
        useCaseTemplate = ExtUtil.readBool(`in`)
        defaultSearch = ExtUtil.readBool(`in`)
        dynamicSearch = ExtUtil.readBool(`in`)
        searchOnClear = ExtUtil.readBool(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        super.writeExternal(out)
        ExtUtil.write(out, ExtWrapList(hiddenQueryValues!!, ExtWrapTagged()))
        ExtUtil.write(out, ExtWrapMap(userQueryPrompts!!))
        ExtUtil.write(out, ExtWrapMap(userQueryGroupHeaders!!))
        ExtUtil.write(out, ExtWrapNullable(title))
        ExtUtil.write(out, ExtWrapNullable(description))
        ExtUtil.writeBool(out, useCaseTemplate)
        ExtUtil.writeBool(out, defaultSearch)
        ExtUtil.writeBool(out, dynamicSearch)
        ExtUtil.writeBool(out, searchOnClear)
    }
}
