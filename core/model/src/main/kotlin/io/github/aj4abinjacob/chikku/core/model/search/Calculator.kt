package io.github.aj4abinjacob.chikku.core.model.search

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.pow

/** Outcome of [Calculator.evaluate]. */
sealed interface CalculationResult {
    /** [display] is [value] formatted for the search row (at most [Calculator.SIGNIFICANT_DIGITS] digits). */
    data class Value(val value: Double, val display: String) : CalculationResult

    /** The text is an arithmetic expression but has no finite value. */
    data class Error(val reason: Reason) : CalculationResult

    /** The text is not an arithmetic expression (a word, a lone number, or malformed); show no row. */
    data object NotAnExpression : CalculationResult

    enum class Reason { DIVISION_BY_ZERO, OVERFLOW, UNDEFINED }
}

/**
 * Offline quick calculation for the search field.
 *
 * Grammar, with the usual precedence; `^` is right-associative and binds tighter than unary minus,
 * so `-2^2 = -4` and `2^-1 = 0.5`:
 * ```
 * expr  := term (('+' | '-') term)*
 * term  := unary (('*' | '/' | '%') unary)*
 * unary := ('-' | '+') unary | power
 * power := atom ('^' unary)?
 * atom  := number | '(' expr ')'
 * ```
 * `%` is the remainder operator. `×`, `÷` and `−` are accepted as `*`, `/` and `-`; whitespace is
 * ignored. Input without a binary operator ("42", "-3", "(7)") is [CalculationResult.NotAnExpression].
 * Pure, never throws, and bounded in length and nesting depth for hostile input.
 */
object Calculator {
    const val SIGNIFICANT_DIGITS = 12
    private const val MAX_LENGTH = 256
    private const val MAX_DEPTH = 64

    fun evaluate(input: String): CalculationResult {
        val text = input.trim()
        if (text.isEmpty() || text.length > MAX_LENGTH) return CalculationResult.NotAnExpression
        val tokens = tokenize(text) ?: return CalculationResult.NotAnExpression
        val parser = Parser(tokens)
        val value = try {
            parser.parseExpression()
        } catch (_: SyntaxError) {
            return CalculationResult.NotAnExpression
        } catch (e: ArithmeticFailure) {
            return CalculationResult.Error(e.reason)
        }
        if (!parser.atEnd() || !parser.sawBinaryOperator) return CalculationResult.NotAnExpression
        return CalculationResult.Value(value, format(value))
    }

    /** Rounds to [SIGNIFICANT_DIGITS] and drops trailing zeros; scientific notation outside 1e-9..1e15. */
    fun format(value: Double): String {
        if (value == 0.0) return "0" // also turns -0.0 into "0"
        val rounded = BigDecimal(value).round(MathContext(SIGNIFICANT_DIGITS, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        val magnitude = abs(value)
        return if (magnitude >= 1e15 || magnitude < 1e-9) rounded.toString() else rounded.toPlainString()
    }

    private sealed interface Token {
        data class Num(val value: Double) : Token
        data class Op(val c: Char) : Token
    }

    /** Null when [text] contains anything but numbers, operators, parentheses and whitespace. */
    private fun tokenize(text: String): List<Token>? {
        val tokens = ArrayList<Token>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c in '0'..'9' || c == '.' -> {
                    val start = i
                    while (i < text.length && (text[i] in '0'..'9' || text[i] == '.')) i++
                    val literal = text.substring(start, i)
                    if (literal.count { it == '.' } > 1 || literal == ".") return null
                    tokens += Token.Num(literal.toDoubleOrNull() ?: return null)
                }
                else -> {
                    tokens += Token.Op(
                        when (c) {
                            '+', '-', '*', '/', '%', '^', '(', ')' -> c
                            '×' -> '*'
                            '÷' -> '/'
                            '−' -> '-'
                            else -> return null
                        },
                    )
                    i++
                }
            }
        }
        return tokens.takeIf { list -> list.any { it is Token.Num } }
    }

    private class SyntaxError : RuntimeException(null, null, false, false)

    private class ArithmeticFailure(val reason: CalculationResult.Reason) : RuntimeException(null, null, false, false)

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0
        private var depth = 0
        var sawBinaryOperator = false
            private set

        fun atEnd() = pos == tokens.size

        private fun peekOp(): Char? = (tokens.getOrNull(pos) as? Token.Op)?.c

        fun parseExpression(): Double {
            enter()
            var value = parseTerm()
            while (true) {
                val op = peekOp()
                if (op != '+' && op != '-') break
                pos++
                sawBinaryOperator = true
                val right = parseTerm()
                value = finite(if (op == '+') value + right else value - right)
            }
            depth--
            return value
        }

        private fun parseTerm(): Double {
            var value = parseUnary()
            while (true) {
                val op = peekOp()
                if (op != '*' && op != '/' && op != '%') return value
                pos++
                sawBinaryOperator = true
                val right = parseUnary()
                if (op != '*' && right == 0.0) throw ArithmeticFailure(CalculationResult.Reason.DIVISION_BY_ZERO)
                value = finite(
                    when (op) {
                        '*' -> value * right
                        '/' -> value / right
                        else -> value % right
                    },
                )
            }
        }

        private fun parseUnary(): Double {
            enter()
            val value = when (peekOp()) {
                '-' -> { pos++; -parseUnary() }
                '+' -> { pos++; parseUnary() }
                else -> parsePower()
            }
            depth--
            return value
        }

        private fun parsePower(): Double {
            val base = parseAtom()
            if (peekOp() != '^') return base
            pos++
            sawBinaryOperator = true
            val exponent = parseUnary()
            if (base == 0.0 && exponent < 0) throw ArithmeticFailure(CalculationResult.Reason.DIVISION_BY_ZERO)
            return finite(base.pow(exponent))
        }

        private fun parseAtom(): Double = when (val token = tokens.getOrNull(pos)) {
            is Token.Num -> {
                pos++
                finite(token.value)
            }
            is Token.Op -> {
                if (token.c != '(') throw SyntaxError()
                pos++
                val value = parseExpression()
                if (peekOp() != ')') throw SyntaxError()
                pos++
                value
            }
            null -> throw SyntaxError()
        }

        private fun enter() {
            if (++depth > MAX_DEPTH) throw SyntaxError()
        }

        private fun finite(value: Double): Double = when {
            value.isNaN() -> throw ArithmeticFailure(CalculationResult.Reason.UNDEFINED)
            value.isInfinite() -> throw ArithmeticFailure(CalculationResult.Reason.OVERFLOW)
            else -> value
        }
    }
}
