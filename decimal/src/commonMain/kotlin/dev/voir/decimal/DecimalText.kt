package dev.voir.decimal

/**
 * Plain decimal text accepted by [Decimal.parse]: an optional sign, digits, and an optional
 * fraction. ASCII digits are spelled out because `\d` may match other scripts on some platforms.
 */
private val PLAIN_DECIMAL_RE = Regex("""^[+-]?[0-9]+(\.[0-9]+)?$""")

/**
 * Integer text accepted by [Decimal.ofInteger].
 */
private val PLAIN_INTEGER_RE = Regex("""^[+-]?[0-9]+$""")

/**
 * Longest input echoed in full by error messages; longer input is shortened.
 */
private const val MAX_PREVIEW_LENGTH = 48

/**
 * Validates plain decimal input and returns canonical text for building a platform value.
 *
 * Both platforms parse through this function, so they accept the same text and report the same
 * error messages.
 *
 * @param value Raw input; surrounding whitespace is ignored.
 * @param integerOnly Whether a fractional part is rejected.
 * @throws IllegalArgumentException When the text is empty, malformed, or outside the envelope.
 */
internal fun parsePlainDecimalText(value: String, integerOnly: Boolean): String {
    val kind = if (integerOnly) "Integer" else "Decimal"
    val trimmed = value.trim()
    require(trimmed.isNotEmpty()) { "$kind text is empty." }

    val pattern = if (integerOnly) PLAIN_INTEGER_RE else PLAIN_DECIMAL_RE
    require(pattern.matches(trimmed)) {
        val expected = if (integerOnly) "base-10 digits such as 42 or -7" else
            "plain base-10 text such as 123, +42, or -0.01"
        "Invalid ${kind.lowercase()} text ${value.inputPreview()}: expected $expected."
    }

    requirePortableDecimalText(trimmed)
    return trimmed.normalizePlainDecimalText()
}

/**
 * Validates formatted decimal input and returns canonical plain text.
 *
 * The input must match one decimal separator and at most one grouping separator from
 * [groupingSeparators]. Grouping is tried in a stable order so the result does not depend on set
 * iteration order.
 *
 * @param value Raw formatted input; surrounding whitespace is ignored.
 * @param decimalSeparator Separator used for the fractional part.
 * @param groupingSeparators Candidate separators used for digit grouping.
 * @throws IllegalArgumentException When no separator combination matches or the value is outside
 * the envelope.
 */
internal fun parseFormattedDecimalText(
    value: String,
    decimalSeparator: Char,
    groupingSeparators: Set<Char>,
): String {
    val trimmed = value.trim()
    require(trimmed.isNotEmpty()) { "Formatted decimal text is empty." }

    val candidates = groupingSeparators.minus(decimalSeparator).sorted()
    val options = listOf<Char?>(null) + candidates
    val match = options.indexOfFirst { candidate ->
        isFormattedDecimalText(trimmed, decimalSeparator, candidate)
    }
    require(match >= 0) {
        val grouping = candidates.joinToString(prefix = "[", postfix = "]") { "'$it'" }
        "Invalid formatted decimal text ${value.inputPreview()} for decimal separator " +
            "'$decimalSeparator' and grouping separators $grouping."
    }

    val plain = trimmed.toPlainDecimalText(decimalSeparator, options[match])
    return parsePlainDecimalText(plain, integerOnly = false)
}

/**
 * Requires a finite double before it is converted to decimal text.
 *
 * @param value Candidate double.
 */
internal fun requireFiniteDouble(value: Double) {
    require(value.isFinite()) { "Decimal cannot be created from non-finite Double $value." }
}

/**
 * Requires a non-negative number of places for moving the decimal point.
 *
 * @param places Candidate number of places.
 */
internal fun requirePortablePlaces(places: Int) {
    require(places >= 0) { "Places must be non-negative, but was $places." }
}

/**
 * Requires consistent arguments for formatting.
 *
 * @param maximumFractionDigits Maximum fractional digits to display.
 * @param minimumFractionDigits Minimum fractional digits to display.
 * @param decimalSeparator Separator used for the fractional part.
 * @param groupingSeparator Optional separator used for digit grouping.
 */
internal fun requireFormatArguments(
    maximumFractionDigits: Int,
    minimumFractionDigits: Int,
    decimalSeparator: Char,
    groupingSeparator: Char?,
) {
    requirePortableScale(maximumFractionDigits, name = "maximumFractionDigits")
    requirePortableScale(minimumFractionDigits, name = "minimumFractionDigits")
    require(minimumFractionDigits <= maximumFractionDigits) {
        "minimumFractionDigits ($minimumFractionDigits) must be at most " +
            "maximumFractionDigits ($maximumFractionDigits)."
    }
    require(groupingSeparator != decimalSeparator) {
        "decimalSeparator and groupingSeparator must differ, but both were '$decimalSeparator'."
    }
}

/**
 * Returns the error thrown when a divisor is zero.
 *
 * @param dividend Plain text of the value being divided.
 */
internal fun divisionByZero(dividend: String): ArithmeticException =
    ArithmeticException("Division by zero: ${dividend.inputPreview()} / 0.")

/**
 * Returns quoted input for error messages, shortened when it is long.
 *
 * Inputs can come from untrusted sources such as JSON, so messages never echo more than a short
 * prefix and suffix.
 */
internal fun String.inputPreview(): String =
    if (length <= MAX_PREVIEW_LENGTH) {
        "\"$this\""
    } else {
        "\"${take(MAX_PREVIEW_LENGTH / 2)}…${takeLast(MAX_PREVIEW_LENGTH / 4)}\" ($length characters)"
    }
