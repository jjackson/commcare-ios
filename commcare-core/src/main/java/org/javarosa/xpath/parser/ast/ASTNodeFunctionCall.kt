package org.javarosa.xpath.parser.ast

import org.javarosa.xpath.expr.XPathAbsFunc
import org.javarosa.xpath.expr.XPathAcosFunc
import org.javarosa.xpath.expr.XPathAsinFunc
import org.javarosa.xpath.expr.XPathAtanFunc
import org.javarosa.xpath.expr.XPathAtanTwoFunc
import org.javarosa.xpath.expr.XPathBooleanFromStringFunc
import org.javarosa.xpath.expr.XPathBooleanFunc
import org.javarosa.xpath.expr.XPathClosestPointOnPolygonFunc
import org.javarosa.xpath.expr.XPathCeilingFunc
import org.javarosa.xpath.expr.XPathChecklistFunc
import org.javarosa.xpath.expr.XPathChecksumFunc
import org.javarosa.xpath.expr.XPathConcatFunc
import org.javarosa.xpath.expr.XPathCondFunc
import org.javarosa.xpath.expr.XPathContainsFunc
import org.javarosa.xpath.expr.XPathCosFunc
import org.javarosa.xpath.expr.XPathCountFunc
import org.javarosa.xpath.expr.XPathCountSelectedFunc
import org.javarosa.xpath.expr.XPathCustomRuntimeFunc
import org.javarosa.xpath.expr.XPathDateFunc
import org.javarosa.xpath.expr.XPathDecryptStringFunc
import org.javarosa.xpath.expr.XPathDependFunc
import org.javarosa.xpath.expr.XPathDistanceFunc
import org.javarosa.xpath.expr.XPathDistinctValuesFunc
import org.javarosa.xpath.expr.XPathDoubleFunc
import org.javarosa.xpath.expr.XPathEncryptStringFunc
import org.javarosa.xpath.expr.XPathEndsWithFunc
import org.javarosa.xpath.expr.XPathExpFunc
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.expr.XPathFalseFunc
import org.javarosa.xpath.expr.XPathFloorFunc
import org.javarosa.xpath.expr.XPathFormatDateForCalendarFunc
import org.javarosa.xpath.expr.XPathFormatDateFunc
import org.javarosa.xpath.expr.XPathFuncExpr
import org.javarosa.xpath.expr.XPathIdCompressFunc
import org.javarosa.xpath.expr.XPathIfFunc
import org.javarosa.xpath.expr.XPathIndexOfFunc
import org.javarosa.xpath.expr.XPathIntFunc
import org.javarosa.xpath.expr.XPathJoinChunkFunc
import org.javarosa.xpath.expr.XPathJoinFunc
import org.javarosa.xpath.expr.XPathJsonPropertyFunc
import org.javarosa.xpath.expr.XPathLogFunc
import org.javarosa.xpath.expr.XPathLogTenFunc
import org.javarosa.xpath.expr.XPathLowerCaseFunc
import org.javarosa.xpath.expr.XPathMaxFunc
import org.javarosa.xpath.expr.XPathMinFunc
import org.javarosa.xpath.expr.XPathNotFunc
import org.javarosa.xpath.expr.XPathNowFunc
import org.javarosa.xpath.expr.XPathNumberFunc
import org.javarosa.xpath.expr.XPathPiFunc
import org.javarosa.xpath.expr.XPathIsPointInsidePolygonFunc
import org.javarosa.xpath.expr.XPathPositionFunc
import org.javarosa.xpath.expr.XPathPowFunc
import org.javarosa.xpath.expr.XPathQName
import org.javarosa.xpath.expr.XPathRandomFunc
import org.javarosa.xpath.expr.XPathRegexFunc
import org.javarosa.xpath.expr.XPathReplaceFunc
import org.javarosa.xpath.expr.XPathRoundFunc
import org.javarosa.xpath.expr.XPathSelectedAtFunc
import org.javarosa.xpath.expr.XPathSelectedFunc
import org.javarosa.xpath.expr.XPathSinFunc
import org.javarosa.xpath.expr.XPathSleepFunc
import org.javarosa.xpath.expr.XPathSortByFunc
import org.javarosa.xpath.expr.XPathSortFunc
import org.javarosa.xpath.expr.XPathSqrtFunc
import org.javarosa.xpath.expr.XPathStartsWithFunc
import org.javarosa.xpath.expr.XPathStringFunc
import org.javarosa.xpath.expr.XPathStringLengthFunc
import org.javarosa.xpath.expr.XPathSubstrFunc
import org.javarosa.xpath.expr.XPathSubstringAfterFunc
import org.javarosa.xpath.expr.XPathSubstringBeforeFunc
import org.javarosa.xpath.expr.XPathSumFunc
import org.javarosa.xpath.expr.XPathTanFunc
import org.javarosa.xpath.expr.XPathTodayFunc
import org.javarosa.xpath.expr.XPathTranslateFunc
import org.javarosa.xpath.expr.XPathTrueFunc
import org.javarosa.xpath.expr.XPathUpperCaseFunc
import org.javarosa.xpath.expr.XPathUuidFunc
import org.javarosa.xpath.expr.XPathWeightedChecklistFunc
import org.javarosa.xpath.expr.XpathCoalesceFunc
import org.javarosa.xpath.parser.XPathSyntaxException

