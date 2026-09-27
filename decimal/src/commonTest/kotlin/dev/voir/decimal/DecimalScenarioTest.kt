package dev.voir.decimal

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers money, exchange, and crypto workflows plus repeated-operation stress cases.
 */
class DecimalScenarioTest {
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
            assertEquals(price.toFormattedString(maximumFractionDigits = 2), unit.toFormattedString(maximumFractionDigits = 2))
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
                    maximumFractionDigits = 2,
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

        assertEquals("1,234.890123456789", ethBalance.toFormattedString(maximumFractionDigits = 12, rounding = Rounding.DOWN))
        assertEquals("0.12345679", btcBalance.toFormattedString(maximumFractionDigits = 8, rounding = Rounding.HALF_UP, groupingSeparator = null))
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

    private data class ExchangeVector(
        val amount: String,
        val rate: String,
        val scale: Int,
        val rounding: Rounding,
        val expected: String,
    )
}
