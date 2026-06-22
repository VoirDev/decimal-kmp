package dev.voir.decimal

import kotlinx.serialization.Serializable
import platform.Foundation.NSDecimalNumber
import platform.Foundation.NSDecimalNumberHandler
import platform.Foundation.NSRoundingMode

/**
 * Apple decimal implementation backed by `Foundation.NSDecimalNumber`.
 *
 * Values are limited to 38 significant digits and a decimal exponent from -128 through 127. The
 * implementation validates inputs before calling Foundation operations that can otherwise raise
 * Objective-C exceptions.
 */
@Serializable(with = DecimalSerializer::class)
actual class Decimal internal constructor(
    /**
     * Native Foundation decimal value stored behind the common `Decimal` API.
     */
    private val value: NSDecimalNumber,
) {
    /**
     * Creates Apple decimal values.
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
            s.requireAppleCompatibleDecimalText()

            val nd = NSDecimalNumber(s)
            if (nd == NSDecimalNumber.notANumber()) throw IllegalArgumentException("Invalid decimal string: $value")

            return Decimal(nd)
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

                // Parse formatted input by normalizing the validated text ourselves. NSNumberFormatter
                // can introduce precision artifacts while parsing, for example turning cents into
                // values like "...000001" on Apple targets.
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
            s.requireAppleCompatibleDecimalText()

            val nd = NSDecimalNumber(s)
            if (nd == NSDecimalNumber.notANumber()) throw IllegalArgumentException("Invalid integer string: $value")

            return Decimal(nd)
        }

        /**
         * Creates a decimal from an integer value.
         *
         * @param value Integer value.
         */
        actual fun fromInt(value: Int): Decimal = of(value.toString())

        /**
         * Creates a decimal from a long value.
         *
         * @param value Long value.
         */
        actual fun fromLong(value: Long): Decimal = of(value.toString())

        /**
         * Creates a decimal from a finite double value.
         *
         * @param value Double value.
         */
        actual fun fromDouble(value: Double): Decimal {
            require(value.isFinite()) { "Decimal cannot be created from a non-finite Double." }
            return of(value.toString())
        }

        /**
         * Returns zero.
         */
        actual fun zero(): Decimal = of("0")

        /**
         * Returns one.
         */
        actual fun one(): Decimal = of("1")
    }

    /**
     * Adds another decimal.
     *
     * @param other Value to add.
     */
    actual fun add(other: Decimal): Decimal = checked(value.decimalNumberByAdding(other.value))

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
        checked(value.decimalNumberBySubtracting(other.value))

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
    actual fun multiply(other: Decimal): Decimal {
        requirePortableMultiplication(toPlainString(), other.toPlainString())
        return checked(value.decimalNumberByMultiplyingBy(other.value))
    }

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
        if (other.toPlainString() == "0") throw ArithmeticException("Division by zero.")

        val divided = value.decimalNumberByDividingBy(other.value)
        val rounded = checked(
            divided.decimalNumberByRoundingAccordingToBehavior(
                roundingHandler(
                    scale,
                    rounding,
                    divided,
                )
            )
        )

        // Foundation can leave tiny representation artifacts after arithmetic even when a scale was
        // requested. Recreate the value from decimal text so toPlainString() matches other platforms.
        return rounded.toExactScaledDecimal(scale, rounding)
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
        return checked(value.decimalNumberByMovingPoint(places, toLeft = true))
    }

    /**
     * Moves the decimal point right.
     *
     * @param places Number of places to move.
     */
    actual fun movePointRight(places: Int): Decimal {
        require(places >= 0) { "Places must be non-negative." }
        requirePortablePointMove(toPlainString(), places, toLeft = false)
        return checked(value.decimalNumberByMovingPoint(places, toLeft = false))
    }

    /**
     * Sets the scale with explicit rounding.
     *
     * @param scale Fractional digits to keep.
     * @param rounding Rounding mode.
     */
    actual fun setScale(scale: Int, rounding: Rounding): Decimal {
        requirePortableScale(scale)
        val rounded = checked(
            value.decimalNumberByRoundingAccordingToBehavior(
                roundingHandler(
                    scale,
                    rounding,
                    value,
                )
            )
        )

        // Keep the stored value aligned with the requested scale semantics instead of preserving
        // Foundation artifacts such as 8518.049999999999 after a scale-2 operation.
        return rounded.toExactScaledDecimal(scale, rounding)
    }

    /**
     * Converts to plain string.
     */
    actual fun toPlainString(): String = value.stringValue.toFoundationPlainDecimalText()

    /**
     * Converts to integer string.
     */
    actual fun toIntegerString(): String {
        val rounded = setScale(0, Rounding.HALF_UP)
        return rounded.value.stringValue
    }

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

        return setScale(scale, rounding).toPlainString()
            .toFormattedPlainDecimalText(scale, decimalSeparator, groupingSeparator)
    }

    /**
     * Returns the absolute value.
     */
    actual fun abs(): Decimal =
        if (toPlainString().startsWith("-")) checked(value.decimalNumberByMultiplyingBy(of("-1").value)) else this

    /**
     * Converts this value to debug text.
     */
    override fun toString(): String = toPlainString()

    /**
     * Compares decimal values by plain string.
     *
     * @param other Candidate value.
     */
    override fun equals(other: Any?): Boolean =
        other is Decimal && toPlainString() == other.toPlainString()

    /**
     * Returns a hash code for this decimal.
     */
    override fun hashCode(): Int = toPlainString().hashCode()
}

