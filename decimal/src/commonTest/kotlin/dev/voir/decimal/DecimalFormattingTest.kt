package dev.voir.decimal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Covers display formatting with separators, fraction digits, and rounding.
 */
class DecimalFormattingTest {
    @Test
    fun `formats decimal values for display`() {
        assertEquals("1,234.57", decimal("1234.567").toFormattedString(maximumFractionDigits = 2))
        assertEquals("1,234", decimal("1234.4").toFormattedString(maximumFractionDigits = 0))
        assertEquals("1234.5", decimal("1234.5").toFormattedString(maximumFractionDigits = 2, groupingSeparator = null))
        assertEquals("1234.50", decimal("1234.5").toFormattedString(maximumFractionDigits = 2, groupingSeparator = null, minimumFractionDigits = 2))
        assertEquals("1.234,57", decimal("1234.567").toFormattedString(maximumFractionDigits = 2, decimalSeparator = ',', groupingSeparator = '.'))
        assertEquals("-9 876 543,2", decimal("-9876543.21").toFormattedString(maximumFractionDigits = 1, decimalSeparator = ',', groupingSeparator = ' '))
        assertEquals("1.23", decimal("1.239").toFormattedString(maximumFractionDigits = 2, rounding = Rounding.DOWN))
        assertEquals("1.24", decimal("1.231").toFormattedString(maximumFractionDigits = 2, rounding = Rounding.UP))
        assertEquals("1,235", decimal("1234.999").toFormattedString(maximumFractionDigits = 2))

        assertFailsWith<IllegalArgumentException> { decimal("1").toFormattedString(maximumFractionDigits = -1) }
        assertFailsWith<IllegalArgumentException> { decimal("1").toFormattedString(maximumFractionDigits = 32768) }
        assertFailsWith<IllegalArgumentException> { decimal("1").toFormattedString(maximumFractionDigits = 2, minimumFractionDigits = -1) }
        assertFailsWith<IllegalArgumentException> { decimal("1").toFormattedString(maximumFractionDigits = 2, minimumFractionDigits = 3) }
        assertFailsWith<IllegalArgumentException> { decimal("1").toFormattedString(maximumFractionDigits = 2, decimalSeparator = ',', groupingSeparator = ',') }
    }

    @Test
    fun `formats negative values that round to zero without a sign`() {
        assertEquals("0", decimal("-0.001").toFormattedString(maximumFractionDigits = 2))
        assertEquals("0.00", decimal("-0.001").toFormattedString(maximumFractionDigits = 2, minimumFractionDigits = 2))
        assertEquals("0", decimal("-0.4").toFormattedString(maximumFractionDigits = 0))
        assertEquals("0", decimal("-0.009").toFormattedString(maximumFractionDigits = 2, rounding = Rounding.DOWN))
        assertEquals("-0.01", decimal("-0.001").toFormattedString(maximumFractionDigits = 2, rounding = Rounding.UP))
        assertEquals("-0.01", decimal("-0.005").toFormattedString(maximumFractionDigits = 2))
    }

    @Test
    fun `formats negative values with explicit rounding modes`() {
        assertEquals("-1.23", decimal("-1.239").toFormattedString(maximumFractionDigits = 2, rounding = Rounding.DOWN))
        assertEquals("-1.24", decimal("-1.231").toFormattedString(maximumFractionDigits = 2, rounding = Rounding.UP))
        assertEquals("-1.24", decimal("-1.235").toFormattedString(maximumFractionDigits = 2, rounding = Rounding.HALF_UP))
        assertEquals("-1,000", decimal("-999.5").toFormattedString(maximumFractionDigits = 0, rounding = Rounding.HALF_UP))
    }

    @Test
    fun `formats high scale values with explicit rounding modes`() {
        val value = decimal("0.123456789123456789")
        val carry = decimal("999999999999999999.999999999999999999")

        assertEquals("0.123456789123456789", value.toFormattedString(maximumFractionDigits = 18, rounding = Rounding.DOWN, groupingSeparator = null))
        assertEquals("0.123456789123456789", value.toFormattedString(maximumFractionDigits = 18, rounding = Rounding.UP, groupingSeparator = null))
        assertEquals("0.123456789123456789", value.toFormattedString(maximumFractionDigits = 18, rounding = Rounding.HALF_UP, groupingSeparator = null))
        assertEquals("1,000,000,000,000,000,000", carry.toFormattedString(maximumFractionDigits = 2, rounding = Rounding.HALF_UP))
        assertEquals("1,000,000,000,000,000,000.00", carry.toFormattedString(maximumFractionDigits = 2, rounding = Rounding.HALF_UP, minimumFractionDigits = 2))
        assertEquals("999,999,999,999,999,999.99", carry.toFormattedString(maximumFractionDigits = 2, rounding = Rounding.DOWN))
    }
}
