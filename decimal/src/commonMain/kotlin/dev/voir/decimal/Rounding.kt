package dev.voir.decimal

/**
 * Rounding behavior used when a decimal operation must discard fractional digits.
 *
 * These modes mirror the `java.math.RoundingMode` modes used by common money and exchange
 * workflows. New modes may be added in minor releases, so exhaustive `when` expressions over
 * this enum should include an `else` branch.
 */
public enum class Rounding {
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

    /**
     * Rounds to the nearest value and rounds ties to the neighbor with an even last digit.
     *
     * This is banker's rounding: `2.5` becomes `2`, `3.5` becomes `4`, and `-2.5` becomes `-2`.
     * It avoids the upward bias of [HALF_UP] when many rounded amounts are summed.
     */
    HALF_EVEN,

    /**
     * Rounds toward negative infinity when any discarded digit is non-zero.
     *
     * Positive values behave like [DOWN]; negative values behave like [UP].
     */
    FLOOR,

    /**
     * Rounds toward positive infinity when any discarded digit is non-zero.
     *
     * Positive values behave like [UP]; negative values behave like [DOWN].
     */
    CEILING,
}
