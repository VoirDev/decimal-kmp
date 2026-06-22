package dev.voir.decimal

/**
 * Convenience factory for [Decimal.parse].
 *
 * @param value Plain decimal text accepted by [Decimal.parse].
 */
inline fun decimal(value: String): Decimal = Decimal.parse(value)

/**
 * Convenience factory for [Decimal.parseFormatted].
 *
 * @param value Formatted decimal text accepted by [Decimal.parseFormatted].
 * @param decimalSeparator Separator used for the fractional part.
 * @param groupingSeparators Candidate separators used for digit grouping.
 */
inline fun formattedDecimal(
    value: String,
    decimalSeparator: Char = '.',
    groupingSeparators: Set<Char> = setOf(',', ' ', '_'),
): Decimal = Decimal.parseFormatted(value, decimalSeparator, groupingSeparators)

/**
 * Applies a transformation to a decimal value and returns the transformed result.
 *
 * @param block Transformation to apply.
 */
inline fun Decimal.transform(block: (Decimal) -> Decimal): Decimal = block(this)
