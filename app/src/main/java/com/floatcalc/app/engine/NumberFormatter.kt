package com.floatcalc.app.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.min

/**
 * Turns results into strings in the internal notation ('.' decimal point, '−' minus, 'E' exponent,
 * no grouping). Results are rounded to [SIGNIFICANT_DIGITS], trailing zeros are dropped, and
 * scientific notation is used only when the plain form would be too long.
 */
object NumberFormatter {
    const val SIGNIFICANT_DIGITS = 16
    private const val SCIENTIFIC_DIGITS = 10
    private const val MIN_PLAIN_EXPONENT = -9
    private const val MAX_FRACTION_DIGITS = 18

    fun format(value: BigDecimal): String {
        if (value.signum() == 0) return "0"
        val rounded = value.round(MathContext(SIGNIFICANT_DIGITS, RoundingMode.HALF_UP)).stripTrailingZeros()
        val exponent = exponentOf(rounded)
        val magnitude = rounded.abs()
        val body = when {
            exponent >= SIGNIFICANT_DIGITS -> scientific(magnitude)
            exponent >= 0 -> magnitude.toPlainString()
            exponent >= MIN_PLAIN_EXPONENT -> magnitude
                .setScale(min(magnitude.scale(), MAX_FRACTION_DIGITS), RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
            else -> scientific(magnitude)
        }
        return if (value.signum() < 0) "${Symbols.MINUS}$body" else body
    }

    /** Full-precision form used to keep chained calculations exact (never shown to the user). */
    fun exact(value: BigDecimal): String = value.stripTrailingZeros().toString()

    private fun scientific(magnitude: BigDecimal): String {
        val short = magnitude.round(MathContext(SCIENTIFIC_DIGITS, RoundingMode.HALF_UP)).stripTrailingZeros()
        val exponent = exponentOf(short)
        val mantissa = short.movePointLeft(exponent).stripTrailingZeros().toPlainString()
        val sign = if (exponent < 0) Symbols.MINUS.toString() else ""
        return "$mantissa${Symbols.EXPONENT}$sign${kotlin.math.abs(exponent)}"
    }

    /** floor(log10(|value|)) for a non-zero value. */
    private fun exponentOf(value: BigDecimal): Int = value.precision() - value.scale() - 1
}
