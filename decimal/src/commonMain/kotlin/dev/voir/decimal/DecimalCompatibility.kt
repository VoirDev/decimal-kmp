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
        tooManyDigitsMessage(value, descriptor.significantDigits)
    }
    require(descriptor.decimalExponent in DECIMAL_MIN_EXPONENT..DECIMAL_MAX_EXPONENT) {
        exponentOutOfRangeMessage(value, descriptor.decimalExponent.toLong())
    }
}

/**
 * Returns the message for a value with more significant digits than the envelope allows.
 *
 * @param value Plain decimal text of the rejected value.
 * @param significantDigits Significant digits the value has.
 */
internal fun tooManyDigitsMessage(value: String, significantDigits: Int): String =
    "Decimal ${value.normalizePlainDecimalText().inputPreview()} has $significantDigits significant " +
        "digits; at most $DECIMAL_MAX_SIGNIFICANT_DIGITS are supported."

/**
 * Returns the message for a value whose exponent is outside the envelope.
 *
 * The exponent is that of the least significant non-zero digit, so `1000` has exponent 3 and
 * `0.01` has exponent -2.
 *
 * @param value Plain decimal text of the rejected value.
 * @param exponent Exponent the value has.
 */
internal fun exponentOutOfRangeMessage(value: String, exponent: Long): String =
    "Decimal ${value.normalizePlainDecimalText().inputPreview()} has exponent $exponent; the " +
        "exponent must be between $DECIMAL_MIN_EXPONENT and $DECIMAL_MAX_EXPONENT."

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
        val direction = if (toLeft) "left" else "right"
        "Moving the decimal point of ${value.inputPreview()} $direction by $places places gives " +
            "exponent $movedExponent; the exponent must be between $DECIMAL_MIN_EXPONENT and " +
            "$DECIMAL_MAX_EXPONENT."
    }
}

/**
 * Requires that an explicit fractional scale is inside the portable range.
 *
 * The maximum comes from the `Short`-backed scale used by Foundation rounding handlers.
 *
 * @param scale Number of fractional digits to keep or display.
 * @param name Parameter name used in the error message.
 */
internal fun requirePortableScale(scale: Int, name: String = "Scale") {
    require(scale in 0..DECIMAL_MAX_SCALE) {
        "$name must be between 0 and $DECIMAL_MAX_SCALE, but was $scale."
    }
}

/**
 * Canonicalizes plain decimal text by removing redundant sign, integer, and fractional zeros.
 *
 * Parsers call this before building platform values so very long zero padding never reaches
 * `BigDecimal` or Foundation, where stripping it can be slow.
 */
internal fun String.normalizePlainDecimalText(): String {
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
    if (fractionPart != null && !fractionPart.all { it in '0'..'9' }) return false

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
        return value.all { it in '0'..'9' }
    }

    val groups = value.split(groupingSeparator)
    return groups.isNotEmpty() &&
        groups.first().length in 1..3 &&
        groups.first().all { it in '0'..'9' } &&
        groups.drop(1).all { group -> group.length == 3 && group.all { it in '0'..'9' } }
}

/**
 * Returns portable decimal metadata for non-zero plain decimal text.
 *
 * @return Metadata for the significant coefficient, or `null` for zero.
 */
internal fun String.portableDecimalDescriptor(): PortableDecimalDescriptor? {
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
 * Portable decimal metadata derived from plain decimal text.
 *
 * @param significantText Significant decimal digits without sign, separator, or insignificant zeros.
 * @param significantDigits Count of non-zero-envelope significant digits.
 * @param decimalExponent Base-10 exponent of the least significant digit, so the value equals
 * `significantText * 10^decimalExponent`.
 */
internal data class PortableDecimalDescriptor(
    val significantText: String,
    val significantDigits: Int,
    val decimalExponent: Int,
)
