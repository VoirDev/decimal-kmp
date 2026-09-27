package dev.voir.decimal

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

/**
 * Android and JVM decimal implementation backed by `java.math.BigDecimal`.
 *
 * Results are constrained to the same decimal envelope used by Apple targets:
 * 38 significant digits and a decimal exponent from -128 through 127.
 */
@Serializable(with = DecimalSerializer::class)
public actual class Decimal internal constructor(
    /**
     * Native decimal value stored without exposing BigDecimal-specific behavior in common code.
     */
    private val value: BigDecimal,
) : Comparable<Decimal> {
    /**
     * Creates JVM and Android decimal values.
     */
    public actual companion object {
        /**
         * Creates a decimal from plain text.
         *
         * @param value Decimal text accepted by the common parser contract.
         */
        public actual fun parse(value: String): Decimal = of(value)

        /**
         * Creates a decimal from plain text.
         *
         * @param value Decimal text accepted by the common parser contract.
         */
        public actual fun of(value: String): Decimal =
            fromValidatedText(parsePlainDecimalText(value, integerOnly = false))

        /**
         * Creates a decimal from formatted text.
         *
         * @param value Formatted decimal text.
         * @param decimalSeparator Separator used for the fractional part.
         * @param groupingSeparators Candidate separators used for digit grouping.
         */
        public actual fun parseFormatted(
            value: String,
            decimalSeparator: Char,
            groupingSeparators: Set<Char>
        ): Decimal = fromValidatedText(parseFormattedDecimalText(value, decimalSeparator, groupingSeparators))

        /**
         * Creates a decimal from plain text, or returns `null` when it is not accepted.
         *
         * @param value Decimal text accepted by the common parser contract.
         */
        public actual fun parseOrNull(value: String): Decimal? = try {
            of(value)
        } catch (_: IllegalArgumentException) {
            null
        }

        /**
         * Creates a decimal from formatted text, or returns `null` when it is not accepted.
         *
         * @param value Formatted decimal text.
         * @param decimalSeparator Separator used for the fractional part.
         * @param groupingSeparators Candidate separators used for digit grouping.
         */
        public actual fun parseFormattedOrNull(
            value: String,
            decimalSeparator: Char,
            groupingSeparators: Set<Char>,
        ): Decimal? = try {
            parseFormatted(value, decimalSeparator, groupingSeparators)
        } catch (_: IllegalArgumentException) {
            null
        }

        /**
         * Creates a decimal from integer text.
         *
         * @param value Integer text.
         */
        public actual fun ofInteger(value: String): Decimal =
            fromValidatedText(parsePlainDecimalText(value, integerOnly = true))

        /**
         * Creates a decimal from an integer value.
         *
         * @param value Integer value.
         */
        public actual fun fromInt(value: Int): Decimal = checked(BigDecimal.valueOf(value.toLong()))

        /**
         * Creates a decimal from a long value.
         *
         * @param value Long value.
         */
        public actual fun fromLong(value: Long): Decimal = checked(BigDecimal.valueOf(value))

        /**
         * Creates a decimal from a finite double value.
         *
         * @param value Double value.
         */
        public actual fun fromDouble(value: Double): Decimal {
            requireFiniteDouble(value)
            return checked(BigDecimal.valueOf(value))
        }

        /**
         * Returns zero.
         */
        public actual fun zero(): Decimal = checked(BigDecimal.ZERO)

        /**
         * Returns one.
         */
        public actual fun one(): Decimal = checked(BigDecimal.ONE)

        /**
         * Creates a decimal from canonical text that already passed the common parser.
         *
         * Integer text can still end in zeros, which are stripped so every stored value has the
         * same representation, and therefore the same hash code, as equal arithmetic results.
         *
         * @param text Canonical plain decimal text inside the portable envelope.
         */
        private fun fromValidatedText(text: String): Decimal = Decimal(BigDecimal(text).stripTrailingZeros())

        /**
         * Creates a decimal after enforcing Apple-compatible numeric limits.
         *
         * Stored values never keep trailing zeros, so their scale is the number of meaningful
         * fractional digits and later operations never carry large zero padding.
         *
         * @param value Native value to wrap.
         */
        private fun checked(value: BigDecimal): Decimal {
            val canonical = value.stripTrailingZeros()
            canonical.requireAppleCompatible()
            return Decimal(canonical)
        }
    }

    /**
     * Adds another decimal.
     *
     * @param other Value to add.
     */
    public actual fun add(other: Decimal): Decimal =
        binary(other) { left, right -> left.add(right, DECIMAL_CONTEXT) }

    /**
     * Adds another decimal.
     *
     * @param other Value to add.
     */
    public actual operator fun plus(other: Decimal): Decimal = add(other)

    /**
     * Subtracts another decimal.
     *
     * @param other Value to subtract.
     */
    public actual fun subtract(other: Decimal): Decimal =
        binary(other) { left, right -> left.subtract(right, DECIMAL_CONTEXT) }

    /**
     * Subtracts another decimal.
     *
     * @param other Value to subtract.
     */
    public actual operator fun minus(other: Decimal): Decimal = subtract(other)

    /**
     * Multiplies by another decimal.
     *
     * @param other Multiplier.
     */
    public actual fun multiply(other: Decimal): Decimal =
        binary(other) { left, right -> left.multiply(right, DECIMAL_CONTEXT) }

    /**
     * Multiplies by another decimal.
     *
     * @param other Multiplier.
     */
    public actual operator fun times(other: Decimal): Decimal = multiply(other)

    /**
     * Multiplies by an integer string.
     *
     * @param integer Integer multiplier.
     */
    public actual fun multiplyInteger(integer: String): Decimal = multiply(ofInteger(integer))

    /**
     * Divides by another decimal with explicit rounding.
     *
     * @param other Divisor.
     * @param scale Fractional digits to keep.
     * @param rounding Rounding mode.
     */
    public actual fun divide(other: Decimal, scale: Int, rounding: Rounding): Decimal {
        requirePortableScale(scale)
        if (other.value.signum() == 0) throw divisionByZero(toPlainString())
        if (value.signum() == 0) return zero()

        return binary(other) { left, right ->
            val keptExponent = divisionRoundingExponent(left.quotientLeadingExponent(right), scale)

            // A negative BigDecimal scale keeps digits above the units place, so this rounds once.
            left.divide(
                right,
                -keptExponent,
                rounding.toJvmRoundingMode()
            )
        }
    }

    /**
     * Divides by another decimal using a default scale of 18.
     *
     * @param other Divisor.
     */
    public actual operator fun div(other: Decimal): Decimal =
        divide(other, scale = 18, rounding = Rounding.HALF_UP)

    /**
     * Divides by an integer string with explicit rounding.
     *
     * @param integer Integer divisor.
     * @param scale Fractional digits to keep.
     * @param rounding Rounding mode.
     */
    public actual fun divideInteger(integer: String, scale: Int, rounding: Rounding): Decimal {
        return divide(ofInteger(integer), scale, rounding)
    }

    /**
     * Moves the decimal point left.
     *
     * @param places Number of places to move.
     */
    public actual fun movePointLeft(places: Int): Decimal {
        requirePortablePlaces(places)
        requirePortablePointMove(toPlainString(), places, toLeft = true)
        return unary { it.movePointLeft(places) }
    }

    /**
     * Moves the decimal point right.
     *
     * @param places Number of places to move.
     */
    public actual fun movePointRight(places: Int): Decimal {
        requirePortablePlaces(places)
        requirePortablePointMove(toPlainString(), places, toLeft = false)
        return unary { it.movePointRight(places) }
    }

    /**
     * Sets the scale with explicit rounding.
     *
     * @param scale Fractional digits to keep.
     * @param rounding Rounding mode.
     */
    public actual fun setScale(scale: Int, rounding: Rounding): Decimal {
        requirePortableScale(scale)
        // Stored values have no trailing zeros; padding up to a large scale only to strip it
        // again in checked() is quadratic in the scale.
        if (value.scale() <= scale) return this
        return unary { it.setScale(scale, rounding.toJvmRoundingMode()) }
    }

    /**
     * Converts to plain string.
     */
    public actual fun toPlainString(): String = value.toPlainString()

    /**
     * Converts to integer string.
     */
    public actual fun toIntegerString(): String = value.setScale(0, RoundingMode.HALF_UP)
        .toBigIntegerExact()
        .toString()

    /**
     * Converts to a human-friendly string.
     *
     * @param maximumFractionDigits Maximum fractional digits to display.
     * @param minimumFractionDigits Minimum fractional digits to display.
     * @param rounding Rounding mode.
     * @param decimalSeparator Decimal separator.
     * @param groupingSeparator Optional grouping separator.
     */
    public actual fun toFormattedString(
        maximumFractionDigits: Int,
        minimumFractionDigits: Int,
        rounding: Rounding,
        decimalSeparator: Char,
        groupingSeparator: Char?,
    ): String {
        requireFormatArguments(maximumFractionDigits, minimumFractionDigits, decimalSeparator, groupingSeparator)

        // DecimalFormat keeps the sign of negative values that round to zero, printing "-0".
        // Round first; stored zero has no sign, so those values format as zero like Apple targets.
        val displayed = setScale(maximumFractionDigits, rounding).value

        return decimalFormatter(decimalSeparator, groupingSeparator).apply {
            this.minimumFractionDigits = minimumFractionDigits
            this.maximumFractionDigits = maximumFractionDigits
            roundingMode = rounding.toJvmRoundingMode()
        }.format(displayed)
    }

    /**
     * Returns the absolute value.
     */
    public actual fun abs(): Decimal = unary { it.abs() }

    /**
     * Returns the negated value.
     */
    public actual fun negate(): Decimal = unary { it.negate() }

    /**
     * Returns the negated value.
     */
    public actual operator fun unaryMinus(): Decimal = negate()

    /**
     * Returns the sign of this value.
     */
    public actual fun signum(): Int = value.signum()

    /**
     * Returns whether this value is zero.
     */
    public actual fun isZero(): Boolean = value.signum() == 0

    /**
     * Compares decimal values by numeric value.
     *
     * @param other Value to compare with.
     */
    actual override fun compareTo(other: Decimal): Int = value.compareTo(other.value)

    /**
     * Converts this value to debug text.
     */
    actual override fun toString(): String = toPlainString()

    /**
     * Compares decimal values by numeric value.
     *
     * @param other Candidate value.
     */
    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Decimal) return false
        return value.compareTo(other.value) == 0
    }

    /**
     * Returns a hash code for this decimal.
     */
    actual override fun hashCode(): Int = value.hashCode()

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
    Rounding.HALF_EVEN -> RoundingMode.HALF_EVEN
    Rounding.FLOOR -> RoundingMode.FLOOR
    Rounding.CEILING -> RoundingMode.CEILING
}

