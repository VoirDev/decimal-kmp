package dev.voir.decimal

import kotlin.math.sign

/**
 * Adds plain decimal text and rounds the exact sum once to 38 significant digits.
 *
 * Rounding uses [Rounding.HALF_UP], matching the JVM `MathContext` used for addition,
 * subtraction, and multiplication. Foundation addition truncates digits beyond its precision
 * instead of rounding them, so Apple targets use this function too.
 *
 * @param left Plain decimal text for the first addend.
 * @param right Plain decimal text for the second addend.
 * @return Plain decimal text for the rounded sum; it may still be outside the portable exponent
 * range and must be validated by the caller.
 */
internal fun addPlainDecimalText(left: String, right: String): String {
    val leftDescriptor = left.portableDecimalDescriptor() ?: return right
    val rightDescriptor = right.portableDecimalDescriptor() ?: return left
    val leftIsNegative = left.startsWith("-")
    val rightIsNegative = right.startsWith("-")

    // Align both coefficients to the smaller exponent so the sum is exact before rounding.
    val exponent = minOf(leftDescriptor.decimalExponent, rightDescriptor.decimalExponent)
    val leftDigits = leftDescriptor.significantText + "0".repeat(leftDescriptor.decimalExponent - exponent)
    val rightDigits = rightDescriptor.significantText + "0".repeat(rightDescriptor.decimalExponent - exponent)

    if (leftIsNegative == rightIsNegative) {
        return addUnsignedIntegerText(leftDigits, rightDigits)
            .roundedToPlainDecimalText(exponent, leftIsNegative)
    }

    val comparison = compareUnsignedIntegerText(leftDigits, rightDigits)
    return when {
        comparison == 0 -> "0"
        comparison > 0 -> subtractUnsignedIntegerText(leftDigits, rightDigits)
            .roundedToPlainDecimalText(exponent, leftIsNegative)
        else -> subtractUnsignedIntegerText(rightDigits, leftDigits)
            .roundedToPlainDecimalText(exponent, rightIsNegative)
    }
}

/**
 * Subtracts plain decimal text and rounds the exact difference once to 38 significant digits.
 *
 * @param left Plain decimal text for the minuend.
 * @param right Plain decimal text for the subtrahend.
 * @return Plain decimal text for the rounded difference; it may still be outside the portable
 * exponent range and must be validated by the caller.
 */
internal fun subtractPlainDecimalText(left: String, right: String): String {
    val negatedRight = when {
        right.startsWith("-") -> right.drop(1)
        right.startsWith("+") -> "-" + right.drop(1)
        else -> "-$right"
    }

    return addPlainDecimalText(left, negatedRight)
}

/**
 * Multiplies plain decimal text and rounds the exact product once to 38 significant digits.
 *
 * Rounding uses [Rounding.HALF_UP], matching the JVM `MathContext` used for addition,
 * subtraction, and multiplication. Computing the product here also avoids Foundation raising
 * uncatchable Objective-C exceptions when a rounded product overflows.
 *
 * @param left Plain decimal text for the multiplicand.
 * @param right Plain decimal text for the multiplier.
 * @return Plain decimal text for the rounded product; it may still be outside the portable
 * exponent range and must be validated by the caller.
 */
internal fun multiplyPlainDecimalText(left: String, right: String): String {
    val leftDescriptor = left.portableDecimalDescriptor() ?: return "0"
    val rightDescriptor = right.portableDecimalDescriptor() ?: return "0"
    val isNegative = left.startsWith("-") != right.startsWith("-")

    return multiplyUnsignedIntegerText(leftDescriptor.significantText, rightDescriptor.significantText)
        .roundedToPlainDecimalText(leftDescriptor.decimalExponent + rightDescriptor.decimalExponent, isNegative)
}

/**
 * Returns the base-10 exponent of the last digit a quotient keeps.
 *
 * Division keeps at most `scale` fractional digits, but never more than the portable 38 significant
 * digits and never digits below the smallest portable exponent. Choosing this position before
 * rounding lets every platform round the exact quotient once, instead of rounding to 38 digits
 * first and then rounding again to the requested scale.
 *
 * @param quotientExponent Base-10 exponent of the exact quotient's most significant digit.
 * @param scale Requested number of fractional digits.
 */
internal fun divisionRoundingExponent(quotientExponent: Int, scale: Int): Int = maxOf(
    -scale,
    quotientExponent - (DECIMAL_MAX_SIGNIFICANT_DIGITS - 1),
    DECIMAL_MIN_EXPONENT,
)

