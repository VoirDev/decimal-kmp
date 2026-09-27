package dev.voir.decimal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DecimalComparisonTest {
    @Test
    fun `compares decimals numerically`() {
        assertEquals(0, decimal("1").compareTo(decimal("1.00")))
        assertEquals(0, decimal("0").compareTo(decimal("-0.000")))
        assertTrue(decimal("-2") < decimal("1"))
        assertTrue(decimal("0.1") > decimal("0.09"))
        assertTrue(decimal("-0.1") < decimal("-0.09"))
        assertTrue(decimal("100") > decimal("99.999999999999999999"))
        assertTrue(decimal("0." + "0".repeat(127) + "1") > Decimal.zero())
        assertTrue(decimal("-" + "1" + "0".repeat(127)) < decimal("-99"))
    }

    @Test
    fun `orders decimals with standard library helpers`() {
        val values = listOf("10", "-1.5", "0", "2.25", "-1.50", "0.001").map(::decimal)

        assertEquals(listOf("-1.5", "-1.5", "0", "0.001", "2.25", "10"), values.sorted().map { it.toPlainString() })
        assertEquals(decimal("-1.5"), values.min())
        assertEquals(decimal("10"), values.max())
        assertEquals(decimal("2"), maxOf(decimal("2"), decimal("1.999")))
        assertEquals(decimal("0"), decimal("-5").coerceIn(decimal("0"), decimal("1")))
    }

    @Test
    fun `compares numerically equivalent decimals consistently`() {
        assertEquals(decimal("1"), decimal("1.0"))
        assertEquals(decimal("1").hashCode(), decimal("1.0").hashCode())
        assertEquals(decimal("0"), decimal("0.000"))
        assertEquals(decimal("0").hashCode(), decimal("0.000").hashCode())
    }

    @Test
    fun `keeps equality hashing and ordering consistent`() {
        val one = decimal("1")
        val oneWithScale = decimal("1.000")

        assertEquals(one, oneWithScale)
        assertEquals(one.hashCode(), oneWithScale.hashCode())
        assertEquals(0, one.compareTo(oneWithScale))
        assertEquals(1, setOf(one, oneWithScale).size)
    }

    @Test
    fun `hashes parsed and computed values alike`() {
        val parsed = decimal("1000")
        val computed = decimal("999") + decimal("1")

        assertEquals(parsed, computed)
        assertEquals(parsed.hashCode(), computed.hashCode())
        assertEquals(decimal("0.5").hashCode(), (decimal("1") / decimal("2")).hashCode())
        assertEquals(Decimal.ofInteger("1000").hashCode(), Decimal.fromLong(1000).hashCode())
    }

    @Test
    fun `negates decimals`() {
        assertEquals("-1.5", (-decimal("1.5")).toPlainString())
        assertEquals("3", decimal("-3").negate().toPlainString())
        assertEquals("0", (-Decimal.zero()).toPlainString())
        assertEquals("0", decimal("-0.00").negate().toPlainString())
    }

    @Test
    fun `reports sign and zero`() {
        assertEquals(-1, decimal("-0.01").signum())
        assertEquals(0, decimal("0.000").signum())
        assertEquals(0, decimal("-0").signum())
        assertEquals(1, decimal("5").signum())
        assertTrue(decimal("-0.000").isZero())
        assertFalse(decimal("0." + "0".repeat(127) + "1").isZero())
    }

    @Test
    fun `sums decimals`() {
        assertEquals("6.6", listOf("1.1", "2.2", "3.3").map(::decimal).sum().toPlainString())
        assertEquals("0", emptyList<Decimal>().sum().toPlainString())
        assertEquals("0", listOf("1.25", "-1.25").map(::decimal).sum().toPlainString())
    }
}
