package com.floatcalc.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class FormattingTest {

    private fun format(value: String) = NumberFormatter.format(BigDecimal(value))

    @Test
    fun dropsTrailingZeros() {
        assertEquals("5", format("5.000"))
        assertEquals("1234567.89", format("1234567.890"))
        assertEquals("100", format("100"))
        assertEquals("0", format("0.000"))
        assertEquals("0", format("-0"))
    }

    @Test
    fun negativeNumbersUseTheMinusSign() {
        assertEquals("−0.5", format("-0.5"))
        assertEquals("−1E20", format("-1E20"))
    }

    @Test
    fun roundsToSixteenSignificantDigits() {
        assertEquals("123456789.1234568", format("123456789.123456789"))
        assertEquals("0.3333333333333333", format("0.33333333333333333333333333"))
        assertEquals("1", format("0.9999999999999999999999"))
    }

    @Test
    fun scientificOnlyWhenTooLong() {
        assertEquals("9999999999999999", format("9999999999999999"))
        assertEquals("1E16", format("1E16"))
        assertEquals("1.23456789E19", format("12345678901234567890"))
        assertEquals("0.000000001", format("1E-9"))
        assertEquals("0.000123", format("0.000123"))
        assertEquals("1.5E−10", format("1.5E-10"))
    }

    @Test
    fun formattedScientificResultsCanBeParsedAgain() {
        val formatted = format("1.5E-10")
        val reparsed = Calculator.evaluate(formatted) as EvalResult.Success
        assertEquals(0, BigDecimal("1.5E-10").compareTo(reparsed.value))
    }

    private val us = DisplayFormatter(decimalSeparator = '.', groupingSeparator = ',')
    private val german = DisplayFormatter(decimalSeparator = ',', groupingSeparator = '.')
    private val indian = DisplayFormatter('.', ',', groupingSize = 3, secondaryGroupingSize = 2)

    @Test
    fun groupsIntegerDigitsForDisplay() {
        assertEquals("1,234,567.5", us.formatNumber("1234567.5"))
        assertEquals("−1,234", us.formatNumber("−1234"))
        assertEquals("123", us.formatNumber("123"))
        assertEquals("1,234.", us.formatNumber("1234."))
        assertEquals("0.0001234", us.formatNumber("0.0001234"))
        assertEquals("1.5E−10", us.formatNumber("1.5E−10"))
    }

    @Test
    fun usesLocaleSeparators() {
        assertEquals("1.234.567,5", german.formatNumber("1234567.5"))
        assertEquals("1,23,45,678", indian.formatNumber("12345678"))
    }

    @Test
    fun formatsEveryNumberInAnExpression() {
        assertEquals("1,234+5,678.9×2", us.formatExpression("1234+5678.9×2"))
        assertEquals("(1.000−0,5)%", german.formatExpression("(1000−0.5)%"))
        assertEquals("2E−3+1,000", us.formatExpression("2E−3+1000"))
    }

    @Test
    fun clipboardNumberIsPlain() {
        assertEquals("-1234.5", us.toClipboardNumber("−1234.5"))
        assertEquals("-1234,5", german.toClipboardNumber("−1234.5"))
    }

    @Test
    fun readsSeparatorsFromTheLocale() {
        val usLocale = DisplayFormatter.forLocale(Locale.US)
        assertEquals('.', usLocale.decimalSeparator)
        assertEquals(',', usLocale.groupingSeparator)
        val germany = DisplayFormatter.forLocale(Locale.GERMANY)
        assertEquals(',', germany.decimalSeparator)
        assertEquals('.', germany.groupingSeparator)
        assertEquals("1.234,5", germany.formatNumber("1234.5"))
    }

    @Test
    fun parsesSecondaryGrouping() {
        assertEquals(2, DisplayFormatter.secondaryGroupingOf("#,##,##0.###"))
        assertNull(DisplayFormatter.secondaryGroupingOf("#,##0.###"))
    }

    @Test
    fun mapsCaretPositionsAcrossGroupingSeparators() {
        val mapped = us.formatExpressionMapped("1234+5")
        assertEquals("1,234+5", mapped.text)
        assertEquals(listOf(0, 2, 3, 4, 5, 6, 7), (0..6).map { mapped.displayOffset(it) })
        assertEquals(1, mapped.internalOffset(1))
        assertEquals(1, mapped.internalOffset(2))
        assertEquals(4, mapped.internalOffset(5))
        assertEquals(6, mapped.internalOffset(7))
        val decimalComma = german.formatExpressionMapped("1234.5")
        assertEquals("1.234,5", decimalComma.text)
        assertEquals(5, decimalComma.displayOffset(4))
        assertEquals(6, decimalComma.displayOffset(5))
    }
}