/**
 * Divides plain decimal text and rounds the exact quotient once.
 *
 * The result keeps digits down to [divisionRoundingExponent]. Long division runs on the decimal
 * coefficients, so the rounding decision always sees the exact discarded remainder rather than a
 * platform result that was already rounded to 38 significant digits.
 *
 * @param dividend Plain decimal text for the dividend.
 * @param divisor Plain decimal text for the divisor.
 * @param scale Requested number of fractional digits.
 * @param rounding Rounding mode applied to the discarded part of the exact quotient.
 * @return Plain decimal text for the rounded quotient; it may still be outside the portable
 * exponent range and must be validated by the caller.
 * @throws ArithmeticException When [divisor] is zero.
 */
internal fun dividePlainDecimalText(
    dividend: String,
    divisor: String,
    scale: Int,
    rounding: Rounding,
): String {
    val divisorDescriptor = divisor.portableDecimalDescriptor() ?: throw divisionByZero(dividend)
    val dividendDescriptor = dividend.portableDecimalDescriptor() ?: return "0"
    val isNegative = dividend.startsWith("-") != divisor.startsWith("-")

    val dividendDigits = dividendDescriptor.significantText
    val divisorDigits = divisorDescriptor.significantText
    val quotientExponent = quotientLeadingExponent(dividendDescriptor, divisorDescriptor)
    val keptExponent = divisionRoundingExponent(quotientExponent, scale)

    val keptDigits: String
    val roundingDigit: Int
    val hasStickyDigits: Boolean
    if (quotientExponent < keptExponent - 1) {
        // The whole quotient sits below the rounding digit: kept and rounding digits are zero, but
        // the discarded quotient is non-zero.
        keptDigits = "0"
        roundingDigit = 0
        hasStickyDigits = true
    } else {
        // Coefficient quotient position p maps to value position p + shift.
        val shift = dividendDescriptor.decimalExponent - divisorDescriptor.decimalExponent
        val firstPosition = maxOf(dividendDigits.length - 1, keptExponent - shift)
        val lastPosition = keptExponent - 1 - shift
        val quotientDigits = StringBuilder()
        var remainder = "0"

        for (position in firstPosition downTo lastPosition) {
            val nextDigit = dividendDigits.digitAtPosition(position)
            remainder = if (remainder == "0") nextDigit.toString() else remainder + nextDigit

            var quotientDigit = 0
            while (compareUnsignedIntegerText(remainder, divisorDigits) >= 0) {
                remainder = subtractUnsignedIntegerText(remainder, divisorDigits)
                quotientDigit++
            }
            quotientDigits.append(quotientDigit)
        }

        keptDigits = quotientDigits.dropLast(1).toString().trimStart('0').ifEmpty { "0" }
        roundingDigit = quotientDigits.last() - '0'
        // A positive last position leaves dividend digits unread. The coefficient has no trailing
        // zeros, so those unread digits always include a non-zero units digit.
        hasStickyDigits = remainder != "0" || lastPosition > 0
    }

    return keptDigits
        .rounded(roundingDigit, hasStickyDigits, rounding, isNegative)
        .coefficientToPlainDecimalText(keptExponent, isNegative)
}

/**
 * Rounds plain decimal text to at most [scale] fractional digits.
 *
 * @param value Plain decimal text to round.
 * @param scale Maximum number of fractional digits to keep.
 * @param rounding Rounding mode applied to the discarded digits.
 * @return Plain decimal text for the rounded value.
 */
internal fun setScalePlainDecimalText(value: String, scale: Int, rounding: Rounding): String {
    val descriptor = value.portableDecimalDescriptor() ?: return "0"
    val keptExponent = -scale
    if (descriptor.decimalExponent >= keptExponent) return value

    val digits = descriptor.significantText
    val roundingIndex = digits.length - (keptExponent - descriptor.decimalExponent)
    val keptDigits = if (roundingIndex > 0) digits.take(roundingIndex) else "0"
    // A negative index means the whole coefficient sits below the rounding digit.
    val roundingDigit = if (roundingIndex >= 0) digits[roundingIndex] - '0' else 0
    val hasStickyDigits = roundingIndex < 0 || digits.drop(roundingIndex + 1).any { it != '0' }
    val isNegative = value.startsWith("-")

    return keptDigits
        .rounded(roundingDigit, hasStickyDigits, rounding, isNegative)
        .coefficientToPlainDecimalText(keptExponent, isNegative)
}

