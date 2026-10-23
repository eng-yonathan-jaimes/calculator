package com.floatcalc.app.engine

import java.math.BigDecimal
import java.math.MathContext

sealed interface EvalResult {
    data class Success(val value: BigDecimal) : EvalResult
    data class Failure(val error: CalcError) : EvalResult
}

/**
 * Evaluates expressions in the internal notation with a tokenizer and a recursive-descent parser.
 * All arithmetic is BigDecimal with [MathContext.DECIMAL128], so 0.1 + 0.2 is exactly 0.3.
 *
 * Grammar:
 * ```
 * expression := term (('+' | '−') term)*
 * term       := unary (('×' | '÷') unary | postfix)*      // a bare postfix is implicit ×
 * unary      := ('−' | '+') unary | postfix
 * postfix    := primary '%'*
 * primary    := NUMBER | '(' expression [')']            // missing ')' at the end is auto-closed
 * ```
 */
object Calculator {
    private val MC: MathContext = MathContext.DECIMAL128

    fun evaluate(expression: String): EvalResult = try {
        val value = eval(Parser(Tokenizer.tokenize(expression)).parse())
        checkRange(value)
    } catch (e: CalcException) {
        EvalResult.Failure(e.error)
    } catch (_: ArithmeticException) {
        EvalResult.Failure(CalcError.OVERFLOW)
    } catch (_: StackOverflowError) {
        EvalResult.Failure(CalcError.MALFORMED)
    }

    private fun checkRange(value: BigDecimal): EvalResult {
        if (value.signum() == 0) return EvalResult.Success(BigDecimal.ZERO)
        val exponent = value.precision() - value.scale() - 1
        return when {
            exponent > Tokenizer.MAX_EXPONENT -> EvalResult.Failure(CalcError.OVERFLOW)
            exponent < -Tokenizer.MAX_EXPONENT -> EvalResult.Success(BigDecimal.ZERO)
            else -> EvalResult.Success(value)
        }
    }

    private fun eval(node: Node): BigDecimal = when (node) {
        is Node.Num -> node.value
        is Node.Negate -> eval(node.operand).negate()
        is Node.Percent -> eval(node.operand).movePointLeft(2)
        is Node.Binary -> evalBinary(node)
    }

    private fun evalBinary(node: Node.Binary): BigDecimal {
        val a = eval(node.left)
        val right = node.right
        return when (node.operator) {
            Operator.PLUS, Operator.MINUS -> {
                // a ± b% means a ± (a·b/100); any other right-hand side is used as is.
                val b = if (right is Node.Percent) {
                    a.multiply(eval(right.operand), MC).movePointLeft(2)
                } else {
                    eval(right)
                }
                if (node.operator == Operator.PLUS) a.add(b, MC) else a.subtract(b, MC)
            }
            Operator.TIMES -> a.multiply(eval(right), MC)
            Operator.DIVIDE -> {
                val b = eval(right)
                if (b.signum() == 0) throw CalcException(CalcError.DIVIDE_BY_ZERO)
                a.divide(b, MC)
            }
        }
    }
}

internal sealed interface Node {
    data class Num(val value: BigDecimal) : Node
    data class Negate(val operand: Node) : Node
    data class Percent(val operand: Node) : Node
    data class Binary(val operator: Operator, val left: Node, val right: Node) : Node
}

internal class Parser(private val tokens: List<Token>) {
    private var pos = 0

    fun parse(): Node {
        val node = expression()
        if (pos < tokens.size) throw CalcException(CalcError.MALFORMED)
        return node
    }

    private fun peek(): Token? = tokens.getOrNull(pos)

    private fun expression(): Node {
        var left = term()
        while (true) {
            val t = peek()
            if (t is Token.Op && (t.operator == Operator.PLUS || t.operator == Operator.MINUS)) {
                pos++
                left = Node.Binary(t.operator, left, term())
            } else {
                return left
            }
        }
    }

    private fun term(): Node {
        var left = unary()
        while (true) {
            val t = peek()
            left = when {
                t is Token.Op && (t.operator == Operator.TIMES || t.operator == Operator.DIVIDE) -> {
                    pos++
                    Node.Binary(t.operator, left, unary())
                }
                startsImplicitFactor(t) -> Node.Binary(Operator.TIMES, left, postfix())
                else -> return left
            }
        }
    }

    /** 2(3), (1)(2), (1)2 and 5%3 multiply implicitly; two bare numbers in a row do not. */
    private fun startsImplicitFactor(t: Token?): Boolean = when (t) {
        Token.Open -> true
        is Token.Number -> tokens[pos - 1] !is Token.Number
        else -> false
    }

    private fun unary(): Node {
        val t = peek()
        if (t is Token.Op && t.operator == Operator.MINUS) {
            pos++
            return Node.Negate(unary())
        }
        if (t is Token.Op && t.operator == Operator.PLUS) {
            pos++
            return unary()
        }
        return postfix()
    }

    private fun postfix(): Node {
        var node = primary()
        while (peek() == Token.Percent) {
            pos++
            node = Node.Percent(node)
        }
        return node
    }

    private fun primary(): Node {
        val t = peek() ?: throw CalcException(CalcError.INCOMPLETE)
        pos++
        return when (t) {
            is Token.Number -> Node.Num(t.value)
            Token.Open -> {
                val inner = expression()
                when (peek()) {
                    Token.Close -> pos++
                    null -> Unit // auto-close at the end of the input
                    else -> throw CalcException(CalcError.MALFORMED)
                }
                inner
            }
            else -> throw CalcException(CalcError.MALFORMED)
        }
    }
}