/**
 * Creates a decimal after confirming Foundation did not produce a non-number value.
 *
 * @param value Native Foundation value to wrap.
 */
private fun checked(value: NSDecimalNumber): Decimal {
    if (value == NSDecimalNumber.notANumber()) {
        throw ArithmeticException("Decimal result is outside NSDecimalNumber limits.")
    }
    value.stringValue.requireAppleCompatibleDecimalText()
    return Decimal(value)
}

/**
 * Creates the behavior object used for explicit scale and rounding.
 *
 * @param scale Fractional digits to keep.
 * @param rounding Rounding mode.
 * @param value Value used to choose sign-aware rounding behavior.
 */
private fun roundingHandler(scale: Int, rounding: Rounding, value: NSDecimalNumber): NSDecimalNumberHandler {
    return NSDecimalNumberHandler.decimalNumberHandlerWithRoundingMode(
        roundingMode = rounding.toNativeRoundingMode(value.stringValue.startsWith("-")),
        scale = scale.toShort(),
        raiseOnExactness = false,
        raiseOnOverflow = false,
        raiseOnUnderflow = false,
        raiseOnDivideByZero = true,
    )
}

/**
 * Converts common rounding to Foundation rounding.
 *
 * @param isNegative Whether the value being rounded is below zero.
 */
private fun Rounding.toNativeRoundingMode(isNegative: Boolean): NSRoundingMode = when (this) {
    Rounding.HALF_UP -> NSRoundingMode.NSRoundPlain
    Rounding.DOWN -> if (isNegative) NSRoundingMode.NSRoundUp else NSRoundingMode.NSRoundDown
    Rounding.UP -> if (isNegative) NSRoundingMode.NSRoundDown else NSRoundingMode.NSRoundUp
}

/**
 * Recreates a rounded decimal from plain text to discard Foundation precision artifacts.
 *
 * Foundation formatters are intentionally not used for high-scale values because they can lose
 * useful precision. Plain text rounding keeps the requested scale exact.
 */
private fun Decimal.toExactScaledDecimal(scale: Int, rounding: Rounding): Decimal =
    Decimal.of(toPlainString().roundPlainDecimalTextToScale(scale, rounding))

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
 * Canonicalizes plain decimal text by removing redundant sign, integer, and fractional zeros.
 */
private fun String.normalizePlainDecimalText(): String {
    val isNegative = startsWith("-")
    val unsigned = when {
        startsWith("+") || startsWith("-") -> drop(1)
        else -> this
    }
    val parts = unsigned.split('.', limit = 2)
    val integerPart = parts[0].trimStart('0').ifEmpty { "0" }
    val fractionPart = parts.getOrElse(1) { "" }.trimEnd('0')
    val normalized = if (fractionPart.isEmpty()) integerPart else "$integerPart.$fractionPart"

    return if (isNegative && normalized != "0") "-$normalized" else normalized
}

