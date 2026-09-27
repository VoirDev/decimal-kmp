package dev.voir.decimal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Covers setScale and integer conversion across rounding boundaries.
 */
class DecimalScaleTest {
    @Test
    fun `rounds decimal scale using all rounding modes`() {
        assertPlain("1.24", decimal("1.235").setScale(2, Rounding.HALF_UP))
        assertPlain("1.23", decimal("1.234").setScale(2, Rounding.HALF_UP))
        assertPlain("1.23", decimal("1.239").setScale(2, Rounding.DOWN))
        assertPlain("1.24", decimal("1.231").setScale(2, Rounding.UP))
        assertPlain("-1.23", decimal("-1.239").setScale(2, Rounding.DOWN))
        assertPlain("-1.24", decimal("-1.231").setScale(2, Rounding.UP))

        assertFailsWith<IllegalArgumentException> { decimal("1.23").setScale(-1, Rounding.HALF_UP) }
        assertFailsWith<IllegalArgumentException> { decimal("1.23").setScale(32768, Rounding.HALF_UP) }
    }

    @Test
    fun `rounds decimal scale at carry and sign boundaries`() {
        assertPlain("1000", decimal("999.995").setScale(2, Rounding.HALF_UP))
        assertPlain("999.99", decimal("999.995").setScale(2, Rounding.DOWN))
        assertPlain("1000", decimal("999.991").setScale(2, Rounding.UP))
        assertPlain("-1000", decimal("-999.995").setScale(2, Rounding.HALF_UP))
        assertPlain("-999.99", decimal("-999.995").setScale(2, Rounding.DOWN))
        assertPlain("-1000", decimal("-999.991").setScale(2, Rounding.UP))

        assertPlain("10", decimal("10.49").setScale(0, Rounding.HALF_UP))
        assertPlain("11", decimal("10.50").setScale(0, Rounding.HALF_UP))
        assertPlain("-10", decimal("-10.49").setScale(0, Rounding.HALF_UP))
        assertPlain("-11", decimal("-10.50").setScale(0, Rounding.HALF_UP))
    }

    @Test
    fun `rounds high scale values consistently for every rounding mode`() {
        val positive = decimal("8518.049999999999999999")
        val negative = decimal("-8518.049999999999999999")

        assertPlain("8518.05", positive.setScale(2, Rounding.HALF_UP))
        assertPlain("8518.04", positive.setScale(2, Rounding.DOWN))
        assertPlain("8518.05", positive.setScale(2, Rounding.UP))
        assertPlain("-8518.05", negative.setScale(2, Rounding.HALF_UP))
        assertPlain("-8518.04", negative.setScale(2, Rounding.DOWN))
        assertPlain("-8518.05", negative.setScale(2, Rounding.UP))
    }

    @Test
    fun `converts decimal to integer string with half up rounding`() {
        assertEquals("124", decimal("123.5").toIntegerString())
        assertEquals("123", decimal("123.49").toIntegerString())
        assertEquals("-124", decimal("-123.5").toIntegerString())
        assertEquals("-123", decimal("-123.49").toIntegerString())
    }

    @Test
    fun `rounds and formats at the largest scale quickly`() {
        val values = listOf("1", "-123.456", "0." + "0".repeat(127) + "1", "9".repeat(38) + "0".repeat(89))

        repeat(200) {
            values.forEach { value ->
                assertPlain(decimal(value).toPlainString(), decimal(value).setScale(32767, Rounding.HALF_EVEN))
            }
        }
        assertEquals("-123.456", decimal("-123.456").toFormattedString(maximumFractionDigits = 32767))
        assertEquals("1.00", decimal("1").toFormattedString(maximumFractionDigits = 32767, minimumFractionDigits = 2))
    }
}
