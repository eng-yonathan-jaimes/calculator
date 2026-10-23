package com.floatcalc.app.engine

/**
 * Turns clipboard text into an internal-notation number or simple expression, or null when
 * nothing usable is there. Spaces, currency symbols and grouping separators are removed, and
 * * and / become × and ÷.
 */
object PasteParser {
    private const val MAX_LENGTH = 200

    fun parse(raw: String, decimalSeparator: Char = '.', groupingSeparator: Char = ','): String? {
        val text = raw.trim().removeSuffix("=").trim()
        if (text.isEmpty() || text.length > MAX_LENGTH) return null

        val mapped = StringBuilder()
        for (c in text) {
            when {
                c.isWhitespace() || c.isSpaceSeparator() -> Unit
                Character.getType(c) == Character.CURRENCY_SYMBOL.toInt() -> Unit
                c == '\'' || c == '’' -> Unit // Swiss-style grouping: 1'234'567
                c in '0'..'9' || c == '.' || c == ',' -> mapped.append(c)
                c == '+' -> mapped.append(Symbols.PLUS)
                c == '-' || c == '−' || c == '–' || c == '—' -> mapped.append(Symbols.MINUS)
                c == '*' || c == 'x' || c == 'X' || c == '×' || c == '·' || c == '∙' -> mapped.append(Symbols.TIMES)
                c == '/' || c == '÷' || c == '∕' -> mapped.append(Symbols.DIVIDE)
                c == '%' || c == '(' || c == ')' -> mapped.append(c)
                c == 'e' || c == 'E' -> mapped.append(Symbols.EXPONENT)
                else -> return null
            }
        }
        val expression = normalizeNumbers(mapped.toString(), decimalSeparator, groupingSeparator) ?: return null
        if (expression.isEmpty()) return null
        return when (val result = Calculator.evaluate(expression)) {
            is EvalResult.Success -> expression
            is EvalResult.Failure -> when (result.error) {
                CalcError.INCOMPLETE, CalcError.MALFORMED -> null
                CalcError.DIVIDE_BY_ZERO, CalcError.OVERFLOW -> expression
            }
        }
    }

    /** Rewrites each run of digits, '.' and ',' with '.' as the only (decimal) separator. */
    private fun normalizeNumbers(s: String, decimalSeparator: Char, groupingSeparator: Char): String? {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c in '0'..'9' || c == '.' || c == ',') {
                var end = i
                while (end < s.length && (s[end] in '0'..'9' || s[end] == '.' || s[end] == ',')) end++
                out.append(normalizeNumber(s.substring(i, end), decimalSeparator, groupingSeparator) ?: return null)
                i = end
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    private fun normalizeNumber(chunk: String, decimalSeparator: Char, groupingSeparator: Char): String? {
        val dots = chunk.count { it == '.' }
        val commas = chunk.count { it == ',' }
        val decimal: Char? = when {
            dots > 0 && commas > 0 -> chunk.last { it == '.' || it == ',' }
            dots + commas == 0 -> null
            else -> {
                val sep = if (dots > 0) '.' else ','
                val count = dots + commas
                val digitsAfter = chunk.length - chunk.lastIndexOf(sep) - 1
                when {
                    count > 1 -> null // 1,234,567 or 1.234.567: grouping only
                    sep == decimalSeparator -> sep
                    sep == groupingSeparator && digitsAfter == 3 -> null
                    else -> sep // e.g. "1,5" in an English locale is still a decimal comma
                }
            }
        }
        if (decimal == null) return chunk.filter { it in '0'..'9' }.ifEmpty { null }
        val grouping = if (decimal == '.') ',' else '.'
        val withoutGrouping = chunk.filter { it != grouping }
        if (withoutGrouping.count { it == decimal } > 1) return null
        val normalized = withoutGrouping.replace(decimal, '.')
        return if (normalized.startsWith('.')) "0$normalized" else normalized
    }

    private fun Char.isSpaceSeparator() = Character.getType(this) == Character.SPACE_SEPARATOR.toInt()
}
