package org.javarosa.xpath.analysis

/**
 * Represents any object which may be subject to static analysis by an XPathAnalyzer
 * (XPathExpressions and TreeReferences)
 *
 * @author Aliza Stone
 */
interface XPathAnalyzable {

    @Throws(AnalysisInvalidException::class)
    fun applyAndPropagateAnalyzer(analyzer: XPathAnalyzer)
}