/**
 * Compares plain decimal text by numeric value.
 *
 * @return A negative number, zero, or a positive number when [left] is less than, equal to, or
 * greater than [right].
 */
internal fun comparePlainDecimalText(left: String, right: String): Int {
    val leftDescriptor = left.portableDecimalDescriptor()
    val rightDescriptor = right.portableDecimalDescriptor()
    val leftSign = leftDescriptor.signOf(left)
    val rightSign = rightDescriptor.signOf(right)
    if (leftSign != rightSign || leftDescriptor == null || rightDescriptor == null) {
        return leftSign.compareTo(rightSign)
    }

    val leftLeadingExponent = leftDescriptor.leadingExponent()
    val rightLeadingExponent = rightDescriptor.leadingExponent()
    val magnitude = if (leftLeadingExponent != rightLeadingExponent) {
        leftLeadingExponent.compareTo(rightLeadingExponent)
    } else {
        compareCoefficients(leftDescriptor, rightDescriptor)
    }

    return magnitude * leftSign
}

/**
 * Rounds an exact unsigned coefficient half up to 38 significant digits and returns plain text.
 *
 * @param exponent Base-10 exponent of the coefficient's last digit.
 * @param isNegative Whether a non-zero result should carry a minus sign.
 */
private fun String.roundedToPlainDecimalText(exponent: Int, isNegative: Boolean): String {
    val discardedDigits = maxOf(length - DECIMAL_MAX_SIGNIFICANT_DIGITS, 0)
    if (discardedDigits == 0) return coefficientToPlainDecimalText(exponent, isNegative)

    val keptDigits = dropLast(discardedDigits)
    val roundingDigit = this[keptDigits.length] - '0'
    val hasStickyDigits = drop(keptDigits.length + 1).any { it != '0' }

    return keptDigits
        .rounded(roundingDigit, hasStickyDigits, Rounding.HALF_UP, isNegative)
        .coefficientToPlainDecimalText(exponent + discardedDigits, isNegative)
}

/**
 * Applies a rounding mode to kept unsigned coefficient digits.
 *
 * @param roundingDigit First discarded digit.
 * @param hasStickyDigits Whether any digit after [roundingDigit] is non-zero.
 * @param rounding Rounding mode to apply.
 * @param isNegative Whether the value being rounded is below zero.
 * @return The kept digits, increased by one unit when the rounding mode moves away from zero.
 */
private fun String.rounded(
    roundingDigit: Int,
    hasStickyDigits: Boolean,
    rounding: Rounding,
    isNegative: Boolean,
): String {
    val hasDiscardedDigits = roundingDigit > 0 || hasStickyDigits
    val isAboveHalf = roundingDigit > 5 || roundingDigit == 5 && hasStickyDigits
    val isHalf = roundingDigit == 5 && !hasStickyDigits
    val roundsAwayFromZero = when (rounding) {
        Rounding.HALF_UP -> roundingDigit >= 5
        Rounding.HALF_EVEN -> isAboveHalf || isHalf && (last() - '0') % 2 == 1
        Rounding.DOWN -> false
        Rounding.UP -> hasDiscardedDigits
        Rounding.FLOOR -> isNegative && hasDiscardedDigits
        Rounding.CEILING -> !isNegative && hasDiscardedDigits
    }

    return if (roundsAwayFromZero) incrementUnsignedIntegerText(this) else this
}

/**
 * Returns the base-10 exponent of the most significant digit of `dividend / divisor`.
 *
 * @param dividend Non-zero dividend metadata.
 * @param divisor Non-zero divisor metadata.
 */
private fun quotientLeadingExponent(
    dividend: PortableDecimalDescriptor,
    divisor: PortableDecimalDescriptor,
): Int {
    // A dividend coefficient smaller than the divisor coefficient loses one leading place.
    val dividendIsSmaller = compareCoefficients(dividend, divisor) < 0

    return dividend.leadingExponent() - divisor.leadingExponent() - if (dividendIsSmaller) 1 else 0
}

/**
 * Returns the base-10 exponent of the most significant digit.
 */
private fun PortableDecimalDescriptor.leadingExponent(): Int = decimalExponent + significantDigits - 1

/**
 * Compares two coefficients as values scaled into `[1, 10)`, ignoring their exponents.
 */
