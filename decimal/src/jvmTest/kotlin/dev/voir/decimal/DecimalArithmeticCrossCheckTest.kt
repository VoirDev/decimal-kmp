package dev.voir.decimal

import kotlin.math.sign
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cross-checks the common text arithmetic used on Apple targets against `BigDecimal`.
 *
 * The implementations share only the choice of the last kept digit, so agreement on random inputs
 * catches rounding and precision drift between platforms.
 */
class DecimalArithmeticCrossCheckTest {
    @Test
    fun `common text division matches BigDecimal division for random inputs`() {
        val random = Random(SEED)

        repeat(ITERATIONS) { iteration ->
            val dividend = random.nextPortableDecimalText()
            val divisor = random.nextPortableDecimalText()
            val scale = random.nextScale()
            val rounding = Rounding.entries.random(random)

            val expected = outcome { decimal(dividend).divide(decimal(divisor), scale, rounding) }
            val actual = outcome {
                Decimal.parse(dividePlainDecimalText(dividend, divisor, scale, rounding))
            }

            assertEquals(
                expected,
                actual,
                "Iteration $iteration: $dividend / $divisor, scale $scale, $rounding",
            )
        }
    }

    @Test
    fun `common text addition and subtraction match BigDecimal for random inputs`() {
        val random = Random(SEED)

        repeat(ITERATIONS) { iteration ->
            val left = random.nextPortableDecimalText()
            val right = random.nextPortableDecimalText()

            assertEquals(
                outcome { decimal(left) + decimal(right) },
                outcome { Decimal.parse(addPlainDecimalText(left, right)) },
                "Iteration $iteration: $left + $right",
            )
            assertEquals(
                outcome { decimal(left) - decimal(right) },
                outcome { Decimal.parse(subtractPlainDecimalText(left, right)) },
                "Iteration $iteration: $left - $right",
            )
        }
    }

    @Test
    fun `common text multiplication matches BigDecimal multiplication for random inputs`() {
        val random = Random(SEED)

        repeat(ITERATIONS) { iteration ->
            val left = random.nextPortableDecimalText()
            val right = random.nextPortableDecimalText()

            val expected = outcome { decimal(left) * decimal(right) }
            val actual = outcome { Decimal.parse(multiplyPlainDecimalText(left, right)) }

            assertEquals(expected, actual, "Iteration $iteration: $left * $right")
        }
    }

    @Test
    fun `common text scale rounding matches BigDecimal for random inputs`() {
        val random = Random(SEED)

        repeat(ITERATIONS) { iteration ->
            val value = random.nextPortableDecimalText()
            val scale = random.nextScale()
            val rounding = Rounding.entries.random(random)

            assertEquals(
                outcome { decimal(value).setScale(scale, rounding) },
                outcome { Decimal.parse(setScalePlainDecimalText(value, scale, rounding)) },
                "Iteration $iteration: $value at scale $scale, $rounding",
            )
        }
    }

    @Test
    fun `common text comparison matches BigDecimal for random inputs`() {
        val random = Random(SEED)

        repeat(ITERATIONS) { iteration ->
            val left = random.nextPortableDecimalText()
            // Compare nearby values too, since random pairs rarely share a leading exponent.
            val right = if (random.nextBoolean()) random.nextPortableDecimalText() else left.nudged(random)
                .takeIf { candidate -> runCatching { decimal(candidate) }.isSuccess } ?: left

            assertEquals(
                decimal(left).compareTo(decimal(right)).sign,
                comparePlainDecimalText(left, right).sign,
                "Iteration $iteration: $left <=> $right",
            )
        }
    }

    /**
     * Returns plain text that differs from this value in one digit, or the value itself.
     */
    private fun String.nudged(random: Random): String {
        val digitIndexes = indices.filter { this[it].isDigit() }
        val index = digitIndexes.random(random)
        return replaceRange(index, index + 1, random.nextInt(10).toString())
    }

    /**
     * Returns the plain result, or the exception type when the operation fails.
     */
    private fun outcome(block: () -> Decimal): String = try {
        block().toPlainString()
    } catch (exception: IllegalArgumentException) {
        "IllegalArgumentException"
    } catch (exception: ArithmeticException) {
        "ArithmeticException"
    }

    /**
     * Returns plain text for a random value inside the portable envelope, including zero.
     */
    private fun Random.nextPortableDecimalText(): String {
        if (nextInt(20) == 0) return "0"

        val digitCount = if (nextBoolean()) nextInt(1, 6) else nextInt(1, DECIMAL_MAX_SIGNIFICANT_DIGITS + 1)
        // Keep both ends non-zero so the chosen exponent is the least significant digit's exponent.
        val coefficient = buildString {
            append(nextInt(1, 10))
            repeat(digitCount - 2) { append(nextInt(10)) }
            if (digitCount > 1) append(nextInt(1, 10))
        }
        val exponent = when (nextInt(4)) {
            0 -> nextInt(DECIMAL_MIN_EXPONENT, DECIMAL_MAX_EXPONENT + 1)
            else -> nextInt(-20, 21)
        }
        val unsigned = when {
            exponent >= 0 -> coefficient + "0".repeat(exponent)
            else -> {
                val padded = coefficient.padStart(-exponent + 1, '0')
                padded.dropLast(-exponent) + "." + padded.takeLast(-exponent)
            }
        }

        return if (nextBoolean()) "-$unsigned" else unsigned
    }

    /**
     * Returns a random scale biased toward common money and token precisions.
     */
    private fun Random.nextScale(): Int = when (nextInt(10)) {
        0 -> DECIMAL_MAX_SCALE
        1 -> nextInt(39, 200)
        else -> nextInt(0, 39)
    }

    private companion object {
        const val SEED = 20260927
        const val ITERATIONS = 50_000
    }
}
