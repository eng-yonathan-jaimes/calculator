package com.floatcalc.app.engine

import kotlinx.serialization.Serializable

/** Editable state of one calculation. Strings are in the internal notation. */
@Serializable
data class CalcState(
    val expression: String = "",
    /** True right after "=": [expression] holds the result. */
    val evaluated: Boolean = false,
    /** The expression that produced the current result, shown above it. */
    val previousExpression: String = "",
    /**
     * When the expression starts with a rounded result ([anchorText]), [anchorExact] is its full
     * precision value, so 1 ÷ 3 = × 3 = gives exactly 1.
     */
    val anchorText: String = "",
    val anchorExact: String = "",
    /** Gentle error shown after "=" failed; cleared by the next key. */
    val error: CalcError? = null,
)

sealed interface CalcKey {
    data class Digit(val digit: Int) : CalcKey {
        init {
            require(digit in 0..9)
        }
    }

    data object Decimal : CalcKey
    data class Op(val operator: Operator) : CalcKey
    data object Percent : CalcKey
    data object Parentheses : CalcKey
    data object ToggleSign : CalcKey
    data object Backspace : CalcKey
    data object Clear : CalcKey
    data object Equals : CalcKey
}

sealed interface Preview {
    data object None : Preview
    data class Value(val number: String) : Preview
    data class Error(val error: CalcError) : Preview
}

/** A completed calculation produced by "=". */
data class Evaluation(val expression: String, val result: String)

data class EditResult(val state: CalcState, val evaluation: Evaluation? = null)

/** Applies keypad input rules to a [CalcState]. Pure functions, no Android dependencies. */
object CalcEditor {
    private val plainNumber = Regex("^−?(\\d+\\.?\\d*|\\.\\d+)(E[+−-]?\\d+)?$")

    fun apply(state: CalcState, key: CalcKey): EditResult {
        val clean = state.copy(error = null)
        return when (key) {
            is CalcKey.Digit -> EditResult(digit(clean, key.digit))
            CalcKey.Decimal -> EditResult(decimal(clean))
            is CalcKey.Op -> EditResult(operator(clean, key.operator))
            CalcKey.Percent -> EditResult(percent(clean))
            CalcKey.Parentheses -> EditResult(parentheses(clean))
            CalcKey.ToggleSign -> EditResult(toggleSign(clean))
            CalcKey.Backspace -> EditResult(backspace(clean))
            CalcKey.Clear -> EditResult(CalcState())
            CalcKey.Equals -> equals(state)
        }.let { it.copy(state = it.state.withValidAnchor()) }
    }

    /** Live result shown under the expression while typing. */
    fun preview(state: CalcState): Preview {
        state.error?.let { return Preview.Error(it) }
        if (state.evaluated || state.expression.isEmpty() || isPlainNumber(state.expression)) return Preview.None
        return when (val result = evaluate(state)) {
            is EvalResult.Success -> Preview.Value(NumberFormatter.format(result.value))
            is EvalResult.Failure -> when (result.error) {
                CalcError.DIVIDE_BY_ZERO, CalcError.OVERFLOW -> Preview.Error(result.error)
                CalcError.INCOMPLETE, CalcError.MALFORMED -> Preview.None
            }
        }
    }

    /** The number a tap on the result copies: the result after "=", otherwise the live preview. */
    fun resultNumber(state: CalcState): String? = when {
        state.evaluated -> state.expression
        else -> when (val p = preview(state)) {
            is Preview.Value -> p.number
            else -> state.expression.takeIf { isPlainNumber(it) }
        }
    }

    /**
     * Inserts a number or expression (from paste or history) in the internal notation. It replaces
     * a finished result, follows an operator or '(' directly, and is multiplied in after a number.
     */
    fun insert(state: CalcState, text: String): CalcState {
        if (text.isEmpty()) return state
        val expression = state.expression
        if (state.evaluated || expression.isEmpty()) return CalcState(expression = text)
        val operand = if (isUnsignedNumber(text)) text else "${Symbols.OPEN}$text${Symbols.CLOSE}"
        val last = expression.last()
        val joined = if (Symbols.isOperator(last) || last == Symbols.OPEN) {
            expression + operand
        } else {
            expression + Symbols.TIMES + operand
        }
        return state.copy(expression = joined, error = null).withValidAnchor()
    }

    fun isPlainNumber(text: String): Boolean = plainNumber.matches(text)

