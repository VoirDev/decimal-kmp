package dev.voir.decimal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pins error messages exactly, so every platform reports the same text for the same failure.
 */
class DecimalErrorMessageTest {
    @Test
    fun `reports malformed input`() {
        assertMessage<IllegalArgumentException>("Decimal text is empty.") { Decimal.parse("  ") }
        assertMessage<IllegalArgumentException>(
            "Invalid decimal text \"1e3\": expected plain base-10 text such as 123, +42, or -0.01.",
        ) { Decimal.parse("1e3") }
        assertMessage<IllegalArgumentException>(
            "Invalid integer text \"1.5\": expected base-10 digits such as 42 or -7.",
        ) { Decimal.ofInteger("1.5") }
        assertMessage<IllegalArgumentException>(
            "Invalid formatted decimal text \"1,23\" for decimal separator '.' and grouping separators [' ', ',', '_'].",
        ) { Decimal.parseFormatted("1,23") }
        assertMessage<IllegalArgumentException>(
            "Decimal cannot be created from non-finite Double NaN.",
        ) { Decimal.fromDouble(Double.NaN) }
    }

    @Test
    fun `rejects non-ASCII digits in formatted input`() {
        assertMessage<IllegalArgumentException>(
            "Invalid formatted decimal text \"١٢٣\" for decimal separator '.' and grouping separators [' ', ',', '_'].",
        ) { Decimal.parseFormatted("١٢٣") }
    }

    @Test
    fun `shortens long input in messages`() {
        val text = "1" + "0".repeat(128)

        assertMessage<IllegalArgumentException>(
            "Decimal \"1${"0".repeat(23)}…${"0".repeat(12)}\" (129 characters) has exponent 128; " +
                "the exponent must be between -128 and 127.",
        ) { Decimal.parse(text) }
    }

    @Test
    fun `reports values outside the envelope`() {
        assertMessage<IllegalArgumentException>(
            "Decimal \"${"1".repeat(39)}\" has 39 significant digits; at most 38 are supported.",
        ) { Decimal.parse("1".repeat(39)) }

        val largest = decimal("9".repeat(38) + "0".repeat(127))
        assertMessage<IllegalArgumentException>(
            "Decimal \"2${"0".repeat(23)}…${"0".repeat(12)}\" (166 characters) has exponent 165; " +
                "the exponent must be between -128 and 127.",
        ) { largest + largest }

        assertMessage<IllegalArgumentException>(
            "Moving the decimal point of \"1\" right by 128 places gives exponent 128; " +
                "the exponent must be between -128 and 127.",
        ) { decimal("1").movePointRight(128) }
    }

    @Test
    fun `reports invalid arguments`() {
        assertMessage<IllegalArgumentException>("Scale must be between 0 and 32767, but was -1.") {
            decimal("1").setScale(-1, Rounding.HALF_UP)
        }
        assertMessage<IllegalArgumentException>("Places must be non-negative, but was -2.") {
            decimal("1").movePointLeft(-2)
        }
        assertMessage<IllegalArgumentException>("maximumFractionDigits must be between 0 and 32767, but was 32768.") {
            decimal("1").toFormattedString(maximumFractionDigits = 32768)
        }
        assertMessage<IllegalArgumentException>("minimumFractionDigits (3) must be at most maximumFractionDigits (2).") {
            decimal("1").toFormattedString(maximumFractionDigits = 2, minimumFractionDigits = 3)
        }
        assertMessage<IllegalArgumentException>("decimalSeparator and groupingSeparator must differ, but both were ','.") {
            decimal("1").toFormattedString(maximumFractionDigits = 2, decimalSeparator = ',', groupingSeparator = ',')
        }
    }

    @Test
    fun `reports division by zero with the dividend`() {
        assertMessage<ArithmeticException>("Division by zero: \"-1.5\" / 0.") {
            decimal("-1.50").divide(Decimal.zero(), scale = 2, rounding = Rounding.HALF_UP)
        }
    }

    private inline fun <reified T : Throwable> assertMessage(expected: String, noinline block: () -> Unit) {
        assertEquals(expected, assertFailsWith<T>(block = block).message)
    }
}
