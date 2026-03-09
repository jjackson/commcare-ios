package org.commcare.modern.session

import org.commcare.core.interfaces.RemoteInstanceFetcher
import org.commcare.core.process.CommCareInstanceInitializer
import org.commcare.suite.model.Entry
import org.commcare.suite.model.SessionDatum
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.xpath.analysis.XPathAnalyzable

/**
 * Created by willpride on 1/3/17.
 */
interface SessionWrapperInterface {
    fun getIIF(): CommCareInstanceInitializer
    fun getNeededData(): String?
    fun getNeededDatum(entry: Entry): SessionDatum
    fun getEvaluationContext(commandId: String): EvaluationContext
    fun getEvaluationContext(): EvaluationContext
    fun getRestrictedEvaluationContext(commandId: String, instancesToInclude: Set<String>): EvaluationContext
    fun getEvaluationContextWithAccumulatedInstances(commandID: String, xPathAnalyzable: XPathAnalyzable): EvaluationContext

    @Throws(RemoteInstanceFetcher.RemoteInstanceException::class)
    fun prepareExternalSources()
}
