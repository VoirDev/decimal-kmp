package dev.voir.decimal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Covers addition, subtraction, multiplication, point moves, and their 38-digit rounding.
 */
class DecimalArithmeticTest {
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
        assertFailsWith<IllegalArgumentException> {
            decimal("1" + "0".repeat(127)).multiplyInteger("10")
        }
        assertFailsWith<IllegalArgumentException> {
            decimal("2") * decimal("5" + "0".repeat(127))
        }
        assertFailsWith<IllegalArgumentException> {
            decimal("0." + "0".repeat(63) + "1") * decimal("0." + "0".repeat(64) + "1")
        }
    }

    @Test
    fun `rejects products that overflow after rounding to 38 digits`() {
        val large = decimal("1".repeat(38) + "0".repeat(50))

        assertFailsWith<IllegalArgumentException> { large * large }
        assertFailsWith<IllegalArgumentException> { large * large.multiplyInteger("-1") }
    }

    @Test
    fun `multiplies products at the exponent limits`() {
        assertPlain("25" + "0".repeat(127), decimal("5") * decimal("5" + "0".repeat(127)))
        assertPlain(
            "0." + "0".repeat(126) + "1",
            decimal("0." + "0".repeat(63) + "5") * decimal("0." + "0".repeat(63) + "2"),
        )

        // The exact product ends below 1e-128, but its 38-digit rounding fits the envelope.
        val small = decimal("0." + "0".repeat(32) + "1".repeat(38))
        assertPlain(
            "0." + "0".repeat(65) + "12345679012345679012345679012345679012",
            small * small,
        )
    }

    @Test
    fun `multiplies by rounding products half up to 38 significant digits`() {
        val nines = decimal("9".repeat(20))

        assertPlain("99999999999999999998" + "0".repeat(20), nines * nines)
        assertPlain("-99999999999999999998" + "0".repeat(20), nines * nines.multiplyInteger("-1"))
        // (3e37 + 1) * 5 = 15 followed by 36 zeros and 5; the discarded 5 rounds half up.
        assertPlain("15" + "0".repeat(35) + "10", decimal("3" + "0".repeat(36) + "1").multiplyInteger("5"))
    }

    @Test
    fun `adds by rounding sums half up to 38 significant digits`() {
        assertPlain("1" + "0".repeat(38), decimal("9".repeat(38)) + decimal("0.5"))
        assertPlain("-1" + "0".repeat(38), decimal("-" + "9".repeat(38)) - decimal("0.5"))
    }

    @Test
    fun `rejects additions above the largest exponent`() {
        val largest = decimal("9".repeat(38) + "0".repeat(127))

        assertFailsWith<IllegalArgumentException> { largest + largest }
        assertFailsWith<IllegalArgumentException> { largest.multiplyInteger("-1") - largest }
    }

    @Test
    fun `moves decimal point left and right`() {
        assertPlain("1.2345", decimal("12345").movePointLeft(4))
        assertPlain("1234500", decimal("123.45").movePointRight(4))
        assertPlain("-0.001234", decimal("-1234").movePointLeft(6))
        assertPlain("0", Decimal.zero().movePointRight(100))
        assertPlain("0", Decimal.zero().movePointRight(Int.MAX_VALUE))
        assertPlain("1", Decimal.zero().movePointLeft(Int.MAX_VALUE) + decimal("1"))

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
    fun `returns decimal absolute values and string representations`() {
        assertPlain("123.45", decimal("-123.45").abs())
        assertPlain("123.45", decimal("123.45").abs())
        assertEquals("123.45", decimal("123.45").toString())
        assertEquals("123.45", decimal("123.45").toPlainString())
    }

    @Test
    fun `applies decimal transform operation`() {
        val transformed = decimal("10").transform { it.multiplyInteger("3").subtract(decimal("1.5")) }

        assertPlain("28.5", transformed)
    }
}
