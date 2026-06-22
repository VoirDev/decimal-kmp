package dev.voir.decimal

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class DecimalTest {
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
    fun `adds and subtracts decimal values`() {
        assertPlain("12.75", decimal("10.50").add(decimal("2.25")))
        assertPlain("8.25", decimal("10.50") - decimal("2.25"))
        assertPlain("-12.75", decimal("-10.50") + decimal("-2.25"))
        assertPlain("0.000000000000000001", decimal("1000000000000000000.000000000000000001") - decimal("1000000000000000000"))
    }

    @Test
    fun `multiplies decimal values and integer strings`() {
        assertPlain("6.25", decimal("2.5").multiply(decimal("2.5")))
        assertPlain("-7.5", decimal("2.5") * decimal("-3"))
        assertPlain("123456789123456789000", decimal("123456789123456789").multiplyInteger("1000"))
        assertPlain("-246.9", decimal("123.45").multiplyInteger("-2"))

        assertFailsWith<IllegalArgumentException> { decimal("2").multiplyInteger("1.5") }
    }

    @Test
    fun `rejects arithmetic results outside NSDecimalNumber exponent limits`() {
        assertFails {
            decimal("1" + "0".repeat(127)).multiplyInteger("10")
        }
    }

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
    fun `moves decimal point left and right`() {
        assertPlain("1.2345", decimal("12345").movePointLeft(4))
        assertPlain("1234500", decimal("123.45").movePointRight(4))
        assertPlain("-0.001234", decimal("-1234").movePointLeft(6))
        assertPlain("0", Decimal.zero().movePointRight(100))

        assertFailsWith<IllegalArgumentException> { decimal("1").movePointLeft(-1) }
        assertFailsWith<IllegalArgumentException> { decimal("1").movePointRight(-1) }
    }

    @Test
    fun `rejects decimal point moves outside exponent limits`() {
        assertFailsWith<IllegalArgumentException> {
            decimal("1").movePointRight(128)
        }
        assertFailsWith<IllegalArgumentException> {
            decimal("1").movePointLeft(129)
        }
    }

    @Test
    fun `converts decimal to integer string with half up rounding`() {
        assertEquals("124", decimal("123.5").toIntegerString())
        assertEquals("123", decimal("123.49").toIntegerString())
        assertEquals("-124", decimal("-123.5").toIntegerString())
        assertEquals("-123", decimal("-123.49").toIntegerString())
    }

    @Test
    fun `formats decimal values for display`() {
        assertEquals("1,234.57", decimal("1234.567").toFormattedString(scale = 2))
        assertEquals("1,234", decimal("1234.4").toFormattedString(scale = 0))
        assertEquals("1234.50", decimal("1234.5").toFormattedString(scale = 2, groupingSeparator = null))
        assertEquals("1.234,57", decimal("1234.567").toFormattedString(scale = 2, decimalSeparator = ',', groupingSeparator = '.'))
        assertEquals("-9 876 543,2", decimal("-9876543.21").toFormattedString(scale = 1, decimalSeparator = ',', groupingSeparator = ' '))
        assertEquals("1.23", decimal("1.239").toFormattedString(scale = 2, rounding = Rounding.DOWN))
        assertEquals("1.24", decimal("1.231").toFormattedString(scale = 2, rounding = Rounding.UP))

        assertFailsWith<IllegalArgumentException> { decimal("1").toFormattedString(scale = -1) }
        assertFailsWith<IllegalArgumentException> { decimal("1").toFormattedString(scale = 32768) }
        assertFailsWith<IllegalArgumentException> { decimal("1").toFormattedString(scale = 2, decimalSeparator = ',', groupingSeparator = ',') }
    }

    @Test
    fun `formats negative values with explicit rounding modes`() {
        assertEquals("-1.23", decimal("-1.239").toFormattedString(scale = 2, rounding = Rounding.DOWN))
        assertEquals("-1.24", decimal("-1.231").toFormattedString(scale = 2, rounding = Rounding.UP))
        assertEquals("-1.24", decimal("-1.235").toFormattedString(scale = 2, rounding = Rounding.HALF_UP))
        assertEquals("-1,000", decimal("-999.5").toFormattedString(scale = 0, rounding = Rounding.HALF_UP))
    }

    @Test
    fun `formats high scale values with explicit rounding modes`() {
        val value = decimal("0.123456789123456789")
        val carry = decimal("999999999999999999.999999999999999999")

        assertEquals("0.123456789123456789", value.toFormattedString(scale = 18, rounding = Rounding.DOWN, groupingSeparator = null))
        assertEquals("0.123456789123456789", value.toFormattedString(scale = 18, rounding = Rounding.UP, groupingSeparator = null))
        assertEquals("0.123456789123456789", value.toFormattedString(scale = 18, rounding = Rounding.HALF_UP, groupingSeparator = null))
        assertEquals("1,000,000,000,000,000,000.00", carry.toFormattedString(scale = 2, rounding = Rounding.HALF_UP))
        assertEquals("999,999,999,999,999,999.99", carry.toFormattedString(scale = 2, rounding = Rounding.DOWN))
    }

    @Test
    fun `returns decimal absolute values and string representations`() {
        assertPlain("123.45", decimal("-123.45").abs())
        assertPlain("123.45", decimal("123.45").abs())
        assertEquals("123.45", decimal("123.45").toString())
        assertEquals("123.45", decimal("123.45").toPlainString())
    }

    @Test
    fun `compares numerically equivalent decimals consistently`() {
        assertEquals(decimal("1"), decimal("1.0"))
        assertEquals(decimal("1").hashCode(), decimal("1.0").hashCode())
        assertEquals(decimal("0"), decimal("0.000"))
        assertEquals(decimal("0").hashCode(), decimal("0.000").hashCode())
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

    @Test
    fun `rejects divide by zero`() {
        assertFails {
            decimal("1").divide(decimal("0"), scale = 2, rounding = Rounding.HALF_UP)
        }

        assertFails {
            decimal("1").divideInteger("0", scale = 2, rounding = Rounding.HALF_UP)
        }
    }

    @Test
    fun `applies decimal transform operation`() {
        val transformed = decimal("10").transform { it.multiplyInteger("3").subtract(decimal("1.5")) }

        assertPlain("28.5", transformed)
    }

    @Test
    fun `serializes and deserializes decimal as plain string`() {
        val encoded = Json.encodeToString(DecimalBox(decimal("1234.5678")))
        assertEquals("""{"amount":"1234.5678"}""", encoded)

        val decoded = Json.decodeFromString<DecimalBox>("""{"amount":"-0.125"}""")
        assertPlain("-0.125", decoded.amount)
    }

    @Test
    fun `stress adds and subtracts decimal round trip to original value`() {
        for (i in -500..500) {
            val base = Decimal.fromInt(i).movePointLeft(2)
            val delta = Decimal.fromInt(i * i + 7).movePointLeft(3)

            assertPlain(base.toPlainString(), (base + delta - delta))
        }
    }

    @Test
    fun `stress decimal multiplication division and scale produce expected cents`() {
        for (i in 1..250) {
            val price = Decimal.fromInt(i * 137).movePointLeft(2)
            val quantity = Decimal.fromInt((i % 9) + 1)
            val total = price * quantity
            val unit = total.divide(quantity, scale = 2, rounding = Rounding.HALF_UP)

            assertPlain(price.toPlainString(), unit)
            assertEquals(price.toFormattedString(scale = 2), unit.toFormattedString(scale = 2))
        }
    }

    @Test
    fun `stress decimal formatted round trips with several separator pairs`() {
        val separatorPairs = listOf(
            Triple('.', ',', setOf(',', ' ', '_')),
            Triple(',', '.', setOf('.')),
            Triple('~', '\'', setOf('\'')),
        )

        for (i in 1..200) {
            val value = Decimal.fromInt(i * 12345).movePointLeft(2)
            separatorPairs.forEach { (decimalSeparator, groupingSeparator, groupingSeparators) ->
                val formatted = value.toFormattedString(
                    scale = 2,
                    decimalSeparator = decimalSeparator,
                    groupingSeparator = groupingSeparator,
                )
                val parsed = Decimal.parseFormatted(
                    formatted,
                    decimalSeparator = decimalSeparator,
                    groupingSeparators = groupingSeparators,
                )

                assertPlain(value.toPlainString(), parsed)
            }
        }
    }

    @Test
    fun `adds decimal crypto balances with many fractional digits`() {
        val initial = decimal("0.123456789123456789")
        val deposit = decimal("1.000000000000000001")
        val reward = decimal("0.000000000000000009")

        assertPlain("1.123456789123456799", initial + deposit + reward)
    }

    @Test
    fun `subtracts decimal crypto network fees without losing precision`() {
        val balance = decimal("5.000000000000000000")
        val transfer = decimal("1.234567890123456789")
        val fee = decimal("0.000000000000000021")

        assertPlain("3.76543210987654319", balance - transfer - fee)
    }

    @Test
    fun `multiplies decimal token amount by high precision price`() {
        val tokenAmount = decimal("1.234567891234")
        val unitPrice = decimal("0.000012345678")

        assertPlain("0.000015241577654313", (tokenAmount * unitPrice).setScale(18, Rounding.DOWN))
    }

    @Test
    fun `multiplies near precision limit with deterministic rounding`() {
        val left = decimal("9999999999999999999")
        val right = decimal("9999999999999999999")

        assertPlain("99999999999999999980000000000000000001", left * right)
    }

    @Test
    fun `divides decimal wei amount into ether units`() {
        val wei = Decimal.ofInteger("123456789123456789123456789")
        val ether = wei.divideInteger("1000000000000000000", scale = 18, rounding = Rounding.DOWN)

        assertPlain("123456789.123456789123456789", ether)
    }

    @Test
    fun `moves decimal crypto base units into display units`() {
        val satoshis = Decimal.ofInteger("2100000000000000")
        val bitcoins = satoshis.movePointLeft(8)

        assertPlain("21000000", bitcoins)
        assertPlain("2100000000000000", bitcoins.movePointRight(8))
    }

    @Test
    fun `rounds decimal crypto quote to exchange tick sizes`() {
        val quote = decimal("0.000000123456789123456789")

        assertPlain("0.000000123456789123", quote.setScale(18, Rounding.DOWN))
        assertPlain("0.000000123456789124", quote.setScale(18, Rounding.UP))
        assertPlain("0.000000123456789123", quote.setScale(18, Rounding.HALF_UP))
    }

    @Test
    fun `formats decimal crypto balances with token precision`() {
        val ethBalance = decimal("1234.890123456789")
        val btcBalance = decimal("0.123456789")

        assertEquals("1,234.890123456789", ethBalance.toFormattedString(scale = 12, rounding = Rounding.DOWN))
        assertEquals("0.12345679", btcBalance.toFormattedString(scale = 8, rounding = Rounding.HALF_UP, groupingSeparator = null))
    }

    @Test
    fun `parses decimal crypto balances from grouped exchange input`() {
        val parsed = Decimal.parseFormatted(
            "1_234.890123456789",
            decimalSeparator = '.',
            groupingSeparators = setOf('_'),
        )

        assertPlain("1234.890123456789", parsed)
    }

    @Test
    fun `stress decimal crypto micro trades preserve accumulated precision`() {
        var balance = decimal("0.000000000000000000")
        val fillSize = decimal("0.000000010000000001")
        val rebate = decimal("0.000000000000000003")

        for (i in 1..500) {
            balance += fillSize
            if (i % 10 == 0) {
                balance += rebate
            }
        }

        assertPlain("0.00000500000000065", balance)
    }

    @Test
    fun `stress decimal crypto fees rounded per fill`() {
        val notional = decimal("0.123456789123456789")
        val feeRate = decimal("0.000750000000000000")
        var totalFees = decimal("0.000000000000000000")

        for (i in 1..100) {
            val multiplier = Decimal.fromInt(i).movePointLeft(2)
            val fee = (notional * multiplier * feeRate).setScale(18, Rounding.UP)
            totalFees += fee
        }

        assertPlain("0.004675925888050973", totalFees)
    }

    @Test
    fun `calculates deterministic exchange conversion vectors`() {
        val vectors = listOf(
            ExchangeVector("100.00", "0.923456", 2, Rounding.HALF_UP, "92.35"),
            ExchangeVector("100.00", "0.923456", 2, Rounding.DOWN, "92.34"),
            ExchangeVector("19.99", "137.425", 2, Rounding.HALF_UP, "2747.13"),
            ExchangeVector("19.99", "137.425", 2, Rounding.DOWN, "2747.12"),
            ExchangeVector("0.00000001", "123456789.123456789", 18, Rounding.DOWN, "1.23456789123456789"),
            ExchangeVector("-42.50", "1.075", 2, Rounding.HALF_UP, "-45.69"),
        )

        vectors.forEach { vector ->
            val converted = (decimal(vector.amount) * decimal(vector.rate)).setScale(vector.scale, vector.rounding)

            assertPlain(vector.expected, converted)
        }
    }

    @Serializable
    private data class DecimalBox(val amount: Decimal)

    private data class ExchangeVector(
        val amount: String,
        val rate: String,
        val scale: Int,
        val rounding: Rounding,
        val expected: String,
    )

    private fun assertPlain(expected: String, actual: Decimal) {
        assertEquals(expected, actual.toPlainString())
    }

}
