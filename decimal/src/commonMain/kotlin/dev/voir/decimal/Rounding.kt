package dev.voir.decimal

/**
 * Rounding behavior used when a decimal operation must discard fractional digits.
 *
 * These modes intentionally mirror the small set used by common money and exchange workflows.
 */
enum class Rounding {
    /**
     * Rounds to the nearest value and rounds ties away from zero.
     */
    HALF_UP,

    /**
     * Drops discarded digits and moves the result toward zero.
     */
    DOWN,

    /**
     * Rounds away from zero when any discarded digit is non-zero.
     */
    UP,
}
