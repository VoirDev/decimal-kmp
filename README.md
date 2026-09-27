# Decimal

[![Maven Central](https://img.shields.io/maven-central/v/dev.voir/decimal)](https://central.sonatype.com/artifact/dev.voir/decimal)

Tiny Kotlin Multiplatform decimal numbers for money, balances, exchange rates, and other values
that should not go through binary floating point math.

`Decimal` is a small common API backed by native decimal implementations:

- JVM and Android runtime: `java.math.BigDecimal`
- Apple targets: `Foundation.NSDecimalNumber`

It supports arithmetic, explicit rounding, formatted parsing and display, base-unit conversions,
and `kotlinx.serialization`.

To keep platform behavior predictable, JVM and Android intentionally follow the same numeric
limits as Apple `NSDecimalNumber`: values may use up to 38 significant digits and a base-10 decimal
exponent from -128 through 127. Inputs outside that envelope are rejected, arithmetic uses the
same 38-significant-digit decimal context, and exponent overflow is rejected instead of working only
on `BigDecimal` platforms.

## Installation

Decimal is published to Maven Central. Replace `<version>` with the latest version shown on the
badge above:

```kotlin
implementation("dev.voir:decimal:<version>")
```

For Kotlin Multiplatform projects, add it to the source set that needs decimal support:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.voir:decimal:<version>")
        }
    }
}
```

The library exposes `Decimal` as a `kotlinx.serialization` type. It depends on serialization core;
add `kotlinx-serialization-json` in your app if you want JSON encoding and decoding.

## Supported Platforms

The current build publishes these Kotlin Multiplatform targets:

| Target              | Backend           |
|---------------------|-------------------|
| JVM                 | `BigDecimal`      |
| Android             | `BigDecimal`      |
| iOS x64             | `NSDecimalNumber` |
| iOS arm64           | `NSDecimalNumber` |
| iOS simulator arm64 | `NSDecimalNumber` |
| macOS arm64         | `NSDecimalNumber` |

JavaScript, WebAssembly, Linux native, Windows native, and macOS x64 are not configured yet.

## Quick Start

```kotlin
import dev.voir.decimal.Decimal
import dev.voir.decimal.Rounding
import dev.voir.decimal.decimal

val price = decimal("19.99")
val quantity = Decimal.fromInt(3)
val total = price * quantity

println(total.toPlainString()) // 59.97
println(total.toFormattedString(maximumFractionDigits = 2)) // 59.97

val third = decimal("1").divide(decimal("3"), scale = 6, rounding = Rounding.HALF_UP)
println(third.toPlainString()) // 0.333333
```

Prefer creating decimals from strings or integers. `Decimal.fromDouble` is available for finite
`Double` values, but decimal text is usually the clearest way to preserve the value you mean.

## NSDecimalNumber Compatibility

Apple targets are backed by `Foundation.NSDecimalNumber`, so this library treats its limits as the
portable contract for every platform:

| Rule                | Meaning                                                     |
|---------------------|-------------------------------------------------------------|
| Significant digits  | At most 38 after insignificant trailing zeros are compacted |
| Decimal exponent    | The stored base-10 exponent must be from -128 through 127   |
| Non-finite values   | `NaN`, infinities, and incomplete decimal text are rejected |
| Scientific notation | Not accepted by parsers; pass plain decimal text instead    |

For example, a 38-digit integer is valid, `1` followed by 127 zeros is valid, and `0.` followed by
127 zeros and then `1` is valid. A 39-digit non-zero coefficient, `1` followed by 128 zeros, or a
non-zero value smaller than `1e-128` is outside the portable range.

The JVM and Android implementation still uses `BigDecimal` internally, but it validates parsed
values against these same rules and runs addition, subtraction, and multiplication with a 38-digit
half-up decimal context so tests do not pass on JVM and then behave differently on iOS or macOS.
On Apple targets, addition, subtraction, multiplication, and division round the exact result in
shared Kotlin code, because Foundation truncates some results and reports overflow with Objective-C
exceptions that terminate Kotlin/Native processes.

## Creating Values

```kotlin
import dev.voir.decimal.Decimal
import dev.voir.decimal.decimal
import dev.voir.decimal.formattedDecimal

val amount = decimal("1234.5678")
val sameAmount = Decimal.parse("1234.5678")

val cents = Decimal.ofInteger("123456")
val fromInt = Decimal.fromInt(42)
val fromLong = Decimal.fromLong(1_000_000L)

