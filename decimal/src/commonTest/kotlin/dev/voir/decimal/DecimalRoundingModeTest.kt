package dev.voir.decimal

import kotlin.test.Test
import kotlin.test.assertEquals

class DecimalRoundingModeTest {
    @Test
    fun `rounds half even ties to the even neighbor`() {
        assertScaled("2", "2.5", 0, Rounding.HALF_EVEN)
        assertScaled("4", "3.5", 0, Rounding.HALF_EVEN)
        assertScaled("-2", "-2.5", 0, Rounding.HALF_EVEN)
        assertScaled("-4", "-3.5", 0, Rounding.HALF_EVEN)
        assertScaled("0", "0.5", 0, Rounding.HALF_EVEN)
        assertScaled("1", "1.005", 2, Rounding.HALF_EVEN)
        assertScaled("1.02", "1.015", 2, Rounding.HALF_EVEN)
        assertScaled("1000", "999.995", 2, Rounding.HALF_EVEN)
    }

    @Test
    fun `rounds half even non-ties to the nearest neighbor`() {
        assertScaled("3", "2.5000000000000000001", 0, Rounding.HALF_EVEN)
        assertScaled("2", "2.4999999999999999999", 0, Rounding.HALF_EVEN)
        assertScaled("-3", "-2.51", 0, Rounding.HALF_EVEN)
    }

    @Test
    fun `rounds floor toward negative infinity`() {
        assertScaled("1.23", "1.239", 2, Rounding.FLOOR)
        assertScaled("-1.24", "-1.231", 2, Rounding.FLOOR)
        assertScaled("-0.01", "-0.001", 2, Rounding.FLOOR)
        assertScaled("0", "0.009", 2, Rounding.FLOOR)
        assertScaled("-1.23", "-1.23", 2, Rounding.FLOOR)
    }

    @Test
    fun `rounds ceiling toward positive infinity`() {
        assertScaled("1.24", "1.231", 2, Rounding.CEILING)
        assertScaled("-1.23", "-1.239", 2, Rounding.CEILING)
        assertScaled("0.01", "0.001", 2, Rounding.CEILING)
        assertScaled("0", "-0.009", 2, Rounding.CEILING)
        assertScaled("1.23", "1.23", 2, Rounding.CEILING)
    }

    @Test
    fun `rounds values far below the requested scale`() {
        val tiny = "0." + "0".repeat(127) + "1"

        assertScaled("0.01", tiny, 2, Rounding.CEILING)
        assertScaled("0", tiny, 2, Rounding.FLOOR)
        assertScaled("-0.01", "-$tiny", 2, Rounding.FLOOR)
        assertScaled("0", tiny, 2, Rounding.HALF_EVEN)
    }

    @Test
    fun `divides with every rounding mode`() {
        val one = decimal("1")
        val minusOne = decimal("-1")
        val six = decimal("6")

        assertEquals("0.17", one.divide(six, scale = 2, rounding = Rounding.HALF_EVEN).toPlainString())
        assertEquals("0.16", one.divide(six, scale = 2, rounding = Rounding.FLOOR).toPlainString())
        assertEquals("0.17", one.divide(six, scale = 2, rounding = Rounding.CEILING).toPlainString())
        assertEquals("-0.17", minusOne.divide(six, scale = 2, rounding = Rounding.FLOOR).toPlainString())
        assertEquals("-0.16", minusOne.divide(six, scale = 2, rounding = Rounding.CEILING).toPlainString())
        assertEquals("0.12", one.divide(decimal("8"), scale = 2, rounding = Rounding.HALF_EVEN).toPlainString())
        assertEquals("0.38", decimal("3").divide(decimal("8"), scale = 2, rounding = Rounding.HALF_EVEN).toPlainString())
    }

    @Test
    fun `formats with every rounding mode`() {
        assertEquals("2", decimal("2.5").toFormattedString(maximumFractionDigits = 0, rounding = Rounding.HALF_EVEN))
        assertEquals("1,234.56", decimal("1234.565").toFormattedString(maximumFractionDigits = 2, rounding = Rounding.HALF_EVEN))
        assertEquals("-0.01", decimal("-0.001").toFormattedString(maximumFractionDigits = 2, rounding = Rounding.FLOOR))
        assertEquals("0", decimal("-0.001").toFormattedString(maximumFractionDigits = 2, rounding = Rounding.CEILING))
        assertEquals("0.00", decimal("-0.001").toFormattedString(maximumFractionDigits = 2, minimumFractionDigits = 2, rounding = Rounding.CEILING))
    }

    private fun assertScaled(expected: String, value: String, scale: Int, rounding: Rounding) {
        assertEquals(expected, decimal(value).setScale(scale, rounding).toPlainString(), "$value at scale $scale, $rounding")
    }
}
