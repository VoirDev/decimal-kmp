package dev.voir.decimal

/**
 * Maximum number of significant digits supported by the portable decimal contract.
 */
internal const val DECIMAL_MAX_SIGNIFICANT_DIGITS = 38

/**
 * Smallest base-10 exponent supported by the portable decimal contract.
 */
internal const val DECIMAL_MIN_EXPONENT = -128

/**
 * Largest base-10 exponent supported by the portable decimal contract.
 */
internal const val DECIMAL_MAX_EXPONENT = 127

/**
 * Largest explicit fractional scale accepted by platform rounding APIs.
 */
internal const val DECIMAL_MAX_SCALE = 32767

/**
 * Requires that plain decimal text fits the portable `NSDecimalNumber` envelope.
 *
 * Zero has no meaningful significant exponent, so all zero spellings are accepted once parsing has
 * already proven that the text is syntactically valid.
 *
 * @param value Plain decimal text using `.` as the decimal separator.
 */
internal fun requirePortableDecimalText(value: String) {
    val descriptor = value.portableDecimalDescriptor() ?: return

    require(descriptor.significantDigits <= DECIMAL_MAX_SIGNIFICANT_DIGITS) {
        "Decimal supports at most $DECIMAL_MAX_SIGNIFICANT_DIGITS significant digits."
    }

    require(descriptor.decimalExponent in DECIMAL_MIN_EXPONENT..DECIMAL_MAX_EXPONENT) {
        "Decimal exponent must be between $DECIMAL_MIN_EXPONENT and $DECIMAL_MAX_EXPONENT."
    }
}

/**
 * Requires that multiplying two plain decimal values cannot overflow the portable exponent range.
 *
 * This preflight is used before Apple multiplication so Foundation does not raise an Objective-C
 * overflow exception before Kotlin code can report a regular argument failure.
 *
 * @param left Plain decimal text for the left value.
 * @param right Plain decimal text for the right value.
 */
internal fun requirePortableMultiplication(left: String, right: String) {
    val leftDescriptor = left.portableDecimalDescriptor() ?: return
    val rightDescriptor = right.portableDecimalDescriptor() ?: return
    val productSignificantDigits = multiplyUnsignedIntegerText(
        leftDescriptor.significantText,
        rightDescriptor.significantText,
    ).length
    val productExponent = leftDescriptor.decimalExponent +
        rightDescriptor.decimalExponent +
        productSignificantDigits -
        leftDescriptor.significantDigits -
        rightDescriptor.significantDigits +
        1

    require(productExponent in DECIMAL_MIN_EXPONENT..DECIMAL_MAX_EXPONENT) {
        "Decimal exponent must be between $DECIMAL_MIN_EXPONENT and $DECIMAL_MAX_EXPONENT."
    }
}

/**
 * Requires that moving a decimal point cannot overflow the portable exponent range.
 *
 * The check uses decimal metadata instead of constructing the moved string so very large `places`
 * values fail cheaply and consistently across platforms.
 *
 * @param value Plain decimal text for the source value.
 * @param places Number of decimal places to move.
 * @param toLeft Whether the decimal point moves left instead of right.
 */
internal fun requirePortablePointMove(value: String, places: Int, toLeft: Boolean) {
    val descriptor = value.portableDecimalDescriptor() ?: return
    val movedExponent = descriptor.decimalExponent.toLong() +
        if (toLeft) -places.toLong() else places.toLong()

    require(movedExponent in DECIMAL_MIN_EXPONENT.toLong()..DECIMAL_MAX_EXPONENT.toLong()) {
        "Decimal exponent must be between $DECIMAL_MIN_EXPONENT and $DECIMAL_MAX_EXPONENT."
    }
}

/**
 * Requires that an explicit fractional scale is inside the portable range.
 *
 * The maximum comes from the `Short`-backed scale used by Foundation rounding handlers.
 *
 * @param scale Number of fractional digits to keep or display.
 */
internal fun requirePortableScale(scale: Int) {
    require(scale >= 0) { "Scale must be non-negative." }
    require(scale <= DECIMAL_MAX_SCALE) { "Scale must be at most $DECIMAL_MAX_SCALE." }
}

