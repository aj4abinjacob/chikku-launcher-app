package io.github.aj4abinjacob.chikku.core.model.search

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.search.CalculationResult.Reason
import org.junit.Test

class CalculatorTest {

    private fun value(expression: String): String =
        (Calculator.evaluate(expression) as? CalculationResult.Value)?.display
            ?: error("not a value: ${Calculator.evaluate(expression)}")

    @Test
    fun basicOperatorsAndPrecedence() {
        assertThat(value("1+2")).isEqualTo("3")
        assertThat(value("7 - 10")).isEqualTo("-3")
        assertThat(value("2+3*4")).isEqualTo("14")
        assertThat(value("(2+3)*4")).isEqualTo("20")
        assertThat(value("10/4")).isEqualTo("2.5")
        assertThat(value("10 % 4")).isEqualTo("2")
        assertThat(value("8 - 2 - 1")).isEqualTo("5") // left-associative
        assertThat(value("100 / 10 / 5")).isEqualTo("2")
    }

    @Test
    fun powerIsRightAssociativeAndBindsTighterThanUnaryMinus() {
        assertThat(value("2^3")).isEqualTo("8")
        assertThat(value("2^3^2")).isEqualTo("512")
        assertThat(value("-2^2")).isEqualTo("-4")
        assertThat(value("(-2)^2")).isEqualTo("4")
        assertThat(value("2^-1")).isEqualTo("0.5")
    }

    @Test
    fun unaryMinusDecimalsAndParentheses() {
        assertThat(value("-3 * -2")).isEqualTo("6")
        assertThat(value("--3 + 1")).isEqualTo("4")
        assertThat(value("1.5 * 2")).isEqualTo("3")
        assertThat(value(".5 + .25")).isEqualTo("0.75")
        assertThat(value("((((1+1))))*3")).isEqualTo("6")
        assertThat(value("0.1 + 0.2")).isEqualTo("0.3") // rounded to 12 significant digits
        assertThat(value("1/3")).isEqualTo("0.333333333333")
        assertThat(value(" 6 × 7 ")).isEqualTo("42")
        assertThat(value("9 ÷ 3 − 1")).isEqualTo("2")
    }

    @Test
    fun divisionAndRemainderByZeroAreErrors() {
        assertThat(Calculator.evaluate("1/0")).isEqualTo(CalculationResult.Error(Reason.DIVISION_BY_ZERO))
        assertThat(Calculator.evaluate("5 % 0")).isEqualTo(CalculationResult.Error(Reason.DIVISION_BY_ZERO))
        assertThat(Calculator.evaluate("1/(2-2)")).isEqualTo(CalculationResult.Error(Reason.DIVISION_BY_ZERO))
        assertThat(Calculator.evaluate("0^-1")).isEqualTo(CalculationResult.Error(Reason.DIVISION_BY_ZERO))
    }

    @Test
    fun overflowAndUndefinedAreErrors() {
        assertThat(Calculator.evaluate("10^400")).isEqualTo(CalculationResult.Error(Reason.OVERFLOW))
        assertThat(Calculator.evaluate("1e5*2")).isEqualTo(CalculationResult.NotAnExpression) // no exponent literals
        assertThat(Calculator.evaluate("10^200 * 10^200"))
            .isEqualTo(CalculationResult.Error(Reason.OVERFLOW))
        assertThat(Calculator.evaluate("(-8)^0.5")).isEqualTo(CalculationResult.Error(Reason.UNDEFINED))
    }

    @Test
    fun largeAndTinyValuesUseScientificNotation() {
        assertThat(value("10^20")).isEqualTo("1E+20")
        assertThat(value("10^-12")).isEqualTo("1E-12")
        assertThat(value("123456789 * 1000")).isEqualTo("123456789000")
    }

    @Test
    fun garbageAndPlainTextAreNotExpressions() {
        listOf(
            "", "   ", "maps", "42", "-3", "(7)", "1+", "*2", "2**3", "(1+2", "1+2)", "()", "1..2+1", ".",
            "1 + a", "drop table", "2(3)", "%", "^^", "1+2;", "١+٢",
        ).forEach { input ->
            assertThat(Calculator.evaluate(input)).isEqualTo(CalculationResult.NotAnExpression)
        }
    }

    @Test
    fun hostileNestingAndLengthAreBounded() {
        assertThat(Calculator.evaluate("(".repeat(5_000) + "1+1" + ")".repeat(5_000)))
            .isEqualTo(CalculationResult.NotAnExpression)
        assertThat(Calculator.evaluate("-".repeat(200) + "1+1")).isEqualTo(CalculationResult.NotAnExpression)
        assertThat(Calculator.evaluate("1+".repeat(10_000) + "1")).isEqualTo(CalculationResult.NotAnExpression)
    }

    @Test
    fun formatNormalizesNegativeZero() {
        assertThat(value("-0 * 5")).isEqualTo("0")
        assertThat(Calculator.format(-0.0)).isEqualTo("0")
    }
}
