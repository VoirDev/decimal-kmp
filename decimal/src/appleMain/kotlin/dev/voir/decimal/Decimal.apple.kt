package dev.voir.decimal

import kotlinx.serialization.Serializable
import platform.Foundation.NSDecimalNumber

/**
 * Apple decimal implementation backed by `Foundation.NSDecimalNumber`.
 *
 * Values are limited to 38 significant digits and a decimal exponent from -128 through 127. The
 * implementation validates inputs before calling Foundation operations that can otherwise raise
 * Objective-C exceptions. Addition, subtraction, multiplication, and division use shared decimal
 * text arithmetic so results are rounded exactly like `BigDecimal` on JVM and Android.
 */
@Serializable(with = DecimalSerializer::class)
public actual class Decimal internal constructor(
    /**
     * Native Foundation decimal value stored behind the common `Decimal` API.
     */
    private val value: NSDecimalNumber,
) : Comparable<Decimal> {
    /**
     * Canonical plain text, computed once because arithmetic, comparison, and hashing use it.
     */
    private val plainText: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        value.stringValue.toFoundationPlainDecimalText()
    }

    /**
     * Creates Apple decimal values.
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
         * Formatted input is normalized in common code instead of `NSNumberFormatter`, which can
         * introduce precision artifacts such as turning cents into values like `...000001`.
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
        public actual fun fromInt(value: Int): Decimal = of(value.toString())

        /**
         * Creates a decimal from a long value.
         *
         * @param value Long value.
         */
        public actual fun fromLong(value: Long): Decimal = of(value.toString())

        /**
         * Creates a decimal from a finite double value.
         *
         * @param value Double value.
         */
        public actual fun fromDouble(value: Double): Decimal {
            requireFiniteDouble(value)
            // Kotlin/Native prints large and small doubles in scientific notation, such as 1.0E7.
            return of(value.toString().expandScientificDecimalText())
        }

        /**
         * Returns zero.
         */
        public actual fun zero(): Decimal = of("0")

        /**
         * Returns one.
         */
        public actual fun one(): Decimal = of("1")

        /**
         * Creates a decimal from canonical text that already passed the common parser.
         *
         * @param text Canonical plain decimal text inside the portable envelope.
         */
        private fun fromValidatedText(text: String): Decimal {
            val number = NSDecimalNumber(text)
            // The common parser already rejected malformed text, so NaN means Foundation disagrees.
            check(number != NSDecimalNumber.notANumber()) {
                "Foundation could not parse validated decimal text ${text.inputPreview()}."
            }
            return Decimal(number)
        }
    }

    /**
     * Adds another decimal.
     *
     * @param other Value to add.
     */
    public actual fun add(other: Decimal): Decimal =
        // Foundation addition truncates digits beyond 38 instead of rounding them, so add the
        // decimal text in common code with the same half-up rounding as JVM and Android.
        of(addPlainDecimalText(toPlainString(), other.toPlainString()))

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
        of(subtractPlainDecimalText(toPlainString(), other.toPlainString()))

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
        // Foundation raises uncatchable Objective-C exceptions when a rounded product overflows,
        // so multiply the decimal text in common code with the same rounding as JVM and Android.
        of(multiplyPlainDecimalText(toPlainString(), other.toPlainString()))

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

        // Foundation division rounds to 38 digits before a scale can be applied, which rounds twice
        // and raises uncatchable Objective-C exceptions on underflow. Divide the decimal text in
        // common code instead so the exact quotient is rounded once, as on JVM and Android.
        return of(dividePlainDecimalText(toPlainString(), other.toPlainString(), scale, rounding))
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
        // Zero never changes, and moving it would multiply by a power of ten up to 65k times.
        if (isZero()) return this
        requirePortablePointMove(toPlainString(), places, toLeft = true)
        return checked(value.decimalNumberByMovingPoint(places, toLeft = true))
    }

    /**
     * Moves the decimal point right.
     *
     * @param places Number of places to move.
     */
    public actual fun movePointRight(places: Int): Decimal {
        requirePortablePlaces(places)
        // Zero never changes, and moving it would multiply by a power of ten up to 65k times.
        if (isZero()) return this
        requirePortablePointMove(toPlainString(), places, toLeft = false)
        return checked(value.decimalNumberByMovingPoint(places, toLeft = false))
    }

    /**
     * Sets the scale with explicit rounding.
     *
     * @param scale Fractional digits to keep.
     * @param rounding Rounding mode.
     */
    public actual fun setScale(scale: Int, rounding: Rounding): Decimal {
        requirePortableScale(scale)

        // Round the decimal text in common code: Foundation rounding can leave artifacts such as
        // 8518.049999999999 after a scale-2 operation and has no half-even mode for all signs.
        return of(setScalePlainDecimalText(toPlainString(), scale, rounding))
    }

    /**
     * Converts to plain string.
     */
    public actual fun toPlainString(): String = plainText

    /**
     * Converts to integer string.
     */
    public actual fun toIntegerString(): String = setScale(0, Rounding.HALF_UP).toPlainString()

    /**
     * Converts to a human-friendly string.
     *
     * @param maximumFractionDigits Maximum fractional digits to display.
     * @param rounding Rounding mode.
     * @param decimalSeparator Decimal separator.
     * @param groupingSeparator Optional grouping separator.
     * @param minimumFractionDigits Minimum fractional digits to display.
     */
    public actual fun toFormattedString(
        maximumFractionDigits: Int,
        minimumFractionDigits: Int,
        rounding: Rounding,
        decimalSeparator: Char,
        groupingSeparator: Char?,
    ): String {
        requireFormatArguments(maximumFractionDigits, minimumFractionDigits, decimalSeparator, groupingSeparator)

        return setScale(maximumFractionDigits, rounding).toPlainString()
            .toFormattedPlainDecimalText(minimumFractionDigits, decimalSeparator, groupingSeparator)
    }

    /**
     * Returns the absolute value.
     */
    public actual fun abs(): Decimal {
        val plain = toPlainString()
        return if (plain.startsWith("-")) of(plain.drop(1)) else this
    }

    /**
     * Returns the negated value.
     */
    public actual fun negate(): Decimal {
        val plain = toPlainString()
        return when {
            plain == "0" -> this
            plain.startsWith("-") -> of(plain.drop(1))
            else -> of("-$plain")
        }
    }

    /**
     * Returns the negated value.
     */
    public actual operator fun unaryMinus(): Decimal = negate()

    /**
     * Returns the sign of this value.
     */
    public actual fun signum(): Int {
        val plain = toPlainString()
        return when {
            plain == "0" -> 0
            plain.startsWith("-") -> -1
            else -> 1
        }
    }

    /**
     * Returns whether this value is zero.
     */
    public actual fun isZero(): Boolean = toPlainString() == "0"

    /**
     * Compares decimal values by numeric value.
     *
     * @param other Value to compare with.
     */
    actual override fun compareTo(other: Decimal): Int =
        comparePlainDecimalText(toPlainString(), other.toPlainString())

    /**
     * Converts this value to debug text.
     */
    actual override fun toString(): String = toPlainString()

    /**
     * Compares decimal values by canonical plain string, which is numeric equality.
     *
     * @param other Candidate value.
     */
    actual override fun equals(other: Any?): Boolean =
        other is Decimal && toPlainString() == other.toPlainString()

    /**
     * Returns a hash code for this decimal.
     */
    actual override fun hashCode(): Int = toPlainString().hashCode()
}

