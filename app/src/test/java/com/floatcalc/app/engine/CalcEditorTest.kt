package com.floatcalc.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcEditorTest {

    private fun key(label: String): CalcKey = when (label) {
        "." -> CalcKey.Decimal
        "+" -> CalcKey.Op(Operator.PLUS)
        "−" -> CalcKey.Op(Operator.MINUS)
        "×" -> CalcKey.Op(Operator.TIMES)
        "÷" -> CalcKey.Op(Operator.DIVIDE)
        "%" -> CalcKey.Percent
        "()" -> CalcKey.Parentheses
        "±" -> CalcKey.ToggleSign
        "⌫" -> CalcKey.Backspace
        "AC" -> CalcKey.Clear
        "=" -> CalcKey.Equals
        else -> CalcKey.Digit(label.toInt())
    }

    /** Types space-separated keys, e.g. "1 2 + 3 =". */
    private fun type(keys: String, start: CalcState = CalcState()): CalcState =
        keys.split(' ').filter { it.isNotEmpty() }.fold(start) { state, k -> CalcEditor.apply(state, key(k)).state }

    private fun expr(keys: String) = type(keys).expression

    @Test
    fun livePreviewWhileTyping() {
        assertEquals(Preview.Value("5"), CalcEditor.preview(type("2 + 3")))
        assertEquals(Preview.None, CalcEditor.preview(type("2 +")))
        assertEquals(Preview.None, CalcEditor.preview(type("5")))
        assertEquals(Preview.Error(CalcError.DIVIDE_BY_ZERO), CalcEditor.preview(type("5 ÷ 0")))
        assertEquals(Preview.Value("14"), CalcEditor.preview(type("2 × () 3 + 4")))
    }

    @Test
    fun equalsShowsResultAndRecordsTheCalculation() {
        val result = CalcEditor.apply(type("2 + 3"), CalcKey.Equals)
        assertEquals("5", result.state.expression)
        assertTrue(result.state.evaluated)
        assertEquals("2+3", result.state.previousExpression)
        assertEquals(Evaluation("2+3", "5"), result.evaluation)
        assertEquals(Preview.None, CalcEditor.preview(result.state))
    }

    @Test
    fun equalsOnAPlainNumberRecordsNothing() {
        assertNull(CalcEditor.apply(type("5"), CalcKey.Equals).evaluation)
    }

    @Test
    fun incompleteEqualsKeepsTheExpressionAndShowsAGentleError() {
        val state = type("2 + =")
        assertEquals("2+", state.expression)
        assertEquals(CalcError.INCOMPLETE, state.error)
        assertEquals(Preview.Error(CalcError.INCOMPLETE), CalcEditor.preview(state))
        val next = type("3", state)
        assertNull(next.error)
        assertEquals("2+3", next.expression)
    }

    @Test
    fun divideByZeroOnEquals() {
        val state = type("5 ÷ 0 =")
        assertEquals("5÷0", state.expression)
        assertEquals(CalcError.DIVIDE_BY_ZERO, state.error)
        assertFalse(state.evaluated)
    }

    @Test
    fun afterEqualsOperatorContinuesAndDigitStartsFresh() {
        assertEquals("5×2", expr("2 + 3 = × 2"))
        assertEquals("7", expr("2 + 3 = 7"))
        assertEquals("0.", expr("2 + 3 = ."))
        assertEquals("5%", expr("2 + 3 = %"))
        assertEquals("(", expr("2 + 3 = ()"))
        assertEquals("", expr("2 + 3 = ⌫"))
    }

    @Test
    fun chainedResultsStayExact() {
        assertEquals("1", expr("1 ÷ 3 = × 3 ="))
        assertEquals("2", expr("2 ÷ 3 = × 3 ="))
    }

    @Test
    fun editingTheResultDropsItsExactValue() {
        // "0.6666666666666667" loses its last digit, so the typed digits are used from then on.
        val state = type("2 ÷ 3 = × ⌫ ⌫ × 3")
        assertEquals("0.666666666666666×3", state.expression)
        assertEquals(Preview.Value("1.999999999999998"), CalcEditor.preview(state))
    }

    @Test
    fun newOperatorReplacesTrailingOne() {
        assertEquals("5×", expr("5 + ×"))
        assertEquals("5−", expr("5 + −"))
        assertEquals("5×−", expr("5 × −"))
        assertEquals("5+", expr("5 × − +"))
        assertEquals("5×−", expr("5 × − −"))
    }

    @Test
    fun operatorsAtTheStartAndAfterParenthesis() {
        assertEquals("−", expr("−"))
        assertEquals("", expr("+"))
        assertEquals("", expr("×"))
        assertEquals("(−", expr("() −"))
        assertEquals("(", expr("() ×"))
    }

    @Test
    fun oneDecimalPointPerNumber() {
        assertEquals("1.2", expr("1 . 2 ."))
        assertEquals("0.", expr("."))
        assertEquals("5+0.", expr("5 + ."))
        assertEquals("1.5+2.5", expr("1 . 5 + 2 . 5 ."))
        assertEquals("(2)×0.", expr("() 2 () ."))
    }

    @Test
    fun leadingZeroIsReplaced() {
        assertEquals("5", expr("0 5"))
        assertEquals("0", expr("0 0"))
        assertEquals("0.05", expr("0 . 0 5"))
        assertEquals("3+5", expr("3 + 0 5"))
        assertEquals("100", expr("1 0 0"))
    }

    @Test
    fun smartParentheses() {
        assertEquals("(", expr("()"))
        assertEquals("(2)", expr("() 2 ()"))
        assertEquals("2(", expr("2 ()"))
        assertEquals("(2+(", expr("() 2 + ()"))
        assertEquals("(2)(", expr("() 2 () ()"))
        assertEquals("((2))", expr("() () 2 () ()"))
        assertEquals("(5%)", expr("() 5 % ()"))
    }

    @Test
    fun digitAfterClosingParenthesisMultiplies() {
        assertEquals("(2)×3", expr("() 2 () 3"))
        assertEquals("5%×3", expr("5 % 3"))
    }

    @Test
    fun toggleSignFlipsTheCurrentNumber() {
        assertEquals("−5", expr("5 ±"))
        assertEquals("5", expr("5 ± ±"))
        assertEquals("3+5", expr("3 − 5 ±"))
        assertEquals("3−5", expr("3 + 5 ±"))
        assertEquals("2×−5", expr("2 × 5 ±"))
        assertEquals("2×5", expr("2 × 5 ± ±"))
        assertEquals("(−5", expr("() 5 ±"))
        assertEquals("−", expr("±"))
        assertEquals("5×−", expr("5 × ±"))
        assertEquals("−(2+3)", expr("() 2 + 3 () ±"))
        assertEquals("50−10%", expr("5 0 + 1 0 % ±"))
    }

    @Test
    fun toggleSignOnAResultKeepsItAResult() {
        val state = type("1 ÷ 3 = ±")
        assertEquals("−0.3333333333333333", state.expression)
        assertTrue(state.evaluated)
        assertEquals("−1", type("× 3 =", state).expression)
        assertEquals("7", type("7", state).expression)
    }

    @Test
    fun percentRules() {
        assertEquals("50%", expr("5 0 %"))
        assertEquals("50%", expr("5 0 % %"))
        assertEquals("", expr("%"))
        assertEquals("5+", expr("5 + %"))
        assertEquals(Preview.Value("220"), CalcEditor.preview(type("2 0 0 + 1 0 %")))
    }

    @Test
    fun backspaceAndClear() {
        assertEquals("12", expr("1 2 3 ⌫"))
        assertEquals("", expr("1 ⌫ ⌫"))
        assertEquals(CalcState(), type("1 + 2 AC"))
    }

    @Test
    fun resultNumberIsWhatATapCopies() {
        assertEquals("5", CalcEditor.resultNumber(type("2 + 3")))
        assertEquals("5", CalcEditor.resultNumber(type("2 + 3 =")))
        assertEquals("42", CalcEditor.resultNumber(type("4 2")))
        assertNull(CalcEditor.resultNumber(type("4 +")))
    }

    @Test
    fun insertPastedText() {
        assertEquals("12", CalcEditor.insert(CalcState(), "12").expression)
        assertEquals("5+12", CalcEditor.insert(type("5 +"), "12").expression)
        assertEquals("5×12", CalcEditor.insert(type("5"), "12").expression)
        assertEquals("5−(−3)", CalcEditor.insert(type("5 −"), "−3").expression)
        assertEquals("2×(12×3+4)", CalcEditor.insert(type("2 ×"), "12×3+4").expression)
        assertEquals("(7", CalcEditor.insert(type("()"), "7").expression)
        val replaced = CalcEditor.insert(type("1 + 1 ="), "99")
        assertEquals("99", replaced.expression)
        assertFalse(replaced.evaluated)
    }
}
