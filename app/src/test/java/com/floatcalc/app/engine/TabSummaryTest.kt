package com.floatcalc.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabSummaryTest {

    private fun summary(expression: String, evaluated: Boolean = false) =
        TabSummary.of(CalcState(expression = expression, evaluated = evaluated))

    @Test
    fun emptyCalculatorShowsTheName() {
        assertNull(summary(""))
    }

    @Test
    fun showsTheCurrentResult() {
        assertEquals(TabSummary.Result("95"), summary("90+5"))
        assertEquals(TabSummary.Result("95"), summary("95", evaluated = true))
        assertEquals(TabSummary.Result("42"), summary("42"))
        assertEquals(TabSummary.Result("6"), summary("2×(3"))
    }

    @Test
    fun showsTheOperationBeingEntered() {
        assertEquals(TabSummary.Pending("12", "+"), summary("12+"))
        assertEquals(TabSummary.Pending("6", "×"), summary("2×3×"))
        assertEquals(TabSummary.Pending("5", "×−"), summary("5×−"))
        assertEquals(TabSummary.Pending("−3", "÷"), summary("−3÷"))
    }

    @Test
    fun nothingToShowFallsBackToTheName() {
        assertNull(summary("−"))
        assertNull(summary("5÷0"))
    }

    @Test
    fun shortensToFourDigits() {
        assertEquals("95", TabSummary.shorten("95"))
        assertEquals("1235", TabSummary.shorten("1234.56"))
        assertEquals("3.142", TabSummary.shorten("3.14159"))
        assertEquals("0.05", TabSummary.shorten("0.05"))
        assertEquals("0.333", TabSummary.shorten("0.3333333333333333"))
        assertEquals("−2.5", TabSummary.shorten("−2.5"))
        assertEquals("0", TabSummary.shorten("0"))
    }

    @Test
    fun tooLongForFourDigitsUsesScientificNotation() {
        assertEquals("1.23E4", TabSummary.shorten("12345"))
        assertEquals("1E4", TabSummary.shorten("9999.6"))
        assertEquals("1E−5", TabSummary.shorten("0.00001"))
        assertEquals("1.2E20", TabSummary.shorten("1.23E20"))
        assertEquals("−1E100", TabSummary.shorten("−1E100"))
    }
}