    private fun isUnsignedNumber(text: String) = isPlainNumber(text) && !text.startsWith(Symbols.MINUS)

    private fun evaluate(state: CalcState): EvalResult = Calculator.evaluate(exactExpression(state))

    /** Swaps a rounded leading result for its exact value before evaluating. */
    private fun exactExpression(state: CalcState): String =
        if (state.hasValidAnchor()) {
            state.anchorExact + state.expression.substring(state.anchorText.length)
        } else {
            state.expression
        }

    private fun digit(state: CalcState, digit: Int): CalcState {
        val d = ('0' + digit).toString()
        if (state.evaluated) return CalcState(expression = d)
        val expression = state.expression
        val number = currentNumber(expression)
        // A lone leading zero is replaced rather than extended: "0" then "5" gives "5", not "05".
        if (number == "0") return state.copy(expression = expression.dropLast(1) + d)
        val prefix = if (endsWithClosedOperand(expression)) Symbols.TIMES.toString() else ""
        return state.copy(expression = expression + prefix + d)
    }

    private fun decimal(state: CalcState): CalcState {
        if (state.evaluated) return CalcState(expression = "0.")
        val expression = state.expression
        val number = currentNumber(expression)
        return when {
            number.contains(Symbols.POINT) || number.contains(Symbols.EXPONENT) -> state
            number.isNotEmpty() -> state.copy(expression = "$expression.")
            endsWithClosedOperand(expression) -> state.copy(expression = "$expression${Symbols.TIMES}0.")
            else -> state.copy(expression = "${expression}0.")
        }
    }

    private fun operator(state: CalcState, op: Operator): CalcState {
        val expression = state.expression
        if (state.evaluated) {
            return state.copy(expression = expression + op.symbol, evaluated = false, previousExpression = "")
        }
        if (expression.isEmpty()) {
            return if (op == Operator.MINUS) state.copy(expression = Symbols.MINUS.toString()) else state
        }
        val last = expression.last()
        return when {
            last == Symbols.OPEN -> if (op == Operator.MINUS) state.copy(expression = expression + op.symbol) else state
            Symbols.isOperator(last) && isUnaryMinusAt(expression, expression.length - 1) -> {
                // "5×−" then "+": drop the unary minus and treat "+" as replacing "×".
                if (op == Operator.MINUS) state else operator(state.copy(expression = expression.dropLast(1)), op)
            }
            Symbols.isOperator(last) -> {
                if (op == Operator.MINUS && (last == Symbols.TIMES || last == Symbols.DIVIDE)) {
                    state.copy(expression = expression + op.symbol)
                } else {
                    state.copy(expression = expression.dropLast(1) + op.symbol)
                }
            }
            else -> state.copy(expression = expression + op.symbol)
        }
    }

    private fun percent(state: CalcState): CalcState {
        val expression = state.expression
        if (state.evaluated) {
            return state.copy(expression = expression + Symbols.PERCENT, evaluated = false, previousExpression = "")
        }
        val last = expression.lastOrNull() ?: return state
        return if (last.isDigit() || last == Symbols.POINT || last == Symbols.CLOSE) {
            state.copy(expression = expression + Symbols.PERCENT)
        } else {
            state
        }
    }

    private fun parentheses(state: CalcState): CalcState {
        if (state.evaluated) return CalcState(expression = Symbols.OPEN.toString())
        val expression = state.expression
        val last = expression.lastOrNull()
        val open = expression.count { it == Symbols.OPEN } - expression.count { it == Symbols.CLOSE }
        val closesOperand = last != null && (last.isDigit() || last == Symbols.POINT || last == Symbols.CLOSE || last == Symbols.PERCENT)
        return when {
            last == Symbols.EXPONENT -> state
            closesOperand && open > 0 -> state.copy(expression = expression + Symbols.CLOSE)
            else -> state.copy(expression = expression + Symbols.OPEN)
        }
    }