val grouped = formattedDecimal("1,234.56")
val european = formattedDecimal(
    "1.234,56",
    decimalSeparator = ',',
    groupingSeparators = setOf('.'),
)
```

Plain decimal parsing accepts values like `123`, `-0.01`, and `+42`. It intentionally rejects
scientific notation, incomplete decimals like `.1` or `1.`, and grouped text. Use
`parseFormatted` or `formattedDecimal` for user-facing grouped input.

For text where rejection is expected, such as a form field, use the `OrNull` variants instead of
catching exceptions. They accept exactly the same input as `parse` and `parseFormatted`:

```kotlin
val typed = Decimal.parseOrNull(input) ?: return showError("Enter an amount like 12.50")
val localized = Decimal.parseFormattedOrNull("1.234,56", decimalSeparator = ',', groupingSeparators = setOf('.'))
```

## Arithmetic

```kotlin
import dev.voir.decimal.Rounding
import dev.voir.decimal.decimal

val balance = decimal("100.00")
val deposit = decimal("25.50")
val fee = decimal("0.75")

val newBalance = balance + deposit - fee
println(newBalance.toPlainString()) // 124.75

val subtotal = decimal("12.50") * decimal("4")
println(subtotal.toPlainString()) // 50

val ratio = decimal("10").divide(decimal("3"), scale = 4, rounding = Rounding.DOWN)
println(ratio.toPlainString()) // 3.3333
```

The `/` operator uses a default scale of 18 and `Rounding.HALF_UP`:

```kotlin
val value = decimal("1") / decimal("3")
println(value.toPlainString()) // 0.333333333333333333
```

For domain logic, prefer `divide(..., scale, rounding)` so the precision and rounding policy are
visible at the call site.

Division rounds the exact quotient once. `scale` is the maximum number of fractional digits: when a
quotient would need more than 38 significant digits, it is rounded to 38 significant digits with
the same rounding mode instead of failing. Quotients are also never kept below `1e-128`.

```kotlin
val large = decimal("1000000000000000000000") / decimal("3")
println(large.toPlainString()) // 333333333333333333333.33333333333333333
```

## Rounding

`Decimal` includes six rounding modes, with the same meaning as `java.math.RoundingMode`:

| Mode                 | Behavior                                                     |
|----------------------|--------------------------------------------------------------|
| `Rounding.HALF_UP`   | Round to nearest; ties round away from zero                  |
| `Rounding.HALF_EVEN` | Round to nearest; ties round to the even neighbor (banker's) |
| `Rounding.DOWN`      | Drop discarded digits; move toward zero                      |
| `Rounding.UP`        | Round away from zero if any discarded digit is non-zero      |
| `Rounding.FLOOR`     | Round toward negative infinity                               |
| `Rounding.CEILING`   | Round toward positive infinity                               |

```kotlin
import dev.voir.decimal.Rounding
import dev.voir.decimal.decimal

decimal("1.235").setScale(2, Rounding.HALF_UP).toPlainString() // 1.24
decimal("1.239").setScale(2, Rounding.DOWN).toPlainString()    // 1.23
decimal("1.231").setScale(2, Rounding.UP).toPlainString()      // 1.24
decimal("2.5").setScale(0, Rounding.HALF_EVEN).toPlainString()  // 2
decimal("-1.231").setScale(2, Rounding.FLOOR).toPlainString()   // -1.24
decimal("-1.239").setScale(2, Rounding.CEILING).toPlainString() // -1.23
```

New rounding modes may be added in minor releases, so give exhaustive `when` expressions over
`Rounding` an `else` branch.

## Comparing

Equality, hashing, and ordering are numeric, so trailing zeros never matter. This differs from
`BigDecimal.equals`, where `1.0` and `1.00` are not equal.

```kotlin
import dev.voir.decimal.decimal
import dev.voir.decimal.sum

decimal("1.0") == decimal("1.00") // true
decimal("0.1") > decimal("0.09") // true

val prices = listOf(decimal("4.50"), decimal("-1.25"), decimal("10"))
prices.sorted() // [-1.25, 4.5, 10]
prices.max() // 10
prices.sum() // 13.25
maxOf(decimal("2"), decimal("1.999")) // 2

-decimal("1.5") // -1.5
decimal("-0.01").signum() // -1
decimal("0.000").isZero() // true
```

## Formatting

Use `toPlainString()` for canonical storage and APIs. Use `toFormattedString()` for display.

```kotlin
import dev.voir.decimal.Rounding
import dev.voir.decimal.decimal

val value = decimal("1234.567")