/**
 * Creates a decimal after confirming Foundation did not produce a non-number value.
 *
 * @param value Native Foundation value to wrap.
 */
private fun checked(value: NSDecimalNumber): Decimal {
    if (value == NSDecimalNumber.notANumber()) {
        throw IllegalArgumentException(
            "Decimal result is outside the portable range: the exponent must be between " +
                "$DECIMAL_MIN_EXPONENT and $DECIMAL_MAX_EXPONENT."
        )
    }
    value.stringValue.requireAppleCompatibleDecimalText()
    return Decimal(value)
}

/**
 * Requires that parsed text fits the documented `NSDecimalNumber` envelope.
 *
 * Foundation may emit scientific notation for edge values, so text is normalized before the common
 * compatibility check runs.
 */
private fun String.requireAppleCompatibleDecimalText() =
    requirePortableDecimalText(toFoundationPlainDecimalText())

/**
 * Converts Foundation decimal text to canonical plain decimal text.
 */
private fun String.toFoundationPlainDecimalText(): String =
    expandScientificDecimalText().normalizePlainDecimalText()

/**
 * Expands Foundation scientific notation while preserving already-plain decimal text.
 */
private fun String.expandScientificDecimalText(): String {
    val exponentMarker = indexOfFirst { it == 'e' || it == 'E' }
    if (exponentMarker == -1) return this

    val significand = substring(0, exponentMarker)
    val exponent = substring(exponentMarker + 1).toInt()
    val isNegative = significand.startsWith("-")
    val unsigned = when {
        significand.startsWith("+") || significand.startsWith("-") -> significand.drop(1)
        else -> significand
    }
    val parts = unsigned.split('.', limit = 2)
    val integerPart = parts[0]
    val fractionPart = parts.getOrElse(1) { "" }
    val digits = integerPart + fractionPart
    val originalIntegerDigits = integerPart.length
    val decimalIndex = originalIntegerDigits + exponent

    val expanded = when {
        digits == "0" -> "0"
        decimalIndex <= 0 -> "0.${"0".repeat(-decimalIndex)}$digits"
        decimalIndex >= digits.length -> digits + "0".repeat(decimalIndex - digits.length)
        else -> digits.take(decimalIndex) + "." + digits.drop(decimalIndex)
    }

    return if (isNegative && expanded != "0") "-$expanded" else expanded
}

