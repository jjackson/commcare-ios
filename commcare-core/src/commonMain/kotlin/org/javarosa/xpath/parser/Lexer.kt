package org.javarosa.xpath.parser
import kotlin.jvm.JvmStatic

import org.javarosa.xpath.expr.XPathQName

object Lexer {

    private const val CONTEXT_LENGTH = 15

    private const val LEX_CONTEXT_VAL = 1
    private const val LEX_CONTEXT_OP = 2

    @JvmStatic
    @Throws(XPathSyntaxException::class)
    fun lex(expr: String): List<Token> {
        val tokens = ArrayList<Token>()

        var i = 0
        var context = LEX_CONTEXT_VAL

        while (i < expr.length) {
            val c = expr[i].code
            val d = getChar(expr, i + 1)

            var token: Token? = null
            var skip = 1

            if (" \n\t\u000C\r".indexOf(c.toChar()) >= 0) {
                /* whitespace; do nothing */
            } else if (c == '='.code) {
                token = Token(Token.EQ)
            } else if (c == '!'.code && d == '='.code) {
                token = Token(Token.NEQ)
                skip = 2
            } else if (c == '<'.code) {
                if (d == '='.code) {
                    token = Token(Token.LTE)
                    skip = 2
                } else {
                    token = Token(Token.LT)
                }
            } else if (c == '>'.code) {
                if (d == '='.code) {
                    token = Token(Token.GTE)
                    skip = 2
                } else {
                    token = Token(Token.GT)
                }
            } else if (c == '+'.code) {
                token = Token(Token.PLUS)
            } else if (c == '-'.code) {
                token = Token(if (context == LEX_CONTEXT_VAL) Token.UMINUS else Token.MINUS) //not sure this is entirely correct
            } else if (c == '*'.code) {
                token = Token(if (context == LEX_CONTEXT_VAL) Token.WILDCARD else Token.MULT)
            } else if (c == '|'.code) {
                token = Token(Token.UNION)
            } else if (c == '/'.code) {
                if (d == '/'.code) {
                    token = Token(Token.DBL_SLASH)
                    skip = 2
                } else {
                    token = Token(Token.SLASH)
                }
            } else if (c == '['.code) {
                token = Token(Token.LBRACK)
            } else if (c == ']'.code) {
                token = Token(Token.RBRACK)
            } else if (c == '('.code) {
                token = Token(Token.LPAREN)
            } else if (c == ')'.code) {
                token = Token(Token.RPAREN)
            } else if (c == '.'.code) {
                if (d == '.'.code) {
                    token = Token(Token.DBL_DOT)
                    skip = 2
                } else if (isDigit(d)) {
                    skip = matchNumeric(expr, i)
                    token = Token(Token.NUM, expr.substring(i, i + skip).toDouble())
                } else {
                    token = Token(Token.DOT)
                }
            } else if (c == '@'.code) {
                token = Token(Token.AT)
            } else if (c == ','.code) {
                token = Token(Token.COMMA)
            } else if (c == ':'.code && d == ':'.code) {
                token = Token(Token.DBL_COLON)
                skip = 2
            } else if (context == LEX_CONTEXT_OP && i + 3 <= expr.length && "and" == expr.substring(i, i + 3)) {
                token = Token(Token.AND)
                skip = 3
            } else if (context == LEX_CONTEXT_OP && i + 2 <= expr.length && "or" == expr.substring(i, i + 2)) {
                token = Token(Token.OR)
                skip = 2
            } else if (context == LEX_CONTEXT_OP && i + 3 <= expr.length && "div" == expr.substring(i, i + 3)) {
                token = Token(Token.DIV)
                skip = 3
            } else if (context == LEX_CONTEXT_OP && i + 3 <= expr.length && "mod" == expr.substring(i, i + 3)) {
                token = Token(Token.MOD)
                skip = 3
            } else if (c == '$'.code) {
                val len = matchQName(expr, i + 1)
                if (len == 0) {
                    badParse(expr, i, c.toChar())
                } else {
                    token = Token(Token.VAR, XPathQName(expr.substring(i + 1, i + len + 1)))
                    skip = len + 1
                }
            } else if (c == '\''.code || c == '"'.code) {
                val end = expr.indexOf(c.toChar(), i + 1)
                if (end == -1) {
                    badParse(expr, i, c.toChar())
                } else {
                    token = Token(Token.STR, expr.substring(i + 1, end))
                    skip = (end - i) + 1
                }
            } else if (isDigit(c)) {
                skip = matchNumeric(expr, i)
                token = Token(Token.NUM, expr.substring(i, i + skip).toDouble())
            } else if (context == LEX_CONTEXT_VAL && (isAlpha(c) || c == '_'.code)) {
                val len = matchQName(expr, i)
                val name = expr.substring(i, i + len)
                if (name.indexOf(':') == -1 && getChar(expr, i + len) == ':'.code && getChar(expr, i + len + 1) == '*'.code) {
                    token = Token(Token.NSWILDCARD, name)
                    skip = len + 2
                } else {
                    token = Token(Token.QNAME, XPathQName(name))
                    skip = len
                }
            } else {
                badParse(expr, i, c.toChar())
            }
            if (token != null) {
                if (token.type == Token.WILDCARD ||
                    token.type == Token.NSWILDCARD ||
                    token.type == Token.QNAME ||
                    token.type == Token.VAR ||
                    token.type == Token.NUM ||
                    token.type == Token.STR ||
                    token.type == Token.RBRACK ||
                    token.type == Token.RPAREN ||
                    token.type == Token.DOT ||
                    token.type == Token.DBL_DOT
                ) {
                    context = LEX_CONTEXT_OP
                } else {
                    context = LEX_CONTEXT_VAL
                }

                tokens.add(token)
            }
            i += skip
        }

        return tokens
    }

