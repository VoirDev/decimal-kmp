package dev.voir.decimal

import kotlinx.serialization.Serializable

/**
 * Portable decimal number backed by each platform's native decimal type.
 *
 * `Decimal` keeps decimal values out of binary floating point math. JVM and Android use
 * `BigDecimal`, and Apple targets use `NSDecimalNumber`. To keep behavior portable, every
 * platform uses the `NSDecimalNumber` envelope: up to 38 significant digits and a decimal exponent
 * from -128 through 127. Values are immutable; every arithmetic operation returns a new decimal.
 *
 * Equality, hashing, and ordering are numeric: `1`, `1.0`, and `1.00` are equal and have the same
 * hash code, unlike `java.math.BigDecimal.equals`. Because `Decimal` is [Comparable], standard
 * library helpers such as `minOf`, `maxOf`, `coerceIn`, and `sorted` work directly.
 */
@Serializable(with = DecimalSerializer::class)
public expect class Decimal : Comparable<Decimal> {
    /**
     * Creates decimal values from plain strings, formatted strings, and primitive numeric values.
     */
    public companion object {
        /**
         * Creates a decimal from a plain base-10 string.
         *
         * @param value Decimal text such as `123`, `+42`, or `-0.01`; scientific notation and
         * grouped text are rejected.
         * @throws IllegalArgumentException When [value] is empty, malformed, or outside the portable
         * envelope.
         */
        public fun parse(value: String): Decimal

        /**
         * Creates a decimal from a plain base-10 string.
         *
         * @param value Decimal text such as `123`, `+42`, or `-0.01`; scientific notation and
         * grouped text are rejected.
         * @throws IllegalArgumentException When [value] is empty, malformed, or outside the portable
         * envelope.
         */
        public fun of(value: String): Decimal

        /**
         * Creates a decimal from formatted text by removing grouping separators.
         *
         * @param value Text containing a decimal value.
         * @param decimalSeparator Separator used for the fractional part.
         * @param groupingSeparators Candidate separators used for digit grouping.
         * @throws IllegalArgumentException When [value] does not match the separators or is outside
         * the portable envelope.
         */
        public fun parseFormatted(
            value: String,
            decimalSeparator: Char = '.',
            groupingSeparators: Set<Char> = setOf(',', ' ', '_'),
        ): Decimal

        /**
         * Creates a decimal from a plain base-10 string, or returns `null` when it is not accepted.
         *
         * Use this for user input and other text where rejection is expected; it accepts exactly
         * what [parse] accepts, including the portable envelope limits.
         *
         * @param value Decimal text such as `123`, `+42`, or `-0.01`.
         */
        public fun parseOrNull(value: String): Decimal?

        /**
         * Creates a decimal from formatted text, or returns `null` when it is not accepted.
         *
         * Accepts exactly what [parseFormatted] accepts with the same separators.
         *
         * @param value Text containing a decimal value.
         * @param decimalSeparator Separator used for the fractional part.
         * @param groupingSeparators Candidate separators used for digit grouping.
         */
        public fun parseFormattedOrNull(
            value: String,
            decimalSeparator: Char = '.',
            groupingSeparators: Set<Char> = setOf(',', ' ', '_'),
        ): Decimal?

        /**
         * Creates a decimal from an integer-only string.
         *
         * @param value Base-10 integer text without decimal, grouping, or exponent separators.
         * @throws IllegalArgumentException When [value] is empty, malformed, or outside the portable
         * envelope.
         */
        public fun ofInteger(value: String): Decimal

        /**
         * Creates a decimal from an integer value.
         *
         * @param value Integer source value.
         */
        public fun fromInt(value: Int): Decimal

        /**
         * Creates a decimal from a long integer value.
         *
         * @param value Integer source value.
         */
        public fun fromLong(value: Long): Decimal

        /**
         * Creates a decimal from a double using the digits printed by `Double.toString()`.
         *
         * `0.1` becomes exactly `0.1`, and values printed in scientific notation such as `1.0E7`
         * are expanded to plain decimals. The result must still fit the portable envelope.
         *
         * @param value Finite double source value; `NaN` and infinities are rejected.
         * @throws IllegalArgumentException When [value] is not finite or is outside the portable
         * envelope.
         */
        public fun fromDouble(value: Double): Decimal

        /**
         * Returns a decimal value equal to zero.
         */
        public fun zero(): Decimal

        /**
         * Returns a decimal value equal to one.
         */
        public fun one(): Decimal
    }

    /**
     * Adds another decimal to this value.
     *
     * @param other Value to add.
     * @throws IllegalArgumentException When the rounded result is outside the portable exponent
     * range.
     */
    public fun add(other: Decimal): Decimal

    /**
     * Adds another decimal to this value.
     *
     * @param other Value to add.
     * @throws IllegalArgumentException When the rounded result is outside the portable exponent
     * range.
     */
    public operator fun plus(other: Decimal): Decimal

    /**
     * Subtracts another decimal from this value.
     *
     * @param other Value to subtract.
     * @throws IllegalArgumentException When the rounded result is outside the portable exponent
     * range.
     */
    public fun subtract(other: Decimal): Decimal

    /**
     * Subtracts another decimal from this value.
     *
     * @param other Value to subtract.
     * @throws IllegalArgumentException When the rounded result is outside the portable exponent
     * range.
     */
    public operator fun minus(other: Decimal): Decimal

    /**
     * Multiplies this value by another decimal.
     *
     * @param other Multiplier.
     * @throws IllegalArgumentException When the rounded result is outside the portable exponent
     * range.
     */
    public fun multiply(other: Decimal): Decimal

    /**
     * Multiplies this value by another decimal.
     *
     * @param other Multiplier.
     * @throws IllegalArgumentException When the rounded result is outside the portable exponent
     * range.
     */
    public operator fun times(other: Decimal): Decimal

    /**
     * Multiplies this value by an integer string.
     *
     * @param integer Integer multiplier.
     * @throws IllegalArgumentException When [integer] is not integer text or the rounded result is
     * outside the portable exponent range.
     */
    public fun multiplyInteger(integer: String): Decimal

    /**
     * Divides this value by another decimal with explicit rounding.
     *
     * The exact quotient is rounded once with [rounding] to at most [scale] fractional digits.
     * When that would need more than 38 significant digits, the quotient is instead rounded to 38
     * significant digits, and digits below `1e-128` are never kept. For example,
     * `1000000000000000000000 / 3` at scale 18 keeps 17 fractional digits. Trailing zeros are not
     * stored, so `10 / 4` at scale 2 is `2.5`.
     *
     * @param other Divisor.
     * @param scale Maximum number of fractional digits to keep, from 0 through 32767.
     * @param rounding Rounding mode used when digits are discarded.
     * @throws ArithmeticException When [other] is zero.
     * @throws IllegalArgumentException When [scale] is out of range or the quotient is larger
     * than the portable exponent range allows.
     */
    public fun divide(other: Decimal, scale: Int, rounding: Rounding): Decimal

    /**
     * Divides this value by another decimal with a default human-friendly scale.
     *
     * @param other Divisor.
     * @return Quotient rounded to at most 18 fractional digits using [Rounding.HALF_UP], with the
     * same 38-significant-digit limit as [divide].
     * @throws ArithmeticException When [other] is zero.
     * @throws IllegalArgumentException When the quotient is larger than the portable exponent
     * range allows.
     */
    public operator fun div(other: Decimal): Decimal

    /**
     * Divides this value by an integer string with explicit rounding.
     *
     * @param integer Integer divisor.
     * @param scale Maximum number of fractional digits to keep, with the same limits as [divide].
     * @param rounding Rounding mode used when digits are discarded.
     * @throws ArithmeticException When [integer] is zero.
     * @throws IllegalArgumentException When [integer] is not integer text or [scale] is out of
     * range.
     */
    public fun divideInteger(integer: String, scale: Int, rounding: Rounding): Decimal

    /**
     * Moves the decimal point left by a fixed number of places.
     *
     * @param places Non-negative number of decimal places to move.
     * @throws IllegalArgumentException When [places] is negative or the result is outside the
     * portable exponent range.
     */
    public fun movePointLeft(places: Int): Decimal

    /**
     * Moves the decimal point right by a fixed number of places.
     *
     * @param places Non-negative number of decimal places to move.
     * @throws IllegalArgumentException When [places] is negative or the result is outside the
     * portable exponent range.
     */
    public fun movePointRight(places: Int): Decimal

    /**
     * Rounds this value to at most [scale] fractional digits.
     *
     * Trailing zeros are not stored, so `decimal("1.5").setScale(2, rounding)` is still `1.5`; use
     * [toFormattedString] with `minimumFractionDigits` to display a fixed number of digits.
     *
     * @param scale Maximum number of fractional digits to keep.
     * @param rounding Rounding mode used when digits are discarded.
     * @throws IllegalArgumentException When [scale] is outside 0 through 32767.
     */
    public fun setScale(scale: Int, rounding: Rounding): Decimal

    /**
     * Converts this value to a plain decimal string without scientific notation.
     */
    public fun toPlainString(): String

    /**
     * Converts this value to an integer string after rounding fractional digits with half-up.
     */
    public fun toIntegerString(): String

    /**
     * Formats this value for human display.
     *
     * @param maximumFractionDigits Maximum fractional digits to display.
     * @param minimumFractionDigits Minimum fractional digits to display.
     * @param rounding Rounding mode used before formatting.
     * @param decimalSeparator Separator used for the fractional part.
     * @param groupingSeparator Optional separator inserted every three integer digits.
     * @throws IllegalArgumentException When a fraction digit count is outside 0 through 32767,
     * [minimumFractionDigits] exceeds [maximumFractionDigits], or both separators are equal.
     */
    public fun toFormattedString(
        maximumFractionDigits: Int,
        minimumFractionDigits: Int = 0,
        rounding: Rounding = Rounding.HALF_UP,
        decimalSeparator: Char = '.',
        groupingSeparator: Char? = ',',
    ): String

    /**
     * Returns the absolute value.
     */
    public fun abs(): Decimal

    /**
     * Returns this value with the opposite sign; zero stays zero.
     */
    public fun negate(): Decimal

    /**
     * Returns this value with the opposite sign; zero stays zero.
     */
    public operator fun unaryMinus(): Decimal

    /**
     * Returns -1, 0, or 1 when this value is negative, zero, or positive.
     */
    public fun signum(): Int

    /**
     * Returns whether this value is numerically zero, regardless of how it was written.
     */
    public fun isZero(): Boolean

    /**
     * Compares decimal values numerically.
     *
     * @param other Value to compare with.
     * @return A negative number, zero, or a positive number when this value is less than, equal
     * to, or greater than [other].
     */
    override fun compareTo(other: Decimal): Int

    /**
     * Returns whether [other] is a decimal with the same numeric value, ignoring trailing zeros.
     *
     * @param other Candidate value.
     */
    override fun equals(other: Any?): Boolean

    /**
     * Returns a hash code consistent with numeric [equals].
     */
    override fun hashCode(): Int

    /**
     * Returns the same canonical plain text as [toPlainString].
     */
    override fun toString(): String
}
