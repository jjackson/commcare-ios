package org.commcare.session

import org.javarosa.core.util.ListMultimap
import org.commcare.cases.util.StringUtils
import org.commcare.data.xml.VirtualInstances
import org.commcare.modern.util.Pair
import org.commcare.suite.model.QueryData
import org.commcare.suite.model.QueryPrompt
import org.commcare.suite.model.QueryPrompt.Companion.INPUT_TYPE_DATERANGE
import org.commcare.suite.model.RemoteQueryDatum
import org.commcare.suite.model.SessionDatum
import org.commcare.util.DateRangeUtils
import org.javarosa.core.model.ItemsetBinding
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.ExternalDataInstance
import org.javarosa.core.model.instance.ExternalDataInstanceSource
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.instance.utils.TreeUtilities
import org.javarosa.core.model.utils.ItemSetUtils
import org.javarosa.core.services.Logger
import org.javarosa.core.util.OrderedHashtable
import org.javarosa.model.xform.XPathReference
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.expr.XPathExpression
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.text.ParseException
import java.util.Enumeration
import java.util.HashMap
import java.util.Hashtable

/**
 * Manager for remote query datums; get/answer user prompts and build
 * resulting query url.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
class RemoteQuerySessionManager private constructor(
    private val queryDatum: RemoteQueryDatum,
    private val evaluationContext: EvaluationContext,
    private val supportedPrompts: List<String>
) {

    private val userAnswers: Hashtable<String, String> = Hashtable()
    private var errors: Hashtable<String, String> = Hashtable()
    private var requiredPrompts: Hashtable<String, Boolean> = Hashtable()

    init {
        initUserAnswers()
        refreshInputDependentState()
    }

    @Throws(XPathException::class)
    private fun initUserAnswers() {
        val queryPrompts = queryDatum.getUserQueryPrompts() ?: return
        val en: Enumeration<*> = queryPrompts.keys()
        while (en.hasMoreElements()) {
            val promptId = en.nextElement() as String
            val prompt = queryPrompts[promptId]

            if (isPromptSupported(prompt!!) && prompt.getDefaultValueExpr() != null) {
                var value = FunctionUtils.toString(prompt.getDefaultValueExpr()!!.eval(evaluationContext))
                if (INPUT_TYPE_DATERANGE == prompt.getInput()) {
                    try {
                        value = DateRangeUtils.formatDateRangeAnswer(value)
                    } catch (e: ParseException) {
                        Logger.exception("Error parsing default date range $value for $promptId", e)
                    }
                }
                userAnswers.put(prompt.getKey(), value)
            }
        }
    }

    fun getNeededUserInputDisplays(): OrderedHashtable<String, QueryPrompt>? {
        return queryDatum.getUserQueryPrompts()
    }

    fun getUserAnswers(): Hashtable<String, String> {
        return userAnswers
    }

    fun getErrors(): Hashtable<String, String> {
        return errors
    }

    fun getRequiredPrompts(): Hashtable<String, Boolean> {
        return requiredPrompts
    }

    fun clearAnswers() {
        userAnswers.clear()
    }

    /**
     * Register a non-null value as an answer for the given key.
     * If value is null, removes the corresponding answer
     */
    fun answerUserPrompt(key: String, value: String?) {
        if (value == null) {
            userAnswers.remove(key)
        } else {
            userAnswers.put(key, value)
        }
    }

    fun getBaseUrl(): URL? {
        return queryDatum.getUrl()
    }

    /**
     * @param skipDefaultPromptValues don't apply the default value expressions for query prompts
     * @return filters to be applied to case search uri as query params
     */
    fun getRawQueryParams(skipDefaultPromptValues: Boolean): ListMultimap<String, String> {
        val evalContextWithAnswers = getEvaluationContextWithUserInputInstance()

        val params: ListMultimap<String, String> = ListMultimap()
        val hiddenQueryValues = queryDatum.getHiddenQueryValues() ?: emptyList()
        for (queryData in hiddenQueryValues) {
            params.putAll(queryData.getKey(), queryData.getValues(evalContextWithAnswers))
        }

        if (!skipDefaultPromptValues) {
            val en: Enumeration<*> = userAnswers.keys()
            while (en.hasMoreElements()) {
                val key = en.nextElement() as String
                val value = userAnswers[key]
                val prompt = queryDatum.getUserQueryPrompts()?.get(key)
                val excludeExpr = prompt!!.getExclude()
                if (!(params.containsKey(key) && params[key].contains(value))) {
                    if (value != null && (excludeExpr == null || !(excludeExpr.eval(evaluationContext) as Boolean))) {
                        val choices = extractMultipleChoices(value)
                        for (choice in choices) {
                            params.put(key, choice)
                        }
                    }
                }
            }
        }
        return params
    }

    private fun getEvaluationContextWithUserInputInstance(): EvaluationContext {
        val userQueryValues = getUserQueryValues(false)
        val refId = getSearchInstanceReferenceId()
        val userInputInstance = VirtualInstances.buildSearchInputInstance(
            refId, userQueryValues
        )
        return evaluationContext.spawnWithCleanLifecycle(
            mapOf(
                userInputInstance.getInstanceId()!! to userInputInstance,
                // Temporary method to make the 'search-input' instance available using the legacy ID
                // Technically this instance elements should get renamed to match the instance ID, but
                // it's OK here since the other instance is always going to be in the eval context.
                "search-input" to userInputInstance
            )
        )
    }

    private fun getSearchInstanceReferenceId(): String {
        return queryDatum.getDataId()!!
    }

    fun populateItemSetChoices(queryPrompt: QueryPrompt) {
        queryPrompt.getItemsetBinding()?.let {
            ItemSetUtils.populateDynamicChoices(it, getEvaluationContextWithUserInputInstance())
        }
    }

    fun getUserQueryValues(includeNulls: Boolean): Map<String, String?> {
        val values: MutableMap<String, String?> = HashMap()
        val queryPrompts = queryDatum.getUserQueryPrompts() ?: return values
        val en: Enumeration<*> = queryPrompts.keys()
        while (en.hasMoreElements()) {
            val promptId = en.nextElement() as String
            if (isPromptSupported(queryPrompts[promptId]!!)) {
                val answer = userAnswers[promptId]
                if (includeNulls || answer != null) {
                    values[promptId] = answer
                }
            }
        }
        return values
    }

    // loops over query prompts and validates selection until all selections are valid
    fun refreshItemSetChoices() {
        val userInputDisplays = getNeededUserInputDisplays() ?: return
        if (userInputDisplays.size == 0) {
            return
        }

        var dirty = true
        var index = 0
        while (dirty) {
            if (index == userInputDisplays.size) {
                // loop has already run as many times as no of questions and we are still dirty
                throw RuntimeException(
                    "Invalid itemset state encountered while trying to refresh itemset choices"
                )
            }
            dirty = false
            val en: Enumeration<*> = userInputDisplays.keys()
            while (en.hasMoreElements()) {
                val promptId = en.nextElement() as String
                val queryPrompt = userInputDisplays[promptId]!!
                if (queryPrompt.isSelect()) {
                    val answer = userAnswers[promptId]
                    populateItemSetChoices(queryPrompt)
                    val selectedChoices = extractMultipleChoices(answer)
                    val validSelectedChoices = ArrayList<String>()
                    for (selectedChoice in selectedChoices) {
                        if (queryPrompt.getItemsetBinding() != null && checkForValidSelectValue(queryPrompt.getItemsetBinding()!!, selectedChoice)) {
                            validSelectedChoices.add(selectedChoice)
                        } else {
                            dirty = true
                        }
                    }
                    if (validSelectedChoices.size > 0) {
                        userAnswers.put(
                            promptId,
                            validSelectedChoices.joinToString(ANSWER_DELIMITER)
                        )
                    } else {
                        // no value
                        userAnswers.remove(promptId)
                    }
                }
            }
            index++
        }
    }

    // Recalculates screen properties that are dependent on user input
    fun refreshInputDependentState() {
        refreshItemSetChoices()
        validateUserAnswers()
    }

    private fun validateUserAnswers() {
        requiredPrompts = Hashtable()
        errors = Hashtable()
        val userInputDisplays = getNeededUserInputDisplays() ?: return
        val instanceId = VirtualInstances.makeSearchInputInstanceID(getSearchInstanceReferenceId())
        val ec = getEvaluationContextWithUserInputInstance()
        val en: Enumeration<*> = userInputDisplays.keys()
        while (en.hasMoreElements()) {
            val key = en.nextElement() as String
            val queryPrompt = userInputDisplays[key]!!
            val isRequired = queryPrompt.isRequired(ec)
            requiredPrompts.put(key, isRequired)
            val value = userAnswers[key]
            val currentRef = getReferenceToInstanceNode(instanceId, key)
            if (!StringUtils.isEmpty(value) && queryPrompt.isInvalidInput(EvaluationContext(ec, currentRef))) {
                errors.put(key, queryPrompt.getValidationMessage(ec))
            }
            if (StringUtils.isEmpty(value) && isRequired) {
                val message = queryPrompt.getRequiredMessage(ec)
                errors.put(key, message)
            }
        }
    }

    private fun getReferenceToInstanceNode(instanceId: String, key: String): TreeReference {
        val keyPath = "instance('$instanceId')/input/field[@name='$key']"
        return XPathReference.getPathExpr(keyPath).getReference()
    }

    fun isPromptSupported(queryPrompt: QueryPrompt): Boolean {
        return queryPrompt.getInput() == null || supportedPrompts.indexOf(queryPrompt.getInput()) != -1
    }

    // checks if value is one of the select choices given in items
    private fun checkForValidSelectValue(itemsetBinding: ItemsetBinding, value: String?): Boolean {
        // blank is always a valid choice
        if (StringUtils.isEmpty(value)) {
            return true
        }
        return ItemSetUtils.getIndexOf(itemsetBinding, value!!) != -1
    }

    fun doDefaultSearch(): Boolean {
        return queryDatum.doDefaultSearch()
    }

    fun getDynamicSearch(): Boolean {
        return queryDatum.getDynamicSearch()
    }

    fun isSearchOnClear(): Boolean {
        return queryDatum.isSearchOnClear()
    }

    fun getQueryDatum(): RemoteQueryDatum {
        return queryDatum
    }

    fun buildExternalDataInstance(
        responseData: InputStream, url: String?,
        requestData: ListMultimap<String, String>?
    ): Pair<ExternalDataInstance?, String?> {
        try {
            val instanceID = getQueryDatum().getDataId()
            val root = TreeUtilities.xmlStreamToTreeElement(responseData, instanceID)
            val instanceSource = ExternalDataInstanceSource.buildRemote(
                instanceID, root, getQueryDatum().useCaseTemplate(), url, requestData
            )
            val instance = instanceSource.toInstance()
            return Pair(instance, "")
        } catch (e: InvalidStructureException) {
            return Pair(null, e.message)
        } catch (e: IOException) {
            return Pair(null, e.message)
        } catch (e: XmlPullParserException) {
            return Pair(null, e.message)
        } catch (e: UnfullfilledRequirementsException) {
            return Pair(null, e.message)
        }
    }

    companion object {
        // used to parse multi-select choices
        const val ANSWER_DELIMITER: String = "#,#"

        @JvmStatic
        fun buildQuerySessionManager(
            session: CommCareSession,
            sessionContext: EvaluationContext,
            supportedPrompts: List<String>
        ): RemoteQuerySessionManager? {
            val datum: SessionDatum?
            try {
                datum = session.getNeededDatum()
            } catch (e: IllegalStateException) {
                // tried loading session info when it wasn't there
                return null
            }
            if (datum is RemoteQueryDatum) {
                return RemoteQuerySessionManager(datum, sessionContext, supportedPrompts)
            } else {
                return null
            }
        }

        @JvmStatic
        fun evalXpathExpression(
            expr: XPathExpression,
            evaluationContext: EvaluationContext
        ): String {
            return FunctionUtils.toString(expr.eval(evaluationContext))
        }

        // Converts a string containing space separated list of choices
        // into a string array of individual choices
        @JvmStatic
        fun extractMultipleChoices(answer: String?): Array<String> {
            if (answer == null) {
                return arrayOf()
            }
            return answer.split(ANSWER_DELIMITER.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        }

        /**
         * Join multiple choices for a prompt into a single String separated by answer delimiter
         *
         * @param choices list of choices to be joined together
         * @return String with choices joined with the answer delimiter
         */
        @JvmStatic
        fun joinMultipleChoices(choices: ArrayList<String>): String {
            return choices.joinToString(ANSWER_DELIMITER)
        }
    }
}
