package dev.voir.decimal

import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Covers division scale, single rounding of exact quotients, and division limits.
 */
class DecimalDivisionTest {
    @Test
    fun `divides decimal values with explicit and default rounding`() {
        assertPlain("0.3333", decimal("1").divide(decimal("3"), scale = 4, rounding = Rounding.DOWN))
        assertPlain("0.3334", decimal("1").divide(decimal("3"), scale = 4, rounding = Rounding.UP))
        assertPlain("2.5", decimal("10").divide(decimal("4"), scale = 2, rounding = Rounding.HALF_UP))
        assertPlain("0.333333333333333333", decimal("1") / decimal("3"))
        assertPlain("3.333", decimal("10").divideInteger("3", scale = 3, rounding = Rounding.DOWN))

        assertFailsWith<IllegalArgumentException> { decimal("1").divide(decimal("3"), scale = -1, rounding = Rounding.HALF_UP) }
        assertFailsWith<IllegalArgumentException> { decimal("1").divide(decimal("3"), scale = 32768, rounding = Rounding.HALF_UP) }
        assertFailsWith<IllegalArgumentException> { decimal("1").divideInteger("1.5", scale = 2, rounding = Rounding.HALF_UP) }
    }

    @Test
    fun `rounds division consistently near discarded digit boundaries`() {
        assertPlain("0.16", decimal("1").divide(decimal("6"), scale = 2, rounding = Rounding.DOWN))
        assertPlain("0.17", decimal("1").divide(decimal("6"), scale = 2, rounding = Rounding.UP))
        assertPlain("0.17", decimal("1").divide(decimal("6"), scale = 2, rounding = Rounding.HALF_UP))
        assertPlain("-0.16", decimal("-1").divide(decimal("6"), scale = 2, rounding = Rounding.DOWN))
        assertPlain("-0.17", decimal("-1").divide(decimal("6"), scale = 2, rounding = Rounding.UP))
        assertPlain("-0.17", decimal("-1").divide(decimal("6"), scale = 2, rounding = Rounding.HALF_UP))

        assertPlain("2.67", decimal("8").divide(decimal("3"), scale = 2, rounding = Rounding.HALF_UP))
        assertPlain("2.66", decimal("8").divide(decimal("3"), scale = 2, rounding = Rounding.DOWN))
        assertPlain("2.67", decimal("8").divide(decimal("3"), scale = 2, rounding = Rounding.UP))
    }

    @Test
    fun `divides high scale values consistently for every rounding mode`() {
        val amount = decimal("10")
        val divisor = decimal("7")

        assertPlain("1.428571428571428571", amount.divide(divisor, scale = 18, rounding = Rounding.DOWN))
        assertPlain("1.428571428571428572", amount.divide(divisor, scale = 18, rounding = Rounding.UP))
        assertPlain("1.428571428571428571", amount.divide(divisor, scale = 18, rounding = Rounding.HALF_UP))
        assertPlain("-1.428571428571428571", amount.abs().multiplyInteger("-1").divide(divisor, scale = 18, rounding = Rounding.DOWN))
        assertPlain("-1.428571428571428572", amount.abs().multiplyInteger("-1").divide(divisor, scale = 18, rounding = Rounding.UP))
        assertPlain("-1.428571428571428571", amount.abs().multiplyInteger("-1").divide(divisor, scale = 18, rounding = Rounding.HALF_UP))
    }

    @Test
    fun `rejects divide by zero`() {
        assertFailsWith<ArithmeticException> {
            decimal("1").divide(decimal("0"), scale = 2, rounding = Rounding.HALF_UP)
        }
        assertFailsWith<ArithmeticException> {
            decimal("1").divideInteger("0", scale = 2, rounding = Rounding.HALF_UP)
        }
        assertFailsWith<ArithmeticException> {
            Decimal.zero() / Decimal.zero()
        }
    }

    @Test
    fun `divides zero dividend to zero`() {
        assertPlain("0", Decimal.zero().divide(decimal("-3"), scale = 2, rounding = Rounding.UP))
    }

    @Test
    fun `divides large quotients by rounding to 38 significant digits`() {
        assertPlain(
            "333333333333333333333.33333333333333333",
            decimal("1000000000000000000000") / decimal("3"),
        )
        assertPlain(
            "666666666666666666666.66666666666666667",
            decimal("2000000000000000000000") / decimal("3"),
        )
        assertPlain(
            "-666666666666666666666.66666666666666666",
            decimal("-2000000000000000000000").divide(decimal("3"), scale = 18, rounding = Rounding.DOWN),
        )
        assertPlain(
            "0." + "3".repeat(38),
            decimal("1").divide(decimal("3"), scale = 40, rounding = Rounding.DOWN),
        )
        assertPlain(
            "0." + "3".repeat(38),
            decimal("1").divide(decimal("3"), scale = 32767, rounding = Rounding.HALF_UP),
        )
    }

    @Test
    fun `divides by rounding the exact quotient once`() {
        // 1 / (10 - 1e-37) = 0.1 + 1e-39 + ...; the non-zero tail is past the 38th digit.
        val divisor = decimal("9.9999999999999999999999999999999999999")

        assertPlain("0.11", decimal("1").divide(divisor, scale = 2, rounding = Rounding.UP))
        assertPlain("0.1", decimal("1").divide(divisor, scale = 2, rounding = Rounding.DOWN))
        assertPlain("0.1", decimal("1").divide(divisor, scale = 2, rounding = Rounding.HALF_UP))
        assertPlain("-0.11", decimal("-1").divide(divisor, scale = 2, rounding = Rounding.UP))
    }

    @Test
    fun `divides quotients below the smallest exponent without failing`() {
        val smallest = decimal("0." + "0".repeat(127) + "1")

        assertPlain("0", smallest.divide(decimal("10"), scale = 2, rounding = Rounding.DOWN))
        assertPlain("0", smallest.divide(decimal("10"), scale = 2, rounding = Rounding.HALF_UP))
        assertPlain("0.01", smallest.divide(decimal("10"), scale = 2, rounding = Rounding.UP))
        assertPlain("0", smallest.divide(decimal("10"), scale = 200, rounding = Rounding.DOWN))
        assertPlain("0", smallest.divide(decimal("10"), scale = 200, rounding = Rounding.HALF_UP))
        assertPlain(smallest.toPlainString(), smallest.divide(decimal("10"), scale = 200, rounding = Rounding.UP))
        assertPlain("-0.01", smallest.divide(decimal("-1" + "0".repeat(127)), scale = 2, rounding = Rounding.UP))
    }

    @Test
    fun `rejects quotients above the largest exponent`() {
        assertFailsWith<IllegalArgumentException> {
            decimal("1" + "0".repeat(127)).divide(decimal("0.1"), scale = 2, rounding = Rounding.DOWN)
        }
    }
}
