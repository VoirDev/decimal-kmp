package dev.voir.decimal

import kotlinx.serialization.Serializable

/**
 * Portable decimal number backed by each platform's native decimal type.
 *
 * `Decimal` keeps decimal values out of binary floating point math. JVM and Android use
 * `BigDecimal`, and Apple targets use `NSDecimalNumber`. To keep behavior portable, every
 * platform uses the `NSDecimalNumber` envelope: up to 38 significant digits and a decimal exponent
 * from -128 through 127. Values are immutable; every arithmetic operation returns a new decimal.
 */
@Serializable(with = DecimalSerializer::class)
expect class Decimal {
    /**
     * Creates decimal values from plain strings, formatted strings, and primitive numeric values.
     */
    companion object {
        /**
         * Creates a decimal from a plain base-10 string.
         *
         * @param value Decimal text such as `123`, `+42`, or `-0.01`; scientific notation and
         * grouped text are rejected.
         */
        fun parse(value: String): Decimal

        /**
         * Creates a decimal from a plain base-10 string.
         *
         * @param value Decimal text such as `123`, `+42`, or `-0.01`; scientific notation and
         * grouped text are rejected.
         */
        fun of(value: String): Decimal

        /**
         * Creates a decimal from formatted text by removing grouping separators.
         *
         * @param value Text containing a decimal value.
         * @param decimalSeparator Separator used for the fractional part.
         * @param groupingSeparators Candidate separators used for digit grouping.
         */
        fun parseFormatted(
            value: String,
            decimalSeparator: Char = '.',
            groupingSeparators: Set<Char> = setOf(',', ' ', '_'),
        ): Decimal

        /**
         * Creates a decimal from an integer-only string.
         *
         * @param value Base-10 integer text without decimal, grouping, or exponent separators.
         */
        fun ofInteger(value: String): Decimal

        /**
         * Creates a decimal from an integer value.
         *
         * @param value Integer source value.
         */
        fun fromInt(value: Int): Decimal

        /**
         * Creates a decimal from a long integer value.
         *
         * @param value Integer source value.
         */
        fun fromLong(value: Long): Decimal

        /**
         * Creates a decimal from a double using the double's canonical string form.
         *
         * @param value Finite double source value; `NaN` and infinities are rejected.
         */
        fun fromDouble(value: Double): Decimal

        /**
         * Returns a decimal value equal to zero.
         */
        fun zero(): Decimal

        /**
         * Returns a decimal value equal to one.
         */
        fun one(): Decimal
    }

    /**
     * Adds another decimal to this value.
     *
     * @param other Value to add.
     */
    fun add(other: Decimal): Decimal

    /**
     * Adds another decimal to this value.
     *
     * @param other Value to add.
     */
    operator fun plus(other: Decimal): Decimal

    /**
     * Subtracts another decimal from this value.
     *
     * @param other Value to subtract.
     */
    fun subtract(other: Decimal): Decimal

    /**
     * Subtracts another decimal from this value.
     *
     * @param other Value to subtract.
     */
    operator fun minus(other: Decimal): Decimal

    /**
     * Multiplies this value by another decimal.
     *
     * @param other Multiplier.
     */
    fun multiply(other: Decimal): Decimal

    /**
     * Multiplies this value by another decimal.
     *
     * @param other Multiplier.
     */
    operator fun times(other: Decimal): Decimal

    /**
     * Multiplies this value by an integer string.
     *
     * @param integer Integer multiplier.
     */
    fun multiplyInteger(integer: String): Decimal

    /**
     * Divides this value by another decimal with explicit rounding.
     *
     * @param other Divisor.
     * @param scale Number of fractional digits to keep.
     * @param rounding Rounding mode used when digits are discarded.
     */
    fun divide(other: Decimal, scale: Int, rounding: Rounding): Decimal

    /**
     * Divides this value by another decimal with a default human-friendly scale.
     *
     * @param other Divisor.
     * @return Quotient rounded to 18 fractional digits using [Rounding.HALF_UP].
     */
    operator fun div(other: Decimal): Decimal

    /**
     * Divides this value by an integer string with explicit rounding.
     *
     * @param integer Integer divisor.
     * @param scale Number of fractional digits to keep.
     * @param rounding Rounding mode used when digits are discarded.
     */
    fun divideInteger(integer: String, scale: Int, rounding: Rounding): Decimal

    /**
     * Moves the decimal point left by a fixed number of places.
     *
     * @param places Non-negative number of decimal places to move.
     */
    fun movePointLeft(places: Int): Decimal

    /**
     * Moves the decimal point right by a fixed number of places.
     *
     * @param places Non-negative number of decimal places to move.
     */
    fun movePointRight(places: Int): Decimal

    /**
     * Sets the number of fractional digits and applies rounding.
     *
     * @param scale Number of fractional digits to keep.
     * @param rounding Rounding mode used when digits are discarded.
     */
    fun setScale(scale: Int, rounding: Rounding): Decimal

    /**
     * Converts this value to a plain decimal string without scientific notation.
     */
    fun toPlainString(): String

    /**
     * Converts this value to an integer string after rounding fractional digits with half-up.
     */
    fun toIntegerString(): String

    /**
     * Formats this value for human display.
     *
     * @param scale Fractional digits to display.
     * @param rounding Rounding mode used before formatting.
     * @param decimalSeparator Separator used for the fractional part.
     * @param groupingSeparator Optional separator inserted every three integer digits.
     */
    fun toFormattedString(
        scale: Int,
        rounding: Rounding = Rounding.HALF_UP,
        decimalSeparator: Char = '.',
        groupingSeparator: Char? = ',',
    ): String

    /**
     * Returns the absolute value.
     */
    fun abs(): Decimal
}
