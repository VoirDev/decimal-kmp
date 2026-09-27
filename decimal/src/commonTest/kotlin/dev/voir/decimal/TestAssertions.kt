package dev.voir.decimal

import kotlin.test.assertEquals

/**
 * Asserts the canonical plain text of a decimal.
 *
 * @param expected Expected [Decimal.toPlainString] output.
 * @param actual Decimal under test.
 */
internal fun assertPlain(expected: String, actual: Decimal) {
    assertEquals(expected, actual.toPlainString())
}