/**
 * Formats canonical plain decimal text without routing through Foundation formatters.
 *
 * @param minimumFractionDigits Minimum fractional digits to include in the result.
 * @param decimalSeparator Separator used for the fractional part.
 * @param groupingSeparator Optional separator inserted every three integer digits.
 */
private fun String.toFormattedPlainDecimalText(
    minimumFractionDigits: Int,
    decimalSeparator: Char,
    groupingSeparator: Char?,
): String {
    val isNegative = startsWith("-")
    val unsigned = if (isNegative || startsWith("+")) drop(1) else this
    val parts = unsigned.split('.', limit = 2)
    val integerPart = parts[0]
    val fractionPart = parts.getOrElse(1) { "" }.padEnd(minimumFractionDigits, '0')
    val groupedInteger = integerPart.toGroupedIntegerText(groupingSeparator)
    val formattedUnsigned = if (fractionPart.isEmpty()) {
        groupedInteger
    } else {
        "$groupedInteger$decimalSeparator$fractionPart"
    }

    return if (isNegative && formattedUnsigned != "0") "-$formattedUnsigned" else formattedUnsigned
}

/**
 * Inserts a grouping separator every three integer digits.
 *
 * @param groupingSeparator Optional separator to insert.
 */
private fun String.toGroupedIntegerText(groupingSeparator: Char?): String {
    if (groupingSeparator == null || length <= 3) return this

    val firstGroupLength = length % 3
    val builder = StringBuilder(length + (length - 1) / 3)
    var index = 0

    // Preserve a short leading group, then append fixed-width groups of three digits.
    if (firstGroupLength != 0) {
        builder.append(take(firstGroupLength))
        index = firstGroupLength
        if (index < length) builder.append(groupingSeparator)
    }

    while (index < length) {
        builder.append(substring(index, index + 3))
        index += 3
        if (index < length) builder.append(groupingSeparator)
    }

    return builder.toString()
}

/**
 * Moves the decimal point in chunks because Foundation accepts only a `Short` power of ten.
 *
 * @param places Number of decimal places to move.
 * @param toLeft Whether the decimal point moves left instead of right.
 */
private fun NSDecimalNumber.decimalNumberByMovingPoint(
    places: Int,
    toLeft: Boolean
): NSDecimalNumber {
    var result = this
    var remaining = places

    while (remaining > 0) {
        val chunk = minOf(remaining, Short.MAX_VALUE.toInt())
        val exponent = if (toLeft) -chunk else chunk
        result = result.decimalNumberByMultiplyingByPowerOf10(exponent.toShort())
        remaining -= chunk
    }

    return result
}