    /**
     * Flips the sign of the last operand. A preceding binary + or − is swapped (3−5 → 3+5, which
     * equals 3−(−5)); otherwise a unary minus is added or removed.
     */
    private fun toggleSign(state: CalcState): CalcState {
        val expression = state.expression
        val start = operandStart(expression)
        val before = expression.getOrNull(start - 1)
        val toggled = when {
            before == null -> Symbols.MINUS + expression
            before == Symbols.MINUS && isUnaryMinusAt(expression, start - 1) ->
                expression.removeRange(start - 1, start)
            before == Symbols.MINUS -> expression.replaceRange(start - 1, start, Symbols.PLUS.toString())
            before == Symbols.PLUS -> expression.replaceRange(start - 1, start, Symbols.MINUS.toString())
            before == Symbols.TIMES || before == Symbols.DIVIDE || before == Symbols.OPEN ->
                expression.substring(0, start) + Symbols.MINUS + expression.substring(start)
            // After ")" or "%" the operand is multiplied implicitly, so wrap it: (2)5 → (2)(−5
            else -> expression.substring(0, start) + Symbols.OPEN + Symbols.MINUS + expression.substring(start)
        }
        if (!state.evaluated) return state.copy(expression = toggled)
        // Keep a toggled result usable as a result (and keep its exact value).
        val negatedExact = state.anchorExact.toBigDecimalOrNull()?.negate()
            ?.takeIf { state.hasValidAnchor() && state.anchorText == expression }
        return state.copy(
            expression = toggled,
            previousExpression = "",
            anchorText = if (negatedExact != null) toggled else "",
            anchorExact = negatedExact?.let { NumberFormatter.exact(it) } ?: "",
        )
    }

    private fun backspace(state: CalcState): CalcState =
        // After "=" the result is cleared as a whole, like the clear key.
        if (state.evaluated) CalcState() else state.copy(expression = state.expression.dropLast(1))

    private fun equals(state: CalcState): EditResult {
        val expression = state.expression
        if (expression.isEmpty() || state.evaluated) return EditResult(state.copy(error = null))
        return when (val result = evaluate(state)) {
            is EvalResult.Failure -> EditResult(state.copy(error = result.error))
            is EvalResult.Success -> {
                val formatted = NumberFormatter.format(result.value)
                val isCalculation = !isPlainNumber(expression)
                EditResult(
                    state = CalcState(
                        expression = formatted,
                        evaluated = true,
                        previousExpression = if (isCalculation) expression else "",
                        anchorText = formatted,
                        anchorExact = NumberFormatter.exact(result.value),
                    ),
                    evaluation = if (isCalculation) Evaluation(expression, formatted) else null,
                )
            }
        }
    }

    private fun CalcState.hasValidAnchor(): Boolean {
        if (anchorText.isEmpty() || !expression.startsWith(anchorText)) return false
        val next = expression.getOrNull(anchorText.length) ?: return true
        return !(next.isDigit() || next == Symbols.POINT || next == Symbols.EXPONENT)
    }

    private fun CalcState.withValidAnchor(): CalcState =
        if (anchorText.isEmpty() || hasValidAnchor()) this else copy(anchorText = "", anchorExact = "")

    /** The digits/point/exponent of the number at the end of [expression] ("" if none). */
    private fun currentNumber(expression: String): String {
        var i = expression.length
        while (i > 0 && expression[i - 1].isDigit()) i--
        if (i >= 2 && (expression[i - 1] == Symbols.MINUS || expression[i - 1] == '+') && expression[i - 2] == Symbols.EXPONENT) {
            i -= 2
        } else if (i >= 1 && expression[i - 1] == Symbols.EXPONENT) {
            i -= 1
        }
        while (i > 0 && (expression[i - 1].isDigit() || expression[i - 1] == Symbols.POINT)) i--
        return expression.substring(i)
    }

    /** Index where the trailing operand (number or parenthesized group, plus any '%') starts. */
    private fun operandStart(expression: String): Int {
        var i = expression.length
        while (i > 0 && expression[i - 1] == Symbols.PERCENT) i--
        if (i > 0 && expression[i - 1] == Symbols.CLOSE) {
            var depth = 0
            var j = i - 1
            while (j >= 0) {
                when (expression[j]) {
                    Symbols.CLOSE -> depth++
                    Symbols.OPEN -> if (--depth == 0) return j
                }
                j--
            }
            return 0
        }
        return i - currentNumber(expression.substring(0, i)).length
    }

    private fun endsWithClosedOperand(expression: String): Boolean {
        val last = expression.lastOrNull() ?: return false
        return last == Symbols.CLOSE || last == Symbols.PERCENT
    }

    /** A '−' at [index] is unary when it starts the expression or follows an operator or '('. */
    private fun isUnaryMinusAt(expression: String, index: Int): Boolean {
        if (expression.getOrNull(index) != Symbols.MINUS) return false
        val before = expression.getOrNull(index - 1) ?: return true
        return Symbols.isOperator(before) || before == Symbols.OPEN
    }
}
