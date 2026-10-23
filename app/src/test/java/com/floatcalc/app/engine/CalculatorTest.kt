package com.floatcalc.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorTest {

    /** Evaluates and formats, or returns the error name. */
    private fun eval(expression: String): String = when (val r = Calculator.evaluate(expression)) {
        is EvalResult.Success -> NumberFormatter.format(r.value)
        is EvalResult.Failure -> r.error.name
    }

    @Test
    fun precedence() {
        assertEquals("14", eval("2+3×4"))
        assertEquals("−10", eval("2−3×4"))
        assertEquals("5", eval("10÷4×2"))
        assertEquals("5", eval("2+12÷4"))
        assertEquals("3", eval("8−3−2"))
        assertEquals("8", eval("64÷4÷2"))
        assertEquals("7", eval("1+2×3"))
    }

    @Test
    fun asciiOperatorsAreAccepted() {
        assertEquals("10", eval("2*3+8/2"))
        assertEquals("−1", eval("2-3"))
    }

    @Test
    fun parentheses() {
        assertEquals("20", eval("(2+3)×4"))
        assertEquals("21", eval("((1+2)×(3+4))"))
        assertEquals("2", eval("(((2)))"))
    }

    @Test
    fun missingClosingParenthesesAreAutoClosed() {
        assertEquals("14", eval("2×(3+4"))
        assertEquals("2", eval("((2"))
    }

    @Test
    fun implicitMultiplication() {
        assertEquals("14", eval("2(3+4)"))
        assertEquals("21", eval("(1+2)(3+4)"))
        assertEquals("6", eval("(2)3"))
        assertEquals("9", eval("6÷2(1+2)"))
        assertEquals("1", eval("50%2"))
    }

    @Test
    fun unaryMinus() {
        assertEquals("−5", eval("−5"))
        assertEquals("−2", eval("−5+3"))
        assertEquals("−6", eval("3×−2"))
        assertEquals("−5", eval("−(2+3)"))
        assertEquals("5", eval("2−−3"))
        assertEquals("5", eval("−−5"))
        assertEquals("5", eval("+5"))
        assertEquals("6", eval("−2×−3"))
    }

    @Test
    fun percentAddsOrSubtractsAPercentageOfTheLeftSide() {
        assertEquals("220", eval("200+10%"))
        assertEquals("180", eval("200−10%"))
        assertEquals("231", eval("200+10%+5%"))
    }

    @Test
    fun percentInMultiplicationAndDivision() {
        assertEquals("20", eval("200×10%"))
        assertEquals("2000", eval("200÷10%"))
        assertEquals("DIVIDE_BY_ZERO", eval("200÷0%"))
    }

    @Test
    fun lonePercentIsHundredth() {
        assertEquals("0.1", eval("10%"))
        assertEquals("0.5", eval("50%"))
        assertEquals("−0.1", eval("−10%"))
        assertEquals("0.0005", eval("5%%"))
    }

    @Test
    fun percentInsideLargerExpressions() {
        assertEquals("440", eval("(200+10%)×2"))
        assertEquals("200.2", eval("200+10%×2"))
        assertEquals("110", eval("100+(10%)"))
    }

    @Test
    fun decimalPrecision() {
        assertEquals("0.3", eval("0.1+0.2"))
        assertEquals("0.1", eval("1−0.9"))
        assertEquals("0.3", eval("0.1×3"))
        assertEquals("1.21", eval("1.1×1.1"))
        assertEquals("0.3333333333333333", eval("1÷3"))
        assertEquals("0.6666666666666667", eval("2÷3"))
        assertEquals("1", eval("1÷3×3"))
        assertEquals("3", eval("3."))
        assertEquals("1", eval(".5+.5"))
    }

    @Test
    fun divideByZero() {
        assertEquals("DIVIDE_BY_ZERO", eval("5÷0"))
        assertEquals("DIVIDE_BY_ZERO", eval("0÷0"))
        assertEquals("DIVIDE_BY_ZERO", eval("5÷(2−2)"))
        assertEquals("DIVIDE_BY_ZERO", eval("1+2÷0×3"))
    }

    @Test
    fun incompleteExpressions() {
        listOf("", "2+", "(", "2×", "−", "1.5E", "1.5E−", "(2+").forEach {
            assertEquals("for '$it'", "INCOMPLETE", eval(it))
        }
    }

    @Test
    fun malformedExpressions() {
        listOf("×2", "2××3", "1.2.3", "()", "2)", ".", "abc", "2 3", "%5", "2+)").forEach {
            assertEquals("for '$it'", "MALFORMED", eval(it))
        }
    }

    @Test
    fun scientificNotationInput() {
        assertEquals("1500", eval("1.5E3"))
        assertEquals("0.002", eval("2E−3"))
        assertEquals("0.002", eval("2E-3"))
        assertEquals("1000", eval("1E+3"))
        assertEquals("OVERFLOW", eval("1E10000"))
        assertEquals("OVERFLOW", eval("1E9999×1E9999"))
    }

    @Test
    fun deepNestingNeverThrows() {
        val result = Calculator.evaluate("(".repeat(20_000) + "1")
        assertTrue(result is EvalResult.Success || result == EvalResult.Failure(CalcError.MALFORMED))
    }
}