private fun compareCoefficients(left: PortableDecimalDescriptor, right: PortableDecimalDescriptor): Int {
    val width = maxOf(left.significantDigits, right.significantDigits)
    return left.significantText.padEnd(width, '0').compareTo(right.significantText.padEnd(width, '0')).sign
}

/**
 * Returns -1, 0, or 1 for plain decimal text with this metadata.
 *
 * @param text Plain decimal text described by this metadata, or zero when it is `null`.
 */
private fun PortableDecimalDescriptor?.signOf(text: String): Int = when {
    this == null -> 0
    text.startsWith("-") -> -1
    else -> 1
}

/**
 * Returns the digit at a base-10 position of unsigned integer text, or zero outside the text.
 *
 * @param position Base-10 position where zero is the units digit.
 */
private fun String.digitAtPosition(position: Int): Char =
    if (position in 0 until length) this[length - 1 - position] else '0'

/**
 * Compares canonical unsigned integer text without leading zeros.
 */
private fun compareUnsignedIntegerText(left: String, right: String): Int =
    if (left.length != right.length) left.length.compareTo(right.length) else left.compareTo(right)

/**
 * Subtracts canonical unsigned integer text where [left] is at least [right].
 */
private fun subtractUnsignedIntegerText(left: String, right: String): String {
    val result = CharArray(left.length)
    var borrow = 0

    for (offset in left.indices) {
        val leftDigit = left[left.length - 1 - offset] - '0'
        val rightDigit = if (offset < right.length) right[right.length - 1 - offset] - '0' else 0
        var digit = leftDigit - rightDigit - borrow
        borrow = if (digit < 0) 1 else 0
        if (digit < 0) digit += 10
        result[left.length - 1 - offset] = '0' + digit
    }

    return result.concatToString().trimStart('0').ifEmpty { "0" }
}

/**
 * Adds canonical unsigned integer text.
 */
private fun addUnsignedIntegerText(left: String, right: String): String {
    val length = maxOf(left.length, right.length)
    val result = CharArray(length + 1)
    var carry = 0

    for (offset in 0 until length) {
        val leftDigit = if (offset < left.length) left[left.length - 1 - offset] - '0' else 0
        val rightDigit = if (offset < right.length) right[right.length - 1 - offset] - '0' else 0
        val digit = leftDigit + rightDigit + carry
        carry = digit / 10
        result[length - offset] = '0' + digit % 10
    }
    result[0] = '0' + carry

    return result.concatToString().trimStart('0').ifEmpty { "0" }
}

/**
 * Multiplies unsigned integer text and returns the unsigned product text.
 *
 * Grade-school multiplication is enough because inputs are at most 38 significant digits.
 *
 * @param left Unsigned integer text.
 * @param right Unsigned integer text.
 */
private fun multiplyUnsignedIntegerText(left: String, right: String): String {
    val product = IntArray(left.length + right.length)

    for (leftIndex in left.indices.reversed()) {
        for (rightIndex in right.indices.reversed()) {
            val productIndex = leftIndex + rightIndex + 1
            val digitProduct = (left[leftIndex] - '0') *
                (right[rightIndex] - '0') +
                product[productIndex]
            product[productIndex] = digitProduct % 10
            product[productIndex - 1] += digitProduct / 10
        }
    }

    return product.joinToString("").trimStart('0').ifEmpty { "0" }
}

/**
 * Adds one to canonical unsigned integer text.
 */
private fun incrementUnsignedIntegerText(value: String): String {
    val digits = value.toCharArray()
    var index = digits.lastIndex

    while (index >= 0 && digits[index] == '9') {
        digits[index] = '0'
        index--
    }

    return if (index >= 0) {
        digits[index] = digits[index] + 1
        digits.concatToString()
    } else {
        "1" + digits.concatToString()
    }
}

/**
 * Converts an unsigned integer coefficient and base-10 exponent into plain decimal text.
 *
 * @param exponent Base-10 exponent of the coefficient's last digit.
 * @param isNegative Whether a non-zero result should carry a minus sign.
 */
private fun String.coefficientToPlainDecimalText(exponent: Int, isNegative: Boolean): String {
    if (this == "0") return "0"

    val unsigned = when {
        exponent >= 0 -> this + "0".repeat(exponent)
        else -> {
            val fractionDigits = -exponent
            val padded = padStart(fractionDigits + 1, '0')
            padded.dropLast(fractionDigits) + "." + padded.takeLast(fractionDigits)
        }
    }

    return if (isNegative) "-$unsigned" else unsigned
}