/**
 * Returns the base-10 exponent of the most significant digit of `this / divisor`.
 *
 * Both values must be non-zero.
 *
 * @param divisor Non-zero divisor.
 */
private fun BigDecimal.quotientLeadingExponent(divisor: BigDecimal): Int {
    val dividendLeadingExponent = leadingExponent()
    val divisorLeadingExponent = divisor.leadingExponent()

    // Compare both magnitudes scaled into [1, 10); a smaller dividend loses one leading place.
    val dividendIsSmaller = abs().scaleByPowerOfTen(-dividendLeadingExponent) <
        divisor.abs().scaleByPowerOfTen(-divisorLeadingExponent)

    return dividendLeadingExponent - divisorLeadingExponent - if (dividendIsSmaller) 1 else 0
}

/**
 * Returns the base-10 exponent of this non-zero value's most significant digit.
 */
private fun BigDecimal.leadingExponent(): Int = precision() - scale() - 1

/**
 * Requires that a JVM value can also be represented by `NSDecimalNumber`.
 *
 * Apple decimals store up to 38 significant decimal digits and a base-10 exponent in the range
 * -128 through 127. `BigDecimal` can exceed both limits, so JVM/Android checks every boundary.
 * This value must have no trailing zeros, so its precision and scale describe the significant
 * digits directly.
 */
private fun BigDecimal.requireAppleCompatible() {
    if (signum() == 0) return

    require(precision() <= DECIMAL_MAX_SIGNIFICANT_DIGITS) {
        tooManyDigitsMessage(toPlainString(), precision())
    }
    require(-scale() in DECIMAL_MIN_EXPONENT..DECIMAL_MAX_EXPONENT) {
        exponentOutOfRangeMessage(toPlainString(), -scale().toLong())
    }
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
