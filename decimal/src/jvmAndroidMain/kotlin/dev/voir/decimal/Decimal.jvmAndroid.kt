package dev.voir.decimal

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Android and JVM decimal implementation backed by `java.math.BigDecimal`.
 *
 * Results are constrained to the same decimal envelope used by Apple targets:
 * 38 significant digits and a decimal exponent from -128 through 127.
 */
@Serializable(with = DecimalSerializer::class)
actual class Decimal internal constructor(
    /**
     * Native decimal value stored without exposing BigDecimal-specific behavior in common code.
     */
    private val value: BigDecimal,
) {
    /**
     * Creates JVM and Android decimal values.
     */
    actual companion object {
        private val DECIMAL_RE = Regex("""^[+-]?\d+(\.\d+)?$""")
        private val INTEGER_RE = Regex("""^[+-]?\d+$""")

        /**
         * Creates a decimal from plain text.
         *
         * @param value Decimal text accepted by the common parser contract.
         */
        actual fun parse(value: String): Decimal = of(value)

        /**
         * Creates a decimal from plain text.
         *
         * @param value Decimal text accepted by the common parser contract.
         */
        actual fun of(value: String): Decimal {
            val s = value.trim()
            if (s.isEmpty()) throw IllegalArgumentException("Empty decimal string")
            if (!DECIMAL_RE.matches(s)) throw IllegalArgumentException("Invalid decimal string: $value")

            val v = try {
                BigDecimal(s, MathContext.UNLIMITED)
            } catch (e: Exception) {
                // Regex validation should prevent this, but keep parser failures contextual.
                throw IllegalArgumentException("Invalid decimal string: $value", e)
            }

            return checked(v)
        }

        /**
         * Creates a decimal from formatted text.
         *
         * @param value Formatted decimal text.
         * @param decimalSeparator Separator used for the fractional part.
         * @param groupingSeparators Candidate separators used for digit grouping.
         */
        actual fun parseFormatted(
            value: String,
            decimalSeparator: Char,
            groupingSeparators: Set<Char>
        ): Decimal {
            val s = value.trim()
            if (s.isEmpty()) throw IllegalArgumentException("Empty formatted decimal string")

            val groupingOptions = listOf(null) + groupingSeparators.minus(decimalSeparator).sorted()
            for (groupingSeparator in groupingOptions) {
                if (!isFormattedDecimalText(s, decimalSeparator, groupingSeparator)) continue

                return of(s.toPlainDecimalText(decimalSeparator, groupingSeparator))
            }

            throw IllegalArgumentException("Invalid formatted decimal string: $value")
        }

        /**
         * Creates a decimal from integer text.
         *
         * @param value Integer text.
         */
        actual fun ofInteger(value: String): Decimal {
            val s = value.trim()
            if (s.isEmpty()) throw IllegalArgumentException("Empty integer string")

            if (!INTEGER_RE.matches(s)) throw IllegalArgumentException("Invalid integer string: $value")

            val v = try {
                BigDecimal(s, MathContext.UNLIMITED)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid integer string: $value", e)
            }

            return checked(v)
        }

        /**
         * Creates a decimal from an integer value.
         *
         * @param value Integer value.
         */
        actual fun fromInt(value: Int): Decimal = checked(BigDecimal.valueOf(value.toLong()))

        /**
         * Creates a decimal from a long value.
         *
         * @param value Long value.
         */
        actual fun fromLong(value: Long): Decimal = checked(BigDecimal.valueOf(value))

        /**
         * Creates a decimal from a finite double value.
         *
         * @param value Double value.
         */
        actual fun fromDouble(value: Double): Decimal {
            require(value.isFinite()) { "Decimal cannot be created from a non-finite Double." }
            return checked(BigDecimal.valueOf(value))
        }

        /**
         * Returns zero.
         */
        actual fun zero(): Decimal = checked(BigDecimal.ZERO)

        /**
         * Returns one.
         */
        actual fun one(): Decimal = checked(BigDecimal.ONE)

        /**
         * Creates a decimal after enforcing Apple-compatible numeric limits.
         *
         * @param value Native value to wrap.
         */
        private fun checked(value: BigDecimal): Decimal {
            value.requireAppleCompatible()
            return Decimal(value)
        }
    }

    /**
     * Adds another decimal.
     *
     * @param other Value to add.
     */
    actual fun add(other: Decimal): Decimal =
        binary(other) { left, right -> left.add(right, DECIMAL_CONTEXT) }

    /**
     * Adds another decimal.
     *
     * @param other Value to add.
     */
    actual operator fun plus(other: Decimal): Decimal = add(other)

    /**
     * Subtracts another decimal.
     *
     * @param other Value to subtract.
     */
    actual fun subtract(other: Decimal): Decimal =
        binary(other) { left, right -> left.subtract(right, DECIMAL_CONTEXT) }

    /**
     * Subtracts another decimal.
     *
     * @param other Value to subtract.
     */
    actual operator fun minus(other: Decimal): Decimal = subtract(other)

    /**
     * Multiplies by another decimal.
     *
     * @param other Multiplier.
     */
    actual fun multiply(other: Decimal): Decimal =
        binary(other) { left, right -> left.multiply(right, DECIMAL_CONTEXT) }

    /**
     * Multiplies by another decimal.
     *
     * @param other Multiplier.
     */
    actual operator fun times(other: Decimal): Decimal = multiply(other)

    /**
     * Multiplies by an integer string.
     *
     * @param integer Integer multiplier.
     */
    actual fun multiplyInteger(integer: String): Decimal = multiply(ofInteger(integer))

    /**
     * Divides by another decimal with explicit rounding.
     *
     * @param other Divisor.
     * @param scale Fractional digits to keep.
     * @param rounding Rounding mode.
     */
    actual fun divide(other: Decimal, scale: Int, rounding: Rounding): Decimal {
        requirePortableScale(scale)
        return binary(other) { left, right ->
            left.divide(
                right,
                scale,
                rounding.toJvmRoundingMode()
            )
        }
    }

    /**
     * Divides by another decimal using a default scale of 18.
     *
     * @param other Divisor.
     */
    actual operator fun div(other: Decimal): Decimal =
        divide(other, scale = 18, rounding = Rounding.HALF_UP)

    /**
     * Divides by an integer string with explicit rounding.
     *
     * @param integer Integer divisor.
     * @param scale Fractional digits to keep.
     * @param rounding Rounding mode.
     */
    actual fun divideInteger(integer: String, scale: Int, rounding: Rounding): Decimal {
        return divide(ofInteger(integer), scale, rounding)
    }

    /**
     * Moves the decimal point left.
     *
     * @param places Number of places to move.
     */
    actual fun movePointLeft(places: Int): Decimal {
        require(places >= 0) { "Places must be non-negative." }
        requirePortablePointMove(toPlainString(), places, toLeft = true)
        return unary { it.movePointLeft(places) }
    }

    /**
     * Moves the decimal point right.
     *
     * @param places Number of places to move.
     */
    actual fun movePointRight(places: Int): Decimal {
        require(places >= 0) { "Places must be non-negative." }
        requirePortablePointMove(toPlainString(), places, toLeft = false)
        return unary { it.movePointRight(places) }
    }

    /**
     * Sets the scale with explicit rounding.
     *
     * @param scale Fractional digits to keep.
     * @param rounding Rounding mode.
     */
    actual fun setScale(scale: Int, rounding: Rounding): Decimal {
        requirePortableScale(scale)
        return unary { it.setScale(scale, rounding.toJvmRoundingMode()) }
    }

    /**
     * Converts to plain string.
     */
    actual fun toPlainString(): String = value.stripTrailingZeros().toPlainString()

    /**
     * Converts to integer string.
     */
    actual fun toIntegerString(): String = value.setScale(0, RoundingMode.HALF_UP)
        .toBigIntegerExact()
        .toString()

    /**
     * Converts to a human-friendly string.
     *
     * @param scale Fractional digits to display.
     * @param rounding Rounding mode.
     * @param decimalSeparator Decimal separator.
     * @param groupingSeparator Optional grouping separator.
     */
    actual fun toFormattedString(
        scale: Int,
        rounding: Rounding,
        decimalSeparator: Char,
        groupingSeparator: Char?,
    ): String {
        requirePortableScale(scale)
        require(groupingSeparator == null || groupingSeparator != decimalSeparator) {
            "Decimal separator cannot also be the grouping separator."
        }

        return decimalFormatter(decimalSeparator, groupingSeparator).apply {
            minimumFractionDigits = scale
            maximumFractionDigits = scale
            roundingMode = rounding.toJvmRoundingMode()
        }.format(value)
    }

    /**
     * Returns the absolute value.
     */
    actual fun abs(): Decimal = unary { it.abs() }

    /**
     * Converts this value to debug text.
     */
    override fun toString(): String = toPlainString()

    /**
     * Compares decimal values by numeric value.
     *
     * @param other Candidate value.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Decimal) return false
        return value.compareTo(other.value) == 0
    }

    /**
     * Returns a hash code for this decimal.
     */
    override fun hashCode(): Int = value.stripTrailingZeros().hashCode()

    /**
     * Applies a unary operation.
     *
     * @param operation Operation to apply.
     */
    private fun unary(operation: (BigDecimal) -> BigDecimal): Decimal =
        checked(operation(value))

    /**
     * Applies a binary operation.
     *
     * @param other Right-hand value.
     * @param operation Operation to apply.
     */
    private fun binary(other: Decimal, operation: (BigDecimal, BigDecimal) -> BigDecimal): Decimal {
        val left = value
        val right = other.value
        return checked(operation(left, right))
    }
}

