package dev.voir.decimal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Covers plain, formatted, integer, and primitive factories and their input limits.
 */
class DecimalParsingTest {
    @Test
    fun `parses decimal plain strings`() {
        assertPlain("0", Decimal.parse("0"))
        assertPlain("123", Decimal.parse("123"))
        assertPlain("-123", Decimal.parse("-123"))
        assertPlain("123.456", Decimal.parse("123.456"))
        assertPlain("-0.001", Decimal.parse("-0.001"))
        assertPlain("42", Decimal.parse("  +42  "))
        assertPlain("999999999.123456789", Decimal.parse("999999999.123456789"))
    }

    @Test
    fun `parses or returns null for plain text`() {
        assertPlain("-0.01", assertNotNull(Decimal.parseOrNull(" -0.010 ")))
        assertPlain("42", assertNotNull(Decimal.parseOrNull("+42")))

        listOf("", "   ", "1e3", "abc", "1.", "1,234", "NaN", "1".repeat(39), "1" + "0".repeat(128)).forEach { value ->
            assertNull(Decimal.parseOrNull(value), "Expected '$value' to be rejected")
        }
    }

    @Test
    fun `parses formatted text or returns null`() {
        assertPlain("1234.56", assertNotNull(Decimal.parseFormattedOrNull("1,234.56")))
        assertPlain(
            "1234.56",
            assertNotNull(Decimal.parseFormattedOrNull("1.234,56", decimalSeparator = ',', groupingSeparators = setOf('.'))),
        )

        listOf("", "12,34", "1,,234", "١٢٣", "1,234.5,6").forEach { value ->
            assertNull(Decimal.parseFormattedOrNull(value), "Expected '$value' to be rejected")
        }
        assertNull(Decimal.parseFormattedOrNull("1.234,56"))
    }

    @Test
    fun `parses decimal through inline factory`() {
        assertPlain("123.45", decimal("123.45"))
        assertPlain("-987.65", decimal("-987.65"))
    }

    @Test
    fun `rejects invalid decimal plain strings`() {
        listOf(
            "",
            "   ",
            ".1",
            "1.",
            "1.2.3",
            "1,234",
            "1_234",
            "1e3",
            "NaN",
            "--1",
            "12-3",
            "abc",
        ).forEach { value ->
            assertFailsWith<IllegalArgumentException>("Expected '$value' to be rejected") {
                Decimal.parse(value)
            }
        }
    }

    @Test
    fun `accepts decimal values inside NSDecimalNumber limits`() {
        val maxPrecision = "99999999999999999999999999999999999999"
        val maxExponent = "1" + "0".repeat(127)
        val minExponent = "0." + "0".repeat(127) + "1"

        assertPlain(maxPrecision, Decimal.parse(maxPrecision))
        assertPlain(maxExponent, Decimal.parse(maxExponent))
        assertPlain(minExponent, Decimal.parse(minExponent))
    }

    @Test
    fun `rejects decimal values outside NSDecimalNumber limits`() {
        assertFailsWith<IllegalArgumentException> {
            Decimal.parse("1".repeat(39))
        }
        assertFailsWith<IllegalArgumentException> {
            Decimal.parse("1" + "0".repeat(128))
        }
        assertFailsWith<IllegalArgumentException> {
            Decimal.parse("0." + "0".repeat(128) + "1")
        }
    }

    @Test
    fun `parses long zero padding without keeping it`() {
        val padding = "0".repeat(100_000)

        assertPlain("1", Decimal.parse("1.$padding"))
        assertPlain("-12.5", Decimal.parse("-${padding}12.5$padding"))
        assertPlain("0", Decimal.parse("0.$padding"))
        assertPlain("7", Decimal.ofInteger("${padding}7"))
        assertFailsWith<IllegalArgumentException> { Decimal.parse("0.${padding}1") }
        assertFailsWith<IllegalArgumentException> { Decimal.parse("1$padding") }
    }