    @Throws(XPathSyntaxException::class)
    private fun badParse(expr: String, i: Int, c: Char) {
        val start = "\u034E$c"
        val preContext = (if (maxOf(0, i - CONTEXT_LENGTH) != 0) "..." else "") +
                expr.substring(maxOf(0, i - CONTEXT_LENGTH), maxOf(0, i)).trim()
        val postcontext = if (i == expr.length - 1) "" else
            expr.substring(minOf(i + 1, expr.length - 1), minOf(i + CONTEXT_LENGTH, expr.length)).trim() +
                    (if (minOf(i + CONTEXT_LENGTH, expr.length) != expr.length) "..." else "")

        throw XPathSyntaxException("Couldn't understand the expression starting at this point: " + (preContext + start + postcontext))
    }

    private fun matchNumeric(expr: String, startIdx: Int): Int {
        var seenDecimalPoint = false
        var i = startIdx

        while (i < expr.length) {
            val c = expr[i].code

            if (!(isDigit(c) || (!seenDecimalPoint && c == '.'.code)))
                break

            if (c == '.'.code)
                seenDecimalPoint = true

            i++
        }

        return i - startIdx
    }

    private fun matchQName(expr: String, i: Int): Int {
        var len = matchNCName(expr, i)

        if (len > 0 && getChar(expr, i + len) == ':'.code) {
            val len2 = matchNCName(expr, i + len + 1)

            if (len2 > 0)
                len += len2 + 1
        }

        return len
    }

    private fun matchNCName(expr: String, startIdx: Int): Int {
        var i = startIdx

        while (i < expr.length) {
            val c = expr[i].code

            if (!(isAlpha(c) || c == '_'.code || (i > startIdx && (isDigit(c) || c == '.'.code || c == '-'.code))))
                break

            i++
        }

        return i - startIdx
    }

    //get char from string, return -1 for EOF
    private fun getChar(expr: String, i: Int): Int {
        return if (i < expr.length) expr[i].code else -1
    }

    private fun isDigit(c: Int): Boolean {
        return c >= 0 && c.toChar().isDigit()
    }

    private fun isAlpha(c: Int): Boolean {
        return c >= 0 && c.toChar().isLetter()
    }
}
