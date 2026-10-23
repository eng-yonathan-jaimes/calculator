package com.floatcalc.app.engine

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/**
 * Localizes internal-notation text for display: groups integer digits and swaps in the locale's
 * decimal separator. Digits stay 0–9 to match the keypad.
 */
class DisplayFormatter(
    val decimalSeparator: Char = '.',
    val groupingSeparator: Char = ',',
    private val groupingSize: Int = 3,
    private val secondaryGroupingSize: Int = groupingSize,
) {
    /** Formats every number inside [expression]; operators and parentheses are kept as they are. */
    fun formatExpression(expression: String): String = formatExpressionMapped(expression).text

    /** Like [formatExpression], also mapping caret positions between the two texts. */
    fun formatExpressionMapped(expression: String): FormattedExpression {
        val out = StringBuilder(expression.length + expression.length / 3)
        val offsets = IntArray(expression.length + 1)
        var i = 0
        while (i < expression.length) {
            val c = expression[i]
            if (c.isDigit() || c == Symbols.POINT) {
                val end = numberEnd(expression, i)
                val formatted = formatNumber(expression.substring(i, end))
                // Walk both texts together; characters only in the formatted one are separators.
                var f = 0
                for (k in i until end) {
                    val target = if (expression[k] == Symbols.POINT) decimalSeparator else expression[k]
                    while (f < formatted.length && formatted[f] != target) f++
                    offsets[k] = out.length + f
                    f++
                }
                out.append(formatted)
                i = end
            } else {
                offsets[i] = out.length
                out.append(c)
                i++
            }
        }
        offsets[expression.length] = out.length
        return FormattedExpression(out.toString(), offsets)
    }

    /** Formats one number in internal notation, e.g. "−1234.5" → "−1,234.5" (en-US). */
    fun formatNumber(number: String): String {
        val sign = if (number.startsWith(Symbols.MINUS)) Symbols.MINUS.toString() else ""
        val unsigned = number.removePrefix(sign)
        val expIndex = unsigned.indexOf(Symbols.EXPONENT)
        val mantissa = if (expIndex >= 0) unsigned.substring(0, expIndex) else unsigned
        val exponent = if (expIndex >= 0) unsigned.substring(expIndex) else ""
        val pointIndex = mantissa.indexOf(Symbols.POINT)
        val integerPart = if (pointIndex >= 0) mantissa.substring(0, pointIndex) else mantissa
        val fraction = if (pointIndex >= 0) decimalSeparator + mantissa.substring(pointIndex + 1) else ""
        return sign + group(integerPart) + fraction + exponent
    }

    /**
     * Text for the clipboard: no grouping, the locale's decimal separator and an ASCII '-', so
     * other apps can parse it as a plain number.
     */
    fun toClipboardNumber(number: String): String =
        number.replace(Symbols.MINUS, '-').replace(Symbols.POINT, decimalSeparator)

    private fun group(digits: String): String {
        if (groupingSize <= 0 || digits.length <= groupingSize) return digits
        val groups = ArrayList<String>()
        var end = digits.length
        var size = groupingSize
        while (end > size) {
            groups += digits.substring(end - size, end)
            end -= size
            size = secondaryGroupingSize
        }
        groups += digits.substring(0, end)
        return groups.asReversed().joinToString(groupingSeparator.toString())
    }

    private fun numberEnd(s: String, start: Int): Int {
        var i = start
        while (i < s.length && (s[i].isDigit() || s[i] == Symbols.POINT)) i++
        if (i < s.length && s[i] == Symbols.EXPONENT) {
            i++
            if (i < s.length && (s[i] == Symbols.MINUS || s[i] == '+')) i++
            while (i < s.length && s[i].isDigit()) i++
        }
        return i
    }

    companion object {
        fun forLocale(locale: Locale): DisplayFormatter {
            val symbols = DecimalFormatSymbols.getInstance(locale)
            val format = NumberFormat.getInstance(locale) as? DecimalFormat
            val primary = format?.groupingSize ?: 3
            return DisplayFormatter(
                decimalSeparator = symbols.decimalSeparator,
                groupingSeparator = symbols.groupingSeparator,
                groupingSize = if (format?.isGroupingUsed == false) 0 else primary,
                secondaryGroupingSize = format?.toPattern()?.let { secondaryGroupingOf(it) } ?: primary,
            )
        }

        /** "#,##,##0.###" (en-IN) has a secondary grouping of 2; "#,##0.###" has none. */
        internal fun secondaryGroupingOf(pattern: String): Int? {
            val integerPattern = pattern.substringBefore(';').substringBefore('.')
            val parts = integerPattern.split(',')
            return if (parts.size >= 3) parts[parts.size - 2].length else null
        }
    }
}

/** A formatted expression plus the caret mapping between internal and displayed positions. */
class FormattedExpression(val text: String, private val offsets: IntArray) {
    /** Where a caret before internal character [index] appears in [text]. */
    fun displayOffset(index: Int): Int = offsets[index.coerceIn(0, offsets.lastIndex)]

    /** The internal caret position closest to [displayOffset] in [text] (ties go right). */
    fun internalOffset(displayOffset: Int): Int {
        var best = 0
        for (i in offsets.indices) {
            if (abs(offsets[i] - displayOffset) <= abs(offsets[best] - displayOffset)) best = i
        }
        return best
    }
}
