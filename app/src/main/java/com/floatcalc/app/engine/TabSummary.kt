package com.floatcalc.app.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

/** What a tab chip shows instead of its name once its calculator has something in it. */
sealed interface TabSummary {
    /** "=95": the current result (or the number typed so far), shortened to [MAX_DIGITS]. */
    data class Result(val number: String) : TabSummary

    /** "12+": a number followed by an operator, waiting for the next number. */
    data class Pending(val number: String, val operators: String) : TabSummary

    companion object {
        const val MAX_DIGITS = 4
        private val LIMIT = BigDecimal.TEN.pow(MAX_DIGITS)

        /** Null when the tab should show its name: empty, or nothing that has a value yet. */
        fun of(state: CalcState): TabSummary? {
            val expression = state.expression
            if (expression.isEmpty()) return null
            if (!state.evaluated && Symbols.isOperator(expression.last())) {
                val head = expression.trimEnd(Symbols::isOperator)
                val value = CalcEditor.resultNumber(CalcState(expression = head)) ?: return null
                return Pending(shorten(value), expression.substring(head.length))
            }
            return CalcEditor.resultNumber(state)?.let { Result(shorten(it)) }
        }

        /**
         * Rounds a number in the internal notation to at most [MAX_DIGITS] digits, e.g. 3.14159 → 3.142.
         * Numbers that cannot fit switch to scientific notation, e.g. 12345 → 1.23E4.
         */
        fun shorten(number: String): String {
            val value = BigDecimal(number.replace(Symbols.MINUS, '-'))
            if (value.signum() == 0) return "0"
            val sign = if (value.signum() < 0) Symbols.MINUS.toString() else ""
            val magnitude = value.abs()
            val integerDigits = magnitude.precision() - magnitude.scale()
            if (integerDigits <= MAX_DIGITS) {
                val rounded = magnitude
                    .setScale(MAX_DIGITS - integerDigits.coerceAtLeast(1), RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                if (rounded.signum() != 0 && rounded < LIMIT) return sign + rounded.toPlainString()
            }
            // The exponent's digits count too, so the mantissa gets what is left.
            val exponentDigits = abs(exponentOf(magnitude)).toString().length
            val mantissaDigits = (MAX_DIGITS - exponentDigits).coerceAtLeast(1)
            val rounded = magnitude.round(MathContext(mantissaDigits, RoundingMode.HALF_UP))
            val exponent = exponentOf(rounded)
            val mantissa = rounded.movePointLeft(exponent).stripTrailingZeros().toPlainString()
            val exponentSign = if (exponent < 0) Symbols.MINUS.toString() else ""
            return "$sign$mantissa${Symbols.EXPONENT}$exponentSign${abs(exponent)}"
        }

        /** floor(log10(|value|)) for a non-zero value. */
        private fun exponentOf(value: BigDecimal): Int = value.precision() - value.scale() - 1
    }
}
