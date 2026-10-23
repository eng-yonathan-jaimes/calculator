package com.floatcalc.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasteParserTest {

    private fun en(text: String) = PasteParser.parse(text, decimalSeparator = '.', groupingSeparator = ',')
    private fun de(text: String) = PasteParser.parse(text, decimalSeparator = ',', groupingSeparator = '.')

    @Test
    fun plainNumbers() {
        assertEquals("1234", en("1234"))
        assertEquals("1234.56", en("  1234.56 "))
        assertEquals("0.5", en(".5"))
        assertEquals("−5", en("-5"))
    }

    @Test
    fun stripsGroupingSeparatorsAndSpaces() {
        assertEquals("1234.56", en("1,234.56"))
        assertEquals("1234567.89", en("1,234,567.89"))
        assertEquals("1234567", en("1 234 567"))
        assertEquals("1234567", en("1'234'567"))
        assertEquals("1234.5", de("1 234,5"))
        assertEquals("1234.5", de("1 234,5"))
    }

    @Test
    fun stripsCurrencySymbols() {
        assertEquals("1234.56", en("$1,234.56"))
        assertEquals("1234.56", en("€ 1.234,56"))
        assertEquals("1234.56", de("1 234,56 €"))
        assertEquals("99", en("£99"))
        assertEquals("1500", en("¥1,500"))
    }

    @Test
    fun resolvesAmbiguousSeparatorsWithTheLocale() {
        assertEquals("1234", en("1,234"))
        assertEquals("1.234", en("1.234"))
        assertEquals("1.234", de("1,234"))
        assertEquals("1234", de("1.234"))
        assertEquals("1.5", en("1,5"))
        assertEquals("1.5", de("1.5"))
        assertEquals("1234567", de("1.234.567"))
        assertEquals("1234.56", de("1.234,56"))
    }

    @Test
    fun simpleExpressions() {
        assertEquals("12×3+4", en("12*3+4"))
        assertEquals("10÷4", en("10/4"))
        assertEquals("2×3", en("2x3"))
        assertEquals("(1+2)×3", en("(1 + 2) * 3"))
        assertEquals("12+3", en("12+3="))
        assertEquals("50%", en("50%"))
        assertEquals("1E3", en("1e3"))
        assertEquals("5−2", en("5 – 2"))
    }

    @Test
    fun keepsExpressionsThatOnlyFailAtEvaluation() {
        assertEquals("5÷0", en("5/0"))
    }

    @Test
    fun rejectsUnusableText() {
        assertNull(en(""))
        assertNull(en("   "))
        assertNull(en("abc"))
        assertNull(en("hello 12"))
        assertNull(en("12+"))
        assertNull(en("*3"))
        assertNull(en("1,2.3,4"))
        assertNull(en("$"))
        assertNull(en("1".repeat(500)))
    }
}