    @Test
    fun `parses decimal formatted strings with default separators`() {
        assertPlain("1234.56", Decimal.parseFormatted("1,234.56"))
        assertPlain("1234567.89", Decimal.parseFormatted("1 234 567.89"))
        assertPlain("1234567.89", Decimal.parseFormatted("1_234_567.89"))
        assertPlain("-9876.54", Decimal.parseFormatted("-9,876.54"))
        assertPlain("42", formattedDecimal("  +42  "))
    }

    @Test
    fun `parses decimal formatted strings with custom separators`() {
        assertPlain("1234.56", Decimal.parseFormatted("1.234,56", decimalSeparator = ',', groupingSeparators = setOf('.')))
        assertPlain("1234567.89", Decimal.parseFormatted("1'234'567~89", decimalSeparator = '~', groupingSeparators = setOf('\'')))
        assertPlain("-1234567.89", formattedDecimal("-1.234.567,89", decimalSeparator = ',', groupingSeparators = setOf('.')))
    }

    @Test
    fun `rejects invalid decimal formatted strings`() {
        listOf(
            "",
            "   ",
            ".",
            ",",
            "1.",
            "1.2.3",
            "1,,234",
            "12,34",
            "1234,567",
            "1,23,456",
            "1,234,",
            ",123",
            "1,234.5,6",
            "12a34.56",
            "1,234.56.78",
            "--1,234.56",
            "1-234.56",
        ).forEach { value ->
            assertFailsWith<IllegalArgumentException>("Expected '$value' to be rejected") {
                Decimal.parseFormatted(value)
            }
        }
    }

    @Test
    fun `creates decimal values from integer sources`() {
        assertPlain("0", Decimal.ofInteger("0"))
        assertPlain("-42", Decimal.ofInteger(" -42 "))
        assertPlain(Int.MAX_VALUE.toString(), Decimal.fromInt(Int.MAX_VALUE))
        assertPlain(Int.MIN_VALUE.toString(), Decimal.fromInt(Int.MIN_VALUE))
        assertPlain(Long.MAX_VALUE.toString(), Decimal.fromLong(Long.MAX_VALUE))
        assertPlain(Long.MIN_VALUE.toString(), Decimal.fromLong(Long.MIN_VALUE))
        assertPlain("0", Decimal.zero())
        assertPlain("1", Decimal.one())
    }

    @Test
    fun `rejects invalid decimal integer strings`() {
        listOf("", " ", "1.0", "1,000", "1e3", "abc", "--1").forEach { value ->
            assertFailsWith<IllegalArgumentException>("Expected '$value' to be rejected") {
                Decimal.ofInteger(value)
            }
        }
    }

    @Test
    fun `creates decimal values from finite doubles`() {
        assertPlain("0.1", Decimal.fromDouble(0.1))
        assertPlain("-123.456", Decimal.fromDouble(-123.456))

        assertFailsWith<IllegalArgumentException> { Decimal.fromDouble(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { Decimal.fromDouble(Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { Decimal.fromDouble(Double.NEGATIVE_INFINITY) }
    }

    @Test
    fun `creates decimal values from doubles printed in scientific notation`() {
        assertPlain("10000000", Decimal.fromDouble(1e7))
        assertPlain("123456789", Decimal.fromDouble(123456789.0))
        assertPlain("1000000000000000000000", Decimal.fromDouble(1e21))
        assertPlain("0.00001", Decimal.fromDouble(0.00001))
        assertPlain("-0.00000015", Decimal.fromDouble(-1.5e-7))
        assertPlain("0", Decimal.fromDouble(-0.0))

        assertFailsWith<IllegalArgumentException> { Decimal.fromDouble(1e300) }
        assertFailsWith<IllegalArgumentException> { Decimal.fromDouble(Double.MIN_VALUE) }
    }

    @Test
    fun `canonicalizes zero values across signs and scales`() {
        assertPlain("0", decimal("-0"))
        assertPlain("0", decimal("-0.000000000000000000"))
        assertPlain("0", decimal("0.000000000000000000"))
        assertEquals(decimal("0"), decimal("-0.000000000000000000"))
        assertEquals(decimal("0").hashCode(), decimal("-0.000000000000000000").hashCode())
        assertEquals("0", decimal("-0.5").setScale(0, Rounding.DOWN).toPlainString())
    }
}