value.toPlainString() // 1234.567
value.toFormattedString(maximumFractionDigits = 2) // 1,234.57
value.toFormattedString(maximumFractionDigits = 2, groupingSeparator = null) // 1234.57
value.toFormattedString(maximumFractionDigits = 2, decimalSeparator = ',', groupingSeparator = '.') // 1.234,57
value.toFormattedString(maximumFractionDigits = 2, rounding = Rounding.DOWN) // 1,234.56
decimal("1234.5").toFormattedString(maximumFractionDigits = 2) // 1,234.5
decimal("1234.5").toFormattedString(maximumFractionDigits = 2, minimumFractionDigits = 2) // 1,234.50
```

## Base-Unit Conversions

`movePointLeft`, `movePointRight`, `ofInteger`, `multiplyInteger`, and `divideInteger` are useful
when converting between integer base units and display units.

```kotlin
import dev.voir.decimal.Decimal
import dev.voir.decimal.Rounding

val satoshis = Decimal.ofInteger("2100000000000000")
val bitcoins = satoshis.movePointLeft(8)

println(bitcoins.toPlainString()) // 21000000
println(bitcoins.movePointRight(8).toIntegerString()) // 2100000000000000

val wei = Decimal.ofInteger("123456789123456789123456789")
val ether = wei.divideInteger("1000000000000000000", scale = 18, rounding = Rounding.DOWN)

println(ether.toPlainString()) // 123456789.123456789123456789
```

## Serialization

`Decimal` is serializable with `kotlinx.serialization`. Values are encoded as plain decimal
strings, which keeps JSON stable and avoids precision loss.

```kotlin
import dev.voir.decimal.Decimal
import dev.voir.decimal.decimal
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class InvoiceLine(
    val amount: Decimal,
)

val encoded = Json.encodeToString(InvoiceLine(decimal("1234.5678")))
println(encoded) // {"amount":"1234.5678"}
```

## Errors

Failures are regular Kotlin exceptions with the same type and message on every platform. On Apple
targets, no Objective-C exception escapes from `Decimal`, so invalid input cannot terminate the
process.

| Exception                  | When                                                                       |
|----------------------------|----------------------------------------------------------------------------|
| `IllegalArgumentException` | Empty or malformed text, or a value outside the 38-digit/exponent envelope |
| `IllegalArgumentException` | A result outside the exponent range, such as an overflowing product        |
| `IllegalArgumentException` | An invalid argument, such as a negative scale or equal separators          |
| `ArithmeticException`      | Division by zero                                                           |

Messages name the rejected value and the limit it broke. Long input is shortened, so text from
untrusted sources such as JSON never floods logs:

```text
Invalid decimal text "1e3": expected plain base-10 text such as 123, +42, or -0.01.
Decimal "111111111111111111111111111111111111111" has 39 significant digits; at most 38 are supported.
Division by zero: "-1.5" / 0.
```

When decoding JSON, `DecimalSerializer` passes these exceptions through; kotlinx.serialization's own
`SerializationException` is also an `IllegalArgumentException`, so one `catch` covers both.

## API Overview

Common factories:

- `Decimal.parse(value: String)`
- `Decimal.of(value: String)`
- `decimal(value: String)`
- `Decimal.parseFormatted(...)`
- `Decimal.parseOrNull(value: String)`
- `Decimal.parseFormattedOrNull(...)`
- `formattedDecimal(...)`
- `Decimal.ofInteger(value: String)`
- `Decimal.fromInt(value: Int)`
- `Decimal.fromLong(value: Long)`
- `Decimal.fromDouble(value: Double)`
- `Decimal.zero()`
- `Decimal.one()`

Common operations:

- `+`, `-`, `*`, `/`, unary `-`
- `add`, `subtract`, `multiply`, `divide`, `negate`
- `multiplyInteger`, `divideInteger`
- `movePointLeft`, `movePointRight`
- `setScale`
- `abs`, `signum`, `isZero`
- `compareTo`, `<`, `>`, and standard library helpers such as `minOf`, `maxOf`, and `sorted`
- `Iterable<Decimal>.sum()`
- `toPlainString`
- `toIntegerString`
- `toFormattedString`

## Contributing

Issues and pull requests are welcome. Good contributions for a small numeric library include:

- Bug reports with a minimal input, expected output, and actual output
- Tests that cover platform-specific decimal behavior
- Additional Kotlin Multiplatform targets
- Documentation improvements and real-world usage examples

Please keep changes focused and include tests for behavior changes. `./gradlew build` runs the
common tests on JVM, Android, macOS, and the iOS simulator, plus JVM cross-checks against
`BigDecimal`. The build also checks the public API against the dumps in `decimal/api`; when a pull
request changes the API on purpose, run `./gradlew updateKotlinAbi` and commit the updated dumps.

## License

Decimal is available under the Apache License, Version 2.0. See [LICENSE](LICENSE).