class ASTNodeFunctionCall(@JvmField val name: XPathQName) : ASTNode() {

    @JvmField
    var args: List<out ASTNode> = ArrayList()

    override fun getChildren(): List<out ASTNode> {
        return args
    }

    @Throws(XPathSyntaxException::class)
    override fun build(): XPathExpression {
        val xargs = Array(args.size) { i ->
            args[i].build()!!
        }

        return buildFuncExpr(name.toString(), xargs)
    }

    companion object {
        @Throws(XPathSyntaxException::class)
        private fun buildFuncExpr(name: String, args: Array<XPathExpression>): XPathFuncExpr {
            return when (name) {
                "if" -> XPathIfFunc(args)
                "coalesce" -> XpathCoalesceFunc(args)
                "cond" -> XPathCondFunc(args)
                "true" -> XPathTrueFunc(args)
                "false" -> XPathFalseFunc(args)
                "boolean" -> XPathBooleanFunc(args)
                "number" -> XPathNumberFunc(args)
                "int" -> XPathIntFunc(args)
                "double" -> XPathDoubleFunc(args)
                "string" -> XPathStringFunc(args)
                "date" -> XPathDateFunc(args)
                "not" -> XPathNotFunc(args)
                "boolean-from-string" -> XPathBooleanFromStringFunc(args)
                "format-date" -> XPathFormatDateFunc(args)
                "selected", "is-selected" -> XPathSelectedFunc(name, args)
                "count-selected" -> XPathCountSelectedFunc(args)
                "selected-at" -> XPathSelectedAtFunc(args)
                "position" -> XPathPositionFunc(args)
                "count" -> XPathCountFunc(args)
                "sum" -> XPathSumFunc(args)
                "max" -> XPathMaxFunc(args)
                "min" -> XPathMinFunc(args)
                "today" -> XPathTodayFunc(args)
                "now" -> XPathNowFunc(args)
                "concat" -> XPathConcatFunc(args)
                "join" -> XPathJoinFunc(args)
                "substr" -> XPathSubstrFunc(args)
                "substring-before" -> XPathSubstringBeforeFunc(args)
                "substring-after" -> XPathSubstringAfterFunc(args)
                "string-length" -> XPathStringLengthFunc(args)
                "upper-case" -> XPathUpperCaseFunc(args)
                "lower-case" -> XPathLowerCaseFunc(args)
                "contains" -> XPathContainsFunc(args)
                "starts-with" -> XPathStartsWithFunc(args)
                "ends-with" -> XPathEndsWithFunc(args)
                "translate" -> XPathTranslateFunc(args)
                "replace" -> XPathReplaceFunc(args)
                "checklist" -> XPathChecklistFunc(args)
                "weighted-checklist" -> XPathWeightedChecklistFunc(args)
                "regex" -> XPathRegexFunc(args)
                "depend" -> XPathDependFunc(args)
                "random" -> XPathRandomFunc(args)
                "uuid" -> XPathUuidFunc(args)
                "pow" -> XPathPowFunc(args)
                "abs" -> XPathAbsFunc(args)
                "ceiling" -> XPathCeilingFunc(args)
                "floor" -> XPathFloorFunc(args)
                "round" -> XPathRoundFunc(args)
                "log" -> XPathLogFunc(args)
                "log10" -> XPathLogTenFunc(args)
                "sin" -> XPathSinFunc(args)
                "cos" -> XPathCosFunc(args)
                "tan" -> XPathTanFunc(args)
                "asin" -> XPathAsinFunc(args)
                "acos" -> XPathAcosFunc(args)
                "atan" -> XPathAtanFunc(args)
                "atan2" -> XPathAtanTwoFunc(args)
                "sqrt" -> XPathSqrtFunc(args)
                "exp" -> XPathExpFunc(args)
                "pi" -> XPathPiFunc(args)
                "distance" -> XPathDistanceFunc(args)
                "format-date-for-calendar" -> XPathFormatDateForCalendarFunc(args)
                "join-chunked" -> XPathJoinChunkFunc(args)
                "id-compress" -> XPathIdCompressFunc(args)
                "checksum" -> XPathChecksumFunc(args)
                "sort" -> XPathSortFunc(args)
                "sort-by" -> XPathSortByFunc(args)
                "distinct-values" -> XPathDistinctValuesFunc(args)
                "sleep" -> XPathSleepFunc(args)
                "index-of" -> XPathIndexOfFunc(args)
                "encrypt-string" -> XPathEncryptStringFunc(args)
                "decrypt-string" -> XPathDecryptStringFunc(args)
                "json-property" -> XPathJsonPropertyFunc(args)
                "closest-point-on-polygon" -> XPathClosestPointOnPolygonFunc(args)
                "is-point-inside-polygon" -> XPathIsPointInsidePolygonFunc(args)
                else -> XPathCustomRuntimeFunc(name, args)
            }
        }
    }
}
