package dev.voir.decimal

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializes [Decimal] as its canonical plain decimal string representation.
 *
 * Encoding as a string keeps JSON and other text formats from passing decimal values through
 * binary floating point numbers.
 */
object DecimalSerializer : KSerializer<Decimal> {
    /**
     * Describes the serialized decimal value as a primitive string.
     */
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("dev.voir.decimal.Decimal", PrimitiveKind.STRING)

    /**
     * Reads a decimal value from a serialized plain decimal string.
     *
     * @param decoder Source decoder.
     */
    override fun deserialize(decoder: Decoder): Decimal = Decimal.parse(decoder.decodeString())

    /**
     * Writes a decimal value as a serialized plain decimal string.
     *
     * @param encoder Destination encoder.
     * @param value Decimal value to serialize.
     */
    override fun serialize(encoder: Encoder, value: Decimal) {
        encoder.encodeString(value.toPlainString())
    }
}
