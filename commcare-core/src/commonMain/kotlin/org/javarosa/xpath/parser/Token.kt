package org.javarosa.xpath.parser

import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

class Token @JvmOverloads constructor(
    @JvmField val type: Int,
    @JvmField val `val`: Any? = null
) {

    override fun toString(): String {
        return when (type) {
            AND -> "AND"
            AT -> "AT"
            COMMA -> "COMMA"
            DBL_COLON -> "DBL_COLON"
            DBL_DOT -> "DBL_DOT"
            DBL_SLASH -> "DBL_SLASH"
            DIV -> "DIV"
            DOT -> "DOT"
            EQ -> "EQ"
            GT -> "GT"
            GTE -> "GTE"
            LBRACK -> "LBRACK"
            LPAREN -> "LPAREN"
            LT -> "LT"
            LTE -> "LTE"
            MINUS -> "MINUS"
            MOD -> "MOD"
            MULT -> "MULT"
            NEQ -> "NEQ"
            NSWILDCARD -> "NSWILDCARD($`val`)"
            NUM -> "NUM(${`val`})"
            OR -> "OR"
            PLUS -> "PLUS"
            QNAME -> "QNAME(${`val`})"
            RBRACK -> "RBRACK"
            RPAREN -> "RPAREN"
            SLASH -> "SLASH"
            STR -> "STR($`val`)"
            UMINUS -> "UMINUS"
            UNION -> "UNION"
            VAR -> "VAR(${`val`})"
            WILDCARD -> "WILDCARD"
            else -> "UNKNOWN"
        }
    }

    companion object {
        const val AND: Int = 1
        const val AT: Int = 2
        const val COMMA: Int = 3
        const val DBL_COLON: Int = 4
        const val DBL_DOT: Int = 5
        const val DBL_SLASH: Int = 6
        const val DIV: Int = 7
        const val DOT: Int = 8
        const val EQ: Int = 9
        const val GT: Int = 10
        const val GTE: Int = 11
        const val LBRACK: Int = 12
        const val LPAREN: Int = 13
        const val LT: Int = 14
        const val LTE: Int = 15
        const val MINUS: Int = 16
        const val MOD: Int = 17
        const val MULT: Int = 18
        const val NEQ: Int = 19
        const val NSWILDCARD: Int = 20
        const val NUM: Int = 21
        const val OR: Int = 22
        const val PLUS: Int = 23
        const val QNAME: Int = 24
        const val RBRACK: Int = 25
        const val RPAREN: Int = 26
        const val SLASH: Int = 27
        const val STR: Int = 28
        // Unary minus op
        const val UMINUS: Int = 29
        const val UNION: Int = 30
        const val VAR: Int = 31
        const val WILDCARD: Int = 32
    }
}