/**
 * Validates that the whole input is decimal text for a single separator pair.
 *
 * The grouping separator is optional for this pass; callers can try several candidate grouping
 * separators and accept the first complete match.
 *
 * @param value Candidate formatted decimal text.
 * @param decimalSeparator Separator used for the fractional part.
 * @param groupingSeparator Optional separator used for digit grouping.
 */
internal fun isFormattedDecimalText(
    value: String,
    decimalSeparator: Char,
    groupingSeparator: Char?,
): Boolean {
    val unsigned = when {
        value.startsWith("+") || value.startsWith("-") -> value.drop(1)
        else -> value
    }
    if (unsigned.isEmpty()) return false

    val parts = unsigned.split(decimalSeparator)
    if (parts.size > 2) return false

    val integerPart = parts[0]
    val fractionPart = parts.getOrNull(1)
    if (integerPart.isEmpty()) return false
    if (fractionPart != null && fractionPart.isEmpty()) return false
    if (fractionPart != null && !fractionPart.all { it.isDigit() }) return false

    return isGroupedIntegerText(integerPart, groupingSeparator)
}

/**
 * Converts validated formatted decimal text into parser-friendly plain text.
 *
 * This function assumes [isFormattedDecimalText] has already accepted the same separator pair.
 *
 * @param decimalSeparator Separator used for the fractional part.
 * @param groupingSeparator Optional separator used for digit grouping.
 */
internal fun String.toPlainDecimalText(decimalSeparator: Char, groupingSeparator: Char?): String =
    buildString(length) {
        this@toPlainDecimalText.forEach { char ->
            when {
                groupingSeparator != null && char == groupingSeparator -> Unit
                char == decimalSeparator -> append('.')
                else -> append(char)
            }
        }
    }

/**
 * Validates plain or grouped integer text.
 *
 * When grouping is present, the first group may have one to three digits and every following group
 * must have exactly three digits.
 *
 * @param value Candidate integer text.
 * @param groupingSeparator Optional separator used for digit grouping.
 */
private fun isGroupedIntegerText(value: String, groupingSeparator: Char?): Boolean {
    if (groupingSeparator == null || !value.contains(groupingSeparator)) {
        return value.all { it.isDigit() }
    }

    val groups = value.split(groupingSeparator)
    return groups.isNotEmpty() &&
        groups.first().length in 1..3 &&
        groups.first().all { it.isDigit() } &&
        groups.drop(1).all { group -> group.length == 3 && group.all { it.isDigit() } }
}

/**
 * Returns portable decimal metadata for non-zero plain decimal text.
 *
 * @return Metadata for the significant coefficient, or `null` for zero.
 */
private fun String.portableDecimalDescriptor(): PortableDecimalDescriptor? {
    val unsigned = when {
        startsWith("+") || startsWith("-") -> drop(1)
        else -> this
    }
    val parts = unsigned.split('.', limit = 2)
    val integerPart = parts[0]
    val fractionPart = parts.getOrElse(1) { "" }
    val digits = integerPart + fractionPart
    val firstSignificant = digits.indexOfFirst { it != '0' }
    if (firstSignificant == -1) return null

    val lastSignificant = digits.indexOfLast { it != '0' }
    return PortableDecimalDescriptor(
        significantText = digits.substring(firstSignificant, lastSignificant + 1),
        significantDigits = lastSignificant - firstSignificant + 1,
        decimalExponent = integerPart.length - lastSignificant - 1,
    )
}

/**
 * Multiplies unsigned integer text and returns the unsigned product text.
 *
 * This tiny decimal-only multiplication avoids depending on platform big-number APIs in common
 * source while still letting compatibility checks reason about product precision.
 *
 * @param left Unsigned integer text.
 * @param right Unsigned integer text.
 */
private fun multiplyUnsignedIntegerText(left: String, right: String): String {
    val product = IntArray(left.length + right.length)

    // Grade-school multiplication is enough here because inputs are at most 38 significant digits.
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
 * Portable decimal metadata derived from plain decimal text.
 *
 * @param significantText Significant decimal digits without sign, separator, or insignificant zeros.
 * @param significantDigits Count of non-zero-envelope significant digits.
 * @param decimalExponent Base-10 exponent of the most significant digit.
 */
private data class PortableDecimalDescriptor(
    val significantText: String,
    val significantDigits: Int,
    val decimalExponent: Int,
)
