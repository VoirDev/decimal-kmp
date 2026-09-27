package dev.voir.decimal

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Covers kotlinx.serialization encoding as plain decimal strings.
 */
class DecimalSerializationTest {
    @Test
    fun `serializes and deserializes decimal as plain string`() {
        val encoded = Json.encodeToString(DecimalBox(decimal("1234.5678")))
        assertEquals("""{"amount":"1234.5678"}""", encoded)

        val decoded = Json.decodeFromString<DecimalBox>("""{"amount":"-0.125"}""")
        assertPlain("-0.125", decoded.amount)
    }

    @Test
    fun `rejects invalid serialized decimals with the parser message`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Json.decodeFromString<DecimalBox>("""{"amount":"1e3"}""")
        }

        assertEquals(
            "Invalid decimal text \"1e3\": expected plain base-10 text such as 123, +42, or -0.01.",
            exception.message,
        )
        assertFailsWith<IllegalArgumentException> { Json.decodeFromString<DecimalBox>("""{"amount":12.5}""") }
    }

    @Serializable
    private data class DecimalBox(val amount: Decimal)
}