/**
 * Rounds plain decimal text to a maximum scale without using platform formatters.
 *
 * The decimal operation already applied the requested rounding mode. This cleanup only trims extra
 * digits that are artifacts of Foundation's representation, using the same rounding mode for any
 * text that remains.
 *
 * @param scale Maximum number of fractional digits to keep.
 * @param rounding Rounding mode to apply if extra digits remain.
 */
private fun String.roundPlainDecimalTextToScale(scale: Int, rounding: Rounding): String {
    val isNegative = startsWith("-")
    val unsigned = if (isNegative || startsWith("+")) drop(1) else this
    val parts = unsigned.split('.', limit = 2)
    val integerPart = parts[0]
    val fractionPart = parts.getOrElse(1) { "" }

    if (fractionPart.length <= scale) return this

    val keptFraction = fractionPart.take(scale)
    val discardedFraction = fractionPart.drop(scale)
    val shouldRoundUp = when (rounding) {
        Rounding.HALF_UP -> discardedFraction.first() >= '5'
        Rounding.DOWN -> false
        Rounding.UP -> discardedFraction.any { it != '0' }
    }
    val unsignedRounded = if (shouldRoundUp) {
        incrementUnsignedDecimalText(integerPart, keptFraction)
    } else {
        joinUnsignedDecimalText(integerPart, keptFraction)
    }

    return if (isNegative && unsignedRounded != "0") "-$unsignedRounded" else unsignedRounded
}

/**
 * Adds one unit at the current fractional scale to unsigned plain decimal text.
 *
 * Example at scale 2: 1299 plus one fractional unit becomes 1300, then rejoins as 13.
 *
 * @param integerPart Digits before the decimal point.
 * @param fractionPart Digits after the decimal point that should be preserved.
 */
private fun incrementUnsignedDecimalText(integerPart: String, fractionPart: String): String {
    val digits = (integerPart + fractionPart).toMutableList()
    var index = digits.lastIndex

    while (index >= 0 && digits[index] == '9') {
        digits[index] = '0'
        index--
    }

    if (index >= 0) {
        digits[index] = digits[index] + 1
    } else {
        digits.add(0, '1')
    }

    val scale = fractionPart.length
    val rounded = digits.joinToString("")
    val roundedInteger = if (scale == 0) rounded else rounded.dropLast(scale).ifEmpty { "0" }
    val roundedFraction = if (scale == 0) "" else rounded.takeLast(scale)

    return joinUnsignedDecimalText(roundedInteger, roundedFraction)
}

/**
 * Joins unsigned integer and fraction text, then canonicalizes insignificant zeros.
 *
 * The public plain string is canonical, while callers that need fixed display precision should use
 * toFormattedString(scale = ...).
 *
 * @param integerPart Digits before the decimal point.
 * @param fractionPart Digits after the decimal point.
 */
private fun joinUnsignedDecimalText(integerPart: String, fractionPart: String): String =
    if (fractionPart.isEmpty()) {
        integerPart.trimStart('0').ifEmpty { "0" }
    } else {
        "${integerPart.trimStart('0').ifEmpty { "0" }}.$fractionPart".normalizePlainDecimalText()
    }

/**
 * Formats canonical plain decimal text without routing through Foundation formatters.
 *
 * @param scale Fractional digits to include in the result.
 * @param decimalSeparator Separator used for the fractional part.
 * @param groupingSeparator Optional separator inserted every three integer digits.
 */
private fun String.toFormattedPlainDecimalText(
    scale: Int,
    decimalSeparator: Char,
    groupingSeparator: Char?,
): String {
    val isNegative = startsWith("-")
    val unsigned = if (isNegative || startsWith("+")) drop(1) else this
    val parts = unsigned.split('.', limit = 2)
    val integerPart = parts[0]
    val fractionPart = parts.getOrElse(1) { "" }.padEnd(scale, '0')
    val groupedInteger = integerPart.toGroupedIntegerText(groupingSeparator)
    val formattedUnsigned = if (scale == 0) {
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
private fun NSDecimalNumber.decimalNumberByMovingPoint(places: Int, toLeft: Boolean): NSDecimalNumber {
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