/**
 * JVM arithmetic context matching the portable 38-significant-digit decimal contract.
 */
private val DECIMAL_CONTEXT = MathContext(DECIMAL_MAX_SIGNIFICANT_DIGITS, RoundingMode.HALF_UP)

/**
 * Converts common rounding to the equivalent JVM [RoundingMode].
 */
private fun Rounding.toJvmRoundingMode(): RoundingMode = when (this) {
    Rounding.HALF_UP -> RoundingMode.HALF_UP
    Rounding.DOWN -> RoundingMode.DOWN
    Rounding.UP -> RoundingMode.UP
}

/**
 * Requires that a JVM value can also be represented by `NSDecimalNumber`.
 *
 * Apple decimals store up to 38 significant decimal digits and a base-10 exponent in the range
 * -128 through 127. `BigDecimal` can exceed both limits, so JVM/Android checks every boundary.
 */
private fun BigDecimal.requireAppleCompatible() {
    if (signum() == 0) return

    requirePortableDecimalText(stripTrailingZeros().toPlainString())
}

/**
 * Creates a JVM decimal formatter with predictable separators.
 *
 * @param decimalSeparator Separator used for the fractional part.
 * @param groupingSeparator Optional separator inserted every three integer digits.
 */
private fun decimalFormatter(
    decimalSeparator: Char,
    groupingSeparator: Char?,
): DecimalFormat {
    val symbols = DecimalFormatSymbols(Locale.ROOT).apply {
        this.decimalSeparator = decimalSeparator
        if (groupingSeparator != null) {
            this.groupingSeparator = groupingSeparator
        }
    }

    return (DecimalFormat.getNumberInstance(Locale.ROOT) as DecimalFormat).apply {
        decimalFormatSymbols = symbols
        isParseBigDecimal = true
        isGroupingUsed = groupingSeparator != null
    }
}
