package com.floatcalc.app.engine

import java.math.BigDecimal

/**
 * Characters of the internal expression notation. The internal notation always uses '.' as the
 * decimal point and never contains grouping separators; localization happens only for display.
 */
object Symbols {
    const val PLUS = '+'
    const val MINUS = '−'
    const val TIMES = '×'
    const val DIVIDE = '÷'
    const val PERCENT = '%'
    const val OPEN = '('
    const val CLOSE = ')'
    const val POINT = '.'
    const val EXPONENT = 'E'

    private const val OPERATORS = "+−×÷"

    fun isOperator(c: Char): Boolean = c in OPERATORS
}

enum class Operator(val symbol: Char) {
    PLUS(Symbols.PLUS),
    MINUS(Symbols.MINUS),
    TIMES(Symbols.TIMES),
    DIVIDE(Symbols.DIVIDE),
}

enum class CalcError {
    /** The expression ends early, e.g. "2+" or "(". */
    INCOMPLETE,

    /** The expression can never become valid by typing more, e.g. "2××3" or "1.2.3". */
    MALFORMED,
    DIVIDE_BY_ZERO,

    /** The result's magnitude is beyond what the calculator handles. */
    OVERFLOW,
}

internal class CalcException(val error: CalcError) : Exception(error.name)

internal sealed interface Token {
    data class Number(val value: BigDecimal) : Token
    data class Op(val operator: Operator) : Token
    data object Percent : Token
    data object Open : Token
    data object Close : Token
}

internal object Tokenizer {
    const val MAX_EXPONENT = 9_999

    fun tokenize(input: String): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++
                c.isAsciiDigit() || c == Symbols.POINT -> i = readNumber(input, i, tokens)
                c == '+' -> i = add(tokens, Token.Op(Operator.PLUS), i)
                c == Symbols.MINUS || c == '-' -> i = add(tokens, Token.Op(Operator.MINUS), i)
                c == Symbols.TIMES || c == '*' -> i = add(tokens, Token.Op(Operator.TIMES), i)
                c == Symbols.DIVIDE || c == '/' -> i = add(tokens, Token.Op(Operator.DIVIDE), i)
                c == Symbols.PERCENT -> i = add(tokens, Token.Percent, i)
                c == Symbols.OPEN -> i = add(tokens, Token.Open, i)
                c == Symbols.CLOSE -> i = add(tokens, Token.Close, i)
                else -> throw CalcException(CalcError.MALFORMED)
            }
        }
        return tokens
    }

    private fun add(tokens: MutableList<Token>, token: Token, index: Int): Int {
        tokens += token
        return index + 1
    }

    /** Reads digits with at most one '.', optionally followed by an exponent like E12 or E−3. */
    private fun readNumber(s: String, start: Int, out: MutableList<Token>): Int {
        var i = start
        var digits = 0
        var points = 0
        while (i < s.length && (s[i].isAsciiDigit() || s[i] == Symbols.POINT)) {
            if (s[i] == Symbols.POINT) points++ else digits++
            i++
        }
        if (points > 1 || digits == 0) throw CalcException(CalcError.MALFORMED)
        var value = BigDecimal(s.substring(start, i))

        if (i < s.length && (s[i] == Symbols.EXPONENT || s[i] == 'e')) {
            i++
            var negative = false
            if (i < s.length && (s[i] == '+' || s[i] == '-' || s[i] == Symbols.MINUS)) {
                negative = s[i] != '+'
                i++
            }
            val expStart = i
            while (i < s.length && s[i].isAsciiDigit()) i++
            if (i == expStart) {
                throw CalcException(if (i >= s.length) CalcError.INCOMPLETE else CalcError.MALFORMED)
            }
            val expDigits = s.substring(expStart, i).trimStart('0')
            if (expDigits.length > 5 || (expDigits.toIntOrNull() ?: 0) > MAX_EXPONENT) {
                throw CalcException(CalcError.OVERFLOW)
            }
            val exponent = expDigits.toIntOrNull() ?: 0
            value = value.scaleByPowerOfTen(if (negative) -exponent else exponent)
        }
        out += Token.Number(value)
        return i
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'
}
