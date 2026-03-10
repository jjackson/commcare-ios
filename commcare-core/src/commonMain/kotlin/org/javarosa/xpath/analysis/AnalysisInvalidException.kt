package org.javarosa.xpath.analysis

import kotlin.jvm.JvmField

/**
 * Thrown when an XPathAnalyzer comes across an expression for which it may not be able to do a
 * complete/accurate analysis, to indicate that the results of the analysis should not be used.
 */
class AnalysisInvalidException(msg: String) : Exception(msg) {

    companion object {
        // Static exception instances b/c generating them dynamically costs valuable time during analysis
        @JvmField
        var INSTANCE_NO_CONTEXT_REF: AnalysisInvalidException =
            AnalysisInvalidException("No context ref available when needed")

        @JvmField
        var INSTANCE_NO_ORIGINAL_CONTEXT_REF: AnalysisInvalidException =
            AnalysisInvalidException("No original context ref available when needed")

        @JvmField
        var INSTANCE_ITEMSET_ACCUM_FAILURE: AnalysisInvalidException =
            AnalysisInvalidException("Itemset accumulation failed")

        @JvmField
        var INSTANCE_TEXT_PARSE_FAILURE: AnalysisInvalidException =
            AnalysisInvalidException("Couldn't parse Text XPath Expression")
    }
}
