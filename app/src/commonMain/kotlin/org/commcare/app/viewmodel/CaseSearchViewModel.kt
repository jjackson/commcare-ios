package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.commcare.app.engine.SessionNavigatorImpl
import org.commcare.app.storage.SqlDelightUserSandbox
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.PlatformHttpClient
import org.commcare.core.parse.ParseUtils
import org.commcare.suite.model.QueryPrompt
import org.commcare.suite.model.RemoteQueryDatum
import org.javarosa.core.io.createByteArrayInputStream
import org.javarosa.core.util.OrderedHashtable

/**
 * Manages remote case search — builds search fields from QueryPrompt config,
 * executes HTTP search requests, and displays results.
 */
class CaseSearchViewModel(
    private val navigator: SessionNavigatorImpl,
    private val sandbox: SqlDelightUserSandbox,
    private val httpClient: PlatformHttpClient,
    private val authHeader: String
) {
    var searchFields by mutableStateOf<List<SearchField>>(emptyList())
        private set
    var searchValues by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var results by mutableStateOf<List<CaseItem>>(emptyList())
        private set
    var isSearching by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var title by mutableStateOf("Case Search")
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var queryDatum: RemoteQueryDatum? = null

    fun cancel() { scope.cancel() }
    private var searchUrl: String? = null

    /**
     * Initialize search fields from the session's RemoteQueryDatum.
     */
    fun loadSearchConfig() {
        try {
            val datum = navigator.session.getNeededDatum()
            if (datum is RemoteQueryDatum) {
                queryDatum = datum
                searchUrl = datum.getValue()

                val prompts = datum.getUserQueryPrompts()
                if (prompts != null) {
                    val fields = mutableListOf<SearchField>()
                    val keys = prompts.keys.iterator()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val prompt = prompts[key]!!
                        if (prompt.hidden != null) continue // Skip hidden prompts

                        val label = try {
                            prompt.display?.text?.evaluate() ?: key
                        } catch (_: Exception) {
                            key
                        }
                        fields.add(SearchField(
                            key = key,
                            label = label,
                            appearance = prompt.appearance,
                            isRequired = prompt.required != null
                        ))
                    }
                    searchFields = fields
                    searchValues = fields.associate { it.key to "" }
                }
            }
        } catch (e: Exception) {
            errorMessage = "Failed to load search config: ${e.message}"
        }
    }

    fun updateSearchValue(key: String, value: String) {
        searchValues = searchValues + (key to value)
    }

    /**
     * Execute remote case search against CommCare HQ.
     */
    fun executeSearch() {
        val url = searchUrl ?: return
        isSearching = true
        errorMessage = null

        scope.launch {
            try {
                // Build query URL with search parameters
                val queryParams = searchValues.entries
                    .filter { it.value.isNotBlank() }
                    .joinToString("&") { "${it.key}=${it.value}" }
                val fullUrl = if (queryParams.isNotEmpty()) "$url?$queryParams" else url

                val response = httpClient.execute(
                    HttpRequest(
                        url = fullUrl,
                        method = "GET",
                        headers = mapOf("Authorization" to authHeader)
                    )
                )

                if (response.code in 200..299) {
                    val body = response.body
                    if (body != null && body.isNotEmpty()) {
                        // Parse response into sandbox as case data
                        val stream = createByteArrayInputStream(body)
                        try {
                            ParseUtils.parseIntoSandbox(stream, sandbox, false)
                        } catch (_: Exception) {
                            // Parse may fail if response format differs
                        }

                        // Load results from case storage
                        loadResultsFromStorage()
                    } else {
                        results = emptyList()
                    }
                } else {
                    errorMessage = "Search failed: server returned ${response.code}"
                    // Fall back to local search
                    loadResultsFromStorage()
                }
            } catch (e: Exception) {
                errorMessage = "Search error: ${e.message}. Searching locally..."
                loadResultsFromStorage()
            } finally {
                isSearching = false
            }
        }
    }

    /**
     * Load results from local case storage (used as fallback or after remote parse).
     */
    private fun loadResultsFromStorage() {
        try {
            val storage = sandbox.getCaseStorage()
            val items = mutableListOf<CaseItem>()
            val searchTerms = searchValues.values.filter { it.isNotBlank() }.map { it.lowercase() }

            val iter = storage.iterate()
            while (iter.hasMore()) {
                val c = iter.nextRecord()
                if (c.isClosed()) continue

                // Filter by search terms if any
                if (searchTerms.isNotEmpty()) {
                    val name = (c.getName() ?: "").lowercase()
                    val matchesAny = searchTerms.any { term ->
                        name.contains(term) || c.getMetaDataFields().any { field ->
                            (c.getMetaData(field)?.toString() ?: "").lowercase().contains(term)
                        }
                    }
                    if (!matchesAny) continue
                }

                items.add(CaseItem(
                    caseId = c.getCaseId() ?: "",
                    name = c.getName() ?: "Unnamed",
                    caseType = c.getTypeId() ?: "",
                    dateOpened = c.getMetaData("date-opened")?.toString() ?: "",
                    properties = buildPropertyMap(c)
                ))
            }
            results = items
        } catch (e: Exception) {
            errorMessage = "Failed to load results: ${e.message}"
        }
    }

    /**
     * Select a search result — sets the datum value and advances session.
     */
    fun selectResult(caseItem: CaseItem) {
        navigator.selectCase(caseItem.caseId)
    }

    private fun buildPropertyMap(c: org.commcare.cases.model.Case): Map<String, String> {
        val props = mutableMapOf<String, String>()
        try {
            for (field in c.getMetaDataFields()) {
                val value = c.getMetaData(field)
                if (value != null) {
                    props[field] = value.toString()
                }
            }
        } catch (_: Exception) { }
        return props
    }
}

data class SearchField(
    val key: String,
    val label: String,
    val appearance: String? = null,
    val isRequired: Boolean = false
)
